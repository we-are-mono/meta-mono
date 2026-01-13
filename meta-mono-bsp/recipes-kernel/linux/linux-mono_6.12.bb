SUMMARY = "Linux kernel for Mono Gateway board"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

DEPENDS += "bison-native flex-native bc-native"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

LINUX_VERSION = "6.12.49"
PV = "${LINUX_VERSION}+git${SRCPV}"

LINUX_QORIQ_BRANCH = "lf-6.12.y"
LINUX_QORIQ_SRC = "git://github.com/nxp-qoriq/linux.git;protocol=https"

SRC_URI = "${LINUX_QORIQ_SRC};branch=${LINUX_QORIQ_BRANCH} \
           file://defconfig \
           file://mono-gateway-dk.dts \
           file://mono-gateway-dk-sdk.dts \
           file://mono-gateway-dk-usdpaa-xg-only.dts \
           file://0001-hwmon-ina2xx-Add-INA234-support.patch \
           file://002-mono-gateway-ask-kernel_linux_6_12.patch \
          "
# Latest lf-6.12.y as of 2026-01-11
SRCREV = "df24f9428e38740256a410b983003a478e72a7c0"

S = "${WORKDIR}/git"

do_configure:prepend() {
    cp ${UNPACKDIR}/defconfig ${B}/.config
    cp ${UNPACKDIR}/*.dts ${S}/arch/arm64/boot/dts/freescale/
}
