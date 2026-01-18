SUMMARY = "U-Boot for Mono Gateway board"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

DEPENDS = "bison-native flex-native dtc-native bc-native u-boot-tools-native"

SRC_URI = "git://github.com/we-are-mono/u-boot;protocol=https;branch=mt-6.12.y \
           file://environment.txt \
          "
SRCREV = "26d27571ac82afc70d7542dd7ab4ec1894666d67"

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
    mkenvimage -s 0x2000 -o ${B}/u-boot.env ${UNPACKDIR}/environment.txt
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/u-boot.bin ${DEPLOYDIR}/u-boot-${MACHINE}-${PV}-${PR}.bin
    ln -sf u-boot-${MACHINE}-${PV}-${PR}.bin ${DEPLOYDIR}/u-boot.bin

    install -m 0644 ${B}/u-boot.env ${DEPLOYDIR}/uboot-${MACHINE}-${PV}-${PR}.env
    ln -sf uboot-${MACHINE}-${PV}-${PR}.env ${DEPLOYDIR}/u-boot.env
}

addtask deploy after do_compile
