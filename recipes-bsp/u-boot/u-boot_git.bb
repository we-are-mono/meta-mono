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
           file://0005-gateway-dk-add-board-header.patch \
           file://0006-gateway-dk-wire-into-upstream-tree.patch \
           file://0007-gateway-dk-derive-SFP-modes-from-SerDes-RCW.patch \
           file://0008-gateway-dk-light-the-SFP-port-LEDs.patch \
           file://0009-crypto-fsl-rng-fail-a-read-when-CAAM-rejects-the-job.patch \
           file://mono-gateway-dk.dts;subdir=${BP}/arch/arm/dts \
           file://mono_gateway_dk_defconfig;subdir=${BP}/configs \
           file://environment.txt \
           file://environment-qspi.txt \
           file://environment-emmc.txt \
          "
SRCREV = "${NXP_LF_SRCREV_UBOOT}"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

inherit kernel-arch deploy

UBOOT_MACHINE = "mono_gateway_dk_defconfig"

# setlocalversion appends the scm hash and a -dirty marker, because OE applies
# the board port with quilt and so leaves tracked files modified. Name the LF
# release the port is based on instead, the way atf_git.bb does for TF-A's
# banner, followed by the Mono firmware version, so that the QSPI and eMMC
# chains, which can carry different builds, tell themselves apart at the
# console. The defconfig turns LOCALVERSION_AUTO off; setting LOCALVERSION
# here also suppresses the "+" that setlocalversion would otherwise append.
export LOCALVERSION = "-${NXP_LF_TAG}-${FIRMWARE_VERSION}"

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
