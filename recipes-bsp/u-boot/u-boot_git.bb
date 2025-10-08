require recipes-bsp/u-boot/u-boot-common.inc
require recipes-bsp/u-boot/u-boot.inc

DEPENDS += "u-boot-tools-native"

SRC_URI = "git://github.com/we-are-mono/u-boot.git;protocol=https;branch=mono-development"
SRCREV = "70021d4a18b2fc20f8ec06dc9f6d76c9880edf62"

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
