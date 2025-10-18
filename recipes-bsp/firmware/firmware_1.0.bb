DESCRIPTION = "Combines RCW+BL2, ATF+U-Boot, environment, FMAN ucode, kernel + initramfs into 32MB NOR flash image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "atf fm-ucode recovery-image"
do_compile[depends] += "atf:do_deploy"
do_compile[depends] += "fm-ucode:do_deploy"
do_compile[depends] += "recovery-image:do_image_complete"

inherit deploy

# No source needed - just assembly
SRC_URI = ""
S = "${WORKDIR}/src"

# BOOTTYPE can be overridden on command line
BOOTTYPE ?= "qspi emmc"

do_compile() {
    for d in ${BOOTTYPE}; do
        dd if=/dev/zero of=${WORKDIR}/firmware-${d}.bin bs=1 count=33030144
        
        dd if=${DEPLOY_DIR_IMAGE}/atf/bl2_${d}.pbl of=${WORKDIR}/firmware-${d}.bin bs=1 seek=0 conv=notrunc    
        dd if=${DEPLOY_DIR_IMAGE}/atf/fip.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=1048576 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/uboot.env of=${WORKDIR}/firmware-${d}.bin bs=1 seek=3145728 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/${FMAN_UCODE} of=${WORKDIR}/firmware-${d}.bin bs=1 seek=4194304 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/mono-gateway-dk-sdk.dtb of=${WORKDIR}/firmware-${d}.bin bs=1 seek=5242880 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/Image.gz-initramfs-${MACHINE}.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=10485760 conv=notrunc
    done
}

do_deploy() {
    install -d ${DEPLOYDIR}

    for d in ${BOOTTYPE}; do
        install -m 644 ${WORKDIR}/firmware-${d}.bin ${DEPLOYDIR}/firmware-${d}-${MACHINE}.bin
        ln -sf firmware-${d}-${MACHINE}.bin ${DEPLOYDIR}/firmware-${d}.bin
    done
}

addtask deploy after do_compile before do_build
