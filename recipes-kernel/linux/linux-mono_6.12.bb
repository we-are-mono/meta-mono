SUMMARY = "Linux kernel for Mono Gateway board"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel kernel-yocto

DEPENDS += "bison-native flex-native bc-native"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Pinned to an NXP Linux Factory release (see conf/include/nxp-base.inc
# for the tag, SHA, and kernel version). Mono's board files (defconfig,
# dts, INA234 patch) layer on top.
require conf/include/nxp-base.inc

# Upstream's curated kernel CVE triage (not-applicable / wrong-platform /
# historic), keyed by CVE_STATUS. Suppresses CVEs that don't apply to a
# stock arm64 kernel so sbom-cve-check reports only the real deltas.
require recipes-kernel/linux/cve-exclusion.inc

LINUX_VERSION = "${NXP_LF_LINUX_VERSION}"
PV = "${LINUX_VERSION}+git${SRCPV}"

KCONFIG_MODE = "alldefconfig"

SRC_URI = "git://github.com/nxp-qoriq/linux.git;protocol=https;nobranch=1 \
           file://defconfig \
           file://mono-gateway-dk.dts \
           file://mono-gateway-dk-usdpaa-xg-only.dts \
           file://001-hwmon-ina2xx-Add-INA234-support.patch \
          "

SRCREV = "${NXP_LF_SRCREV_LINUX}"

do_configure:prepend() {
    cp ${UNPACKDIR}/*.dts ${S}/arch/arm64/boot/dts/freescale/
}
