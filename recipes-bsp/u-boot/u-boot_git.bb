SUMMARY = "U-Boot for Mono Gateway board"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

DEPENDS = "bison-native flex-native dtc-native bc-native u-boot-tools-native"

SRC_URI = "git://github.com/we-are-mono/u-boot.git;protocol=https;branch=mt-6.12.34-2.1.0 \
           file://environment.txt \
          "
SRCREV = "dcb20811864a43a723f9b5a7c217f231a9dd1dcb"

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
    mkenvimage -s 0x2000 -o ${B}/uboot.env ${UNPACKDIR}/environment.txt
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 644 ${B}/u-boot.bin ${DEPLOYDIR}/u-boot-${MACHINE}-${PV}-${PR}.bin
    ln -sf u-boot-${MACHINE}-${PV}-${PR}.bin ${DEPLOYDIR}/u-boot.bin
    
    install -m 644 ${B}/uboot.env ${DEPLOYDIR}/uboot-${MACHINE}-${PV}-${PR}.env
    ln -sf uboot-${MACHINE}-${PV}-${PR}.env ${DEPLOYDIR}/uboot.env
}

addtask deploy after do_compile