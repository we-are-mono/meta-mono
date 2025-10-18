SUMMARY = "Linux kernel for Mono Gateway board"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

DEPENDS += "bison-native flex-native bc-native"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

LINUX_VERSION = "6.12.34"
PV = "${LINUX_VERSION}+git${SRCPV}"

LINUX_QORIQ_BRANCH = "lf-6.12.y"
LINUX_QORIQ_SRC = "git://github.com/nxp-qoriq/linux.git;protocol=https"

SRC_URI = "${LINUX_QORIQ_SRC};branch=${LINUX_QORIQ_BRANCH} \
           file://defconfig \
           file://mono-gateway-dk.dts \
           file://mono-gateway-dk-sdk.dts \
           file://mono-gateway-dk-usdpaa-xg-only.dts \
          "
SRCREV = "be78e49cb4339fd38c9a40019df49b72fbb8bcb7"

S = "${WORKDIR}/git"

do_configure:prepend() {
    cp ${UNPACKDIR}/defconfig ${B}/.config        
    cp ${UNPACKDIR}/*.dts ${S}/arch/arm64/boot/dts/freescale/
}