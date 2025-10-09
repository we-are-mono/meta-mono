require recipes-bsp/u-boot/u-boot-common.inc
require recipes-bsp/u-boot/u-boot.inc

DEPENDS += "u-boot-tools-native"

SRC_URI = "git://github.com/we-are-mono/u-boot.git;protocol=https;branch=mono-development"
SRCREV = "25c04dfe00ec6a849721aeaf0bd3aff3f27d64c3"

# U-Boot configuration
UBOOT_MACHINE = "mono_gateway_dk_defconfig"

# Environment size - typically 64KB or 128KB for NOR flash
ENV_SIZE = "0x2000"
SRC_URI += "file://environment.txt"

do_compile:append () {
    mkenvimage -s ${ENV_SIZE} -o ${B}/u-boot-env.bin ${UNPACKDIR}/environment.txt
}

do_deploy:append () {
    install -d ${DEPLOYDIR}
    install -m 644 ${B}/u-boot-env.bin ${DEPLOYDIR}/u-boot-env-${MACHINE}.bin
    ln -sf u-boot-env-${MACHINE}.bin ${DEPLOYDIR}/u-boot-env.bin
}

addtask deploy after do_compile before do_build
