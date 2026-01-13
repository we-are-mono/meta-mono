DESCRIPTION = "Combines RCW+BL2, ATF+U-Boot, environment, FMAN ucode, kernel + initramfs into 32MB NOR flash image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "atf fm-ucode virtual/kernel recovery-image util-linux-native"
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

do_compile() {
    for d in ${BOOTTYPE}; do
        # Create 32MB firmware image
        dd if=/dev/zero of=${WORKDIR}/firmware-${d}.bin bs=1 count=33554432

        if [ "$d" = "emmc" ]; then
            BL2_OFFSET=4096
        else
            BL2_OFFSET=0
        fi

        dd if=${DEPLOY_DIR_IMAGE}/atf/bl2_${d}.pbl of=${WORKDIR}/firmware-${d}.bin bs=1 seek=${BL2_OFFSET} conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/atf/fip.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=1048576 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/u-boot.env of=${WORKDIR}/firmware-${d}.bin bs=1 seek=3145728 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/${FMAN_UCODE} of=${WORKDIR}/firmware-${d}.bin bs=1 seek=4194304 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/mono-gateway-dk-sdk.dtb of=${WORKDIR}/firmware-${d}.bin bs=1 seek=5242880 conv=notrunc
        dd if=${DEPLOY_DIR_IMAGE}/Image.gz-initramfs-${MACHINE}.bin of=${WORKDIR}/firmware-${d}.bin bs=1 seek=10485760 conv=notrunc

        # There's probably a better way to do this, but it works, so we'll run with it.
        # We want a single partition on the eMMC, at 32MB offset, but if we just write
        # the partition table at 0, then sfdisk will complain, because the actual
        # partition is outside of the image. So we make a fake one, which is larger,
        # then just copy the first block over
        if [ "$d" = "emmc" ]; then
            truncate -s 31826378752 ${WORKDIR}/temp-${d}.img
            echo "start=65536, type=83, bootable" | sfdisk ${WORKDIR}/temp-${d}.img

            # Extract just the MBR
            dd if=${WORKDIR}/temp-${d}.img of=${WORKDIR}/firmware-${d}.bin bs=512 count=1 conv=notrunc
            rm ${WORKDIR}/temp-${d}.img
        fi
    done
}


do_deploy() {
    install -d ${DEPLOYDIR}

    for d in ${BOOTTYPE}; do
        install -m 0644 ${WORKDIR}/firmware-${d}.bin ${DEPLOYDIR}/firmware-${d}-${MACHINE}.bin
        ln -sf firmware-${d}-${MACHINE}.bin ${DEPLOYDIR}/firmware-${d}.bin
    done
}

addtask deploy after do_compile before do_build
