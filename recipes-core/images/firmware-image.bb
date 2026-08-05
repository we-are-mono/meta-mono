DESCRIPTION = "Combines RCW+BL2, ATF+U-Boot, environment, FMAN ucode and the recovery FIT into a 32MB NOR flash image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "atf fm-ucode linux-mono-fitimage recovery-image util-linux-native openssl-native"
# u-boot must be explicit: do_compile reads the .env images straight from
# u-boot's deploy, and the implicit route via atf is cut by hash
# equivalence whenever an env-only change leaves u-boot.bin identical.
do_compile[depends] += "u-boot:do_deploy"
do_compile[depends] += "atf:do_deploy"
do_compile[depends] += "fm-ucode:do_deploy"
do_compile[depends] += "linux-mono-fitimage:do_deploy"
do_compile[depends] += "recovery-image:do_image_complete"

inherit deploy

# No source needed - just assembly
SRC_URI = ""
S = "${UNPACKDIR}/src"

# BOOTTYPE can be overridden on command line
BOOTTYPE ?= "qspi emmc"

# Firmware signing key (ECDSA P-256). Set these to enable image signing.
# If FIRMWARE_SIGNING_KEY is not set or the file doesn't exist, the build
# produces unsigned images. Set FIRMWARE_SIGNING_REQUIRED = "1" (typically
# in site.conf) to make the build fail loudly instead of silently producing
# an unsigned image.
FIRMWARE_SIGNING_KEY ?= ""
FIRMWARE_SIGNING_PUBKEY ?= ""
FIRMWARE_SIGNING_REQUIRED ?= "0"

python __anonymous() {
    if d.getVar("FIRMWARE_SIGNING_REQUIRED") != "1":
        return
    key = d.getVar("FIRMWARE_SIGNING_KEY") or ""
    pub = d.getVar("FIRMWARE_SIGNING_PUBKEY") or ""
    if not key or not os.path.isfile(key):
        bb.fatal("FIRMWARE_SIGNING_REQUIRED=1 but FIRMWARE_SIGNING_KEY is unset "
                 "or missing (%r). Refusing to build an unsigned firmware image." % key)
    if not pub or not os.path.isfile(pub):
        bb.fatal("FIRMWARE_SIGNING_REQUIRED=1 but FIRMWARE_SIGNING_PUBKEY is unset "
                 "or missing (%r). Devices need the pubkey to verify signatures." % pub)
}

# Fail the build if a component outgrows its flash window; dd would
# otherwise silently overwrite the next region or grow the image past 32MB.
check_size() {
    file="$1"
    window_kib="$2"
    size=$(stat -Lc %s "$file")
    if [ "$size" -gt "$(expr $window_kib \* 1024)" ]; then
        bbfatal "$(basename $file) is $size bytes, exceeds its ${window_kib}KiB flash window"
    fi
}

do_compile() {
    for d in ${BOOTTYPE}; do
        # Create 32MB firmware image (zero-filled). Remove any previous
        # image first: truncate on an existing file keeps its contents,
        # and WORKDIR persists between builds, so a shrinking component
        # would otherwise leave the old build's tail bytes in the gaps.
        rm -f ${WORKDIR}/firmware-${d}.bin
        truncate -s 32M ${WORKDIR}/firmware-${d}.bin

        # On eMMC the CPU boots from a 4KB offset to avoid the partition table region
        if [ "$d" = "emmc" ]; then
            BL2_SEEK=4
        else
            BL2_SEEK=0
        fi

        # Windows match the partition layout in the kernel device tree (and
        # the sizes the recovery command reads back), with the FIT window
        # ending at the 32MB image size.
        check_size ${DEPLOY_DIR_IMAGE}/atf/bl2_${d}.pbl $(expr 1024 - $BL2_SEEK)
        check_size ${DEPLOY_DIR_IMAGE}/atf/fip.bin 2048
        check_size ${DEPLOY_DIR_IMAGE}/u-boot-${d}.env 1024
        check_size ${DEPLOY_DIR_IMAGE}/${FMAN_UCODE} 1024
        check_size ${DEPLOY_DIR_IMAGE}/fitImage 24576

        dd if=${DEPLOY_DIR_IMAGE}/atf/bl2_${d}.pbl of=${WORKDIR}/firmware-${d}.bin bs=1K seek=${BL2_SEEK} conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/atf/fip.bin of=${WORKDIR}/firmware-${d}.bin bs=1K seek=1024 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/u-boot-${d}.env of=${WORKDIR}/firmware-${d}.bin bs=1K seek=3072 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/${FMAN_UCODE} of=${WORKDIR}/firmware-${d}.bin bs=1K seek=4096 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/fitImage of=${WORKDIR}/firmware-${d}.bin bs=1K seek=8192 conv=notrunc
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

        if [ -f ${WORKDIR}/firmware-${d}.bin.sig ]; then
            install -m 0644 ${WORKDIR}/firmware-${d}.bin.sig ${DEPLOYDIR}/firmware-${d}-${MACHINE}.bin.sig
        fi
    done

    if [ -n "${FIRMWARE_SIGNING_PUBKEY}" ] && [ -f "${FIRMWARE_SIGNING_PUBKEY}" ]; then
        install -m 0644 "${FIRMWARE_SIGNING_PUBKEY}" ${DEPLOYDIR}/firmware-signing.pub
    fi
}

addtask deploy after do_sign before do_build
