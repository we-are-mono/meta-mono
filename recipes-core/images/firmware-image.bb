DESCRIPTION = "Combines RCW+BL2, ATF+U-Boot, environment, FMAN ucode, kernel + initramfs into 32MB NOR flash image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "atf fm-ucode virtual/kernel recovery-image util-linux-native openssl-native"
do_compile[depends] += "atf:do_deploy"
do_compile[depends] += "fm-ucode:do_deploy"
do_compile[depends] += "virtual/kernel:do_deploy"
do_compile[depends] += "recovery-image:do_image_complete"

inherit deploy

# No source needed - just assembly
SRC_URI = ""
S = "${WORKDIR}/src"

# BOOTTYPE can be overridden on command line
BOOTTYPE ?= "qspi emmc"

# Firmware signing key (ECDSA P-256). Set these to enable image signing.
# If FIRMWARE_SIGNING_KEY is not set or the file doesn't exist, the build
# produces unsigned images.
FIRMWARE_SIGNING_KEY ?= ""
FIRMWARE_SIGNING_PUBKEY ?= ""

do_compile() {
    for d in ${BOOTTYPE}; do
        # Create 32MB firmware image
        dd if=/dev/zero of=${WORKDIR}/firmware-${d}.bin bs=1 count=33554432

        # On eMMC the CPU boots from a 4KB offset to avoid the partition table region
        if [ "$d" = "emmc" ]; then
            BL2_OFFSET=4096
        else
            BL2_OFFSET=0
        fi

        dd if=${DEPLOY_DIR_IMAGE}/atf/bl2_${d}.pbl of=${WORKDIR}/firmware-${d}.bin bs=1 seek=${BL2_OFFSET} conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/atf/fip.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=1048576 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/u-boot-${d}.env of=${WORKDIR}/firmware-${d}.bin bs=1 seek=3145728 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/${FMAN_UCODE} of=${WORKDIR}/firmware-${d}.bin bs=1 seek=4194304 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/mono-gateway-dk.dtb of=${WORKDIR}/firmware-${d}.bin bs=1 seek=5242880 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/Image.gz-initramfs-${MACHINE}.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=10485760 conv=notrunc
    done
}


do_sign() {
    if [ -z "${FIRMWARE_SIGNING_KEY}" ] || [ ! -f "${FIRMWARE_SIGNING_KEY}" ]; then
        bbnote "Firmware signing key not configured, skipping image signing"
        return 0
    fi

    for d in ${BOOTTYPE}; do
        openssl dgst -sha256 -sign "${FIRMWARE_SIGNING_KEY}" \
            -out ${WORKDIR}/firmware-${d}.bin.sig \
            ${WORKDIR}/firmware-${d}.bin
        bbnote "Signed firmware-${d}.bin"
    done
}

addtask sign after do_compile before do_deploy

do_deploy() {
    install -d ${DEPLOYDIR}

    for d in ${BOOTTYPE}; do
        install -m 0644 ${WORKDIR}/firmware-${d}.bin ${DEPLOYDIR}/firmware-${d}-${MACHINE}.bin
        ln -sf firmware-${d}-${MACHINE}.bin ${DEPLOYDIR}/firmware-${d}.bin

        if [ -f ${WORKDIR}/firmware-${d}.bin.sig ]; then
            install -m 0644 ${WORKDIR}/firmware-${d}.bin.sig ${DEPLOYDIR}/firmware-${d}-${MACHINE}.bin.sig
            ln -sf firmware-${d}-${MACHINE}.bin.sig ${DEPLOYDIR}/firmware-${d}.bin.sig
        fi
    done

    if [ -n "${FIRMWARE_SIGNING_PUBKEY}" ] && [ -f "${FIRMWARE_SIGNING_PUBKEY}" ]; then
        install -m 0644 "${FIRMWARE_SIGNING_PUBKEY}" ${DEPLOYDIR}/firmware-signing.pub
    fi
}

addtask deploy after do_sign before do_build
