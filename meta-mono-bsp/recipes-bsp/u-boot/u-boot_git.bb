SUMMARY = "U-Boot for Mono Gateway board"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

DEPENDS = "bison-native flex-native dtc-native bc-native u-boot-tools-native"

SRC_URI = "git://github.com/we-are-mono/u-boot;protocol=https;branch=mt-6.12.y \
           file://environment.txt \
           file://environment-qspi.txt \
           file://environment-emmc.txt \
          "
SRCREV = "9f13d11658f696d4d1b4f76fa88264c52bd2e7c2"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

S = "${WORKDIR}/git"

inherit kernel-arch deploy

UBOOT_MACHINE = "mono_gateway_dk_defconfig"

EXTRA_OEMAKE = 'CROSS_COMPILE=${TARGET_PREFIX} V=1'
EXTRA_OEMAKE += 'CC="${TARGET_PREFIX}gcc ${TOOLCHAIN_OPTIONS} ${DEBUG_PREFIX_MAP}"'
EXTRA_OEMAKE += 'HOSTCC="${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}"'

do_compile() {
    unset LDFLAGS
    unset CFLAGS
    unset CPPFLAGS

    oe_runmake ${UBOOT_MACHINE}
    oe_runmake ${EXTRA_OEMAKE}
    # Build per-boottype environments (common base + boottype-specific recovery command)
    cat ${UNPACKDIR}/environment.txt ${UNPACKDIR}/environment-qspi.txt | mkenvimage -s 0x2000 -o ${B}/u-boot-qspi.env -
    cat ${UNPACKDIR}/environment.txt ${UNPACKDIR}/environment-emmc.txt | mkenvimage -s 0x2000 -o ${B}/u-boot-emmc.env -
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/u-boot.bin ${DEPLOYDIR}/u-boot-${MACHINE}-${PV}-${PR}.bin
    ln -sf u-boot-${MACHINE}-${PV}-${PR}.bin ${DEPLOYDIR}/u-boot.bin

    install -m 0644 ${B}/u-boot-qspi.env ${DEPLOYDIR}/uboot-qspi-${MACHINE}-${PV}-${PR}.env
    ln -sf uboot-qspi-${MACHINE}-${PV}-${PR}.env ${DEPLOYDIR}/u-boot-qspi.env

    install -m 0644 ${B}/u-boot-emmc.env ${DEPLOYDIR}/uboot-emmc-${MACHINE}-${PV}-${PR}.env
    ln -sf uboot-emmc-${MACHINE}-${PV}-${PR}.env ${DEPLOYDIR}/u-boot-emmc.env
}

addtask deploy after do_compile
