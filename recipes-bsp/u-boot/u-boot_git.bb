SUMMARY = "U-Boot for Mono Gateway board"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

COMPATIBLE_MACHINE = "gateway-dk"

DEPENDS = "bison-native flex-native dtc-native bc-native u-boot-tools-native"

# Pinned to an NXP Linux Factory release (see conf/include/nxp-base.inc
# for the tag and SHA). Mono's Gateway-DK board support is applied
# from files/ as a numbered patch series.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/u-boot;protocol=https;nobranch=1 \
           file://0001-gateway-dk-add-board-core.patch \
           file://0002-gateway-dk-add-hw-self-test-harness.patch \
           file://0003-gateway-dk-add-per-component-self-te.patch \
           file://0004-gateway-dk-add-USB-PD-and-EEPROM-dev.patch \
           file://0005-gateway-dk-add-defconfig-and-board-header.patch \
           file://0006-gateway-dk-add-dts.patch \
           file://0007-gateway-dk-wire-into-upstream-tree.patch \
          "
SRCREV = "${NXP_LF_SRCREV_UBOOT}"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

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
    # Build per-boottype environments (common base + boottype-specific recovery command).
    # Templates are read directly from the layer; no SRC_URI/UNPACKDIR dance needed.
    cat ${THISDIR}/files/environment.txt ${THISDIR}/files/environment-qspi.txt | mkenvimage -s 0x2000 -o ${B}/u-boot-qspi.env -
    cat ${THISDIR}/files/environment.txt ${THISDIR}/files/environment-emmc.txt | mkenvimage -s 0x2000 -o ${B}/u-boot-emmc.env -
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
