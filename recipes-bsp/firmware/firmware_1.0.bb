DESCRIPTION = "Combines RCW+BL2, ATF+U-Boot, environment, FMAN ucode, kernel + initramfs into 32MB NOR flash image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "qoriq-atf fm-ucode recovery-image"
do_compile[depends] += "qoriq-atf:do_deploy"
do_compile[depends] += "fm-ucode:do_deploy"
do_compile[depends] += "recovery-image:do_image_complete"

inherit deploy

# No source needed - just assembly
SRC_URI = ""
S = "${WORKDIR}/src"

COMPATIBLE_MACHINE = "gateway"

# BOOTTYPE can be overridden on command line
BOOTTYPE ?= "qspi"

# NOR Flash layout configuration
FLASH_SIZE = "33030144"
BL2_OFFSET = "0"
FIP_OFFSET = "1048576"
ENV_OFFSET = "3145728"
FMAN_OFFSET = "4194304"
DTB_OFFSET = "5242880"
KERNEL_OFFSET = "10485760"

do_compile() {
    cd ${S}

    # Create empty firmware image
    dd if=/dev/zero of=firmware-${BOOTTYPE}.bin bs=1 count=${FLASH_SIZE}

    # Iterate over components and write them into the image
    for component in bl2 fip env fman dtb kernel; do
        case $component in
            bl2)
                src=${DEPLOY_DIR_IMAGE}/atf/bl2_${BOOTTYPE}.pbl
                offset=${BL2_OFFSET}
                ;;
            fip)
                src=${DEPLOY_DIR_IMAGE}/atf/fip_uboot.bin
                offset=${FIP_OFFSET}
                ;;
            env)
                src=${DEPLOY_DIR_IMAGE}/u-boot-env.bin
                offset=${ENV_OFFSET}
                ;;
            fman)
                src=${DEPLOY_DIR_IMAGE}/${FMAN_UCODE}
                offset=${FMAN_OFFSET}
                ;;
            dtb)
                src=${DEPLOY_DIR_IMAGE}/mono-gateway-dk-sdk.dtb
                offset=${DTB_OFFSET}
                ;;
            kernel)
                src=${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}-initramfs-${MACHINE}.bin
                offset=${KERNEL_OFFSET}
                ;;
        esac

        [ -f "$src" ] || bbfatal "Component file not found: $src"

        dd if="$src" of=firmware-${BOOTTYPE}.bin bs=1 seek=$offset conv=notrunc
    done
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 644 ${S}/firmware-${BOOTTYPE}.bin ${DEPLOYDIR}/
    ln -sf firmware-${BOOTTYPE}.bin ${DEPLOYDIR}/firmware.bin
}

addtask deploy after do_compile before do_build
