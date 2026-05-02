SUMMARY = "Reset Configuration Word for Mono Gateway board"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=44a0d0fad189770cc022af4ac6262cbe"

COMPATIBLE_MACHINE = "gateway-dk"

DEPENDS = "tcl-native"

# Pinned to an NXP Linux Factory release (see conf/include/nxp-base.inc
# for the tag and SHA). Mono's Gateway-DK board files are applied
# from files/ as a patch.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/rcw;protocol=https;nobranch=1 \
           file://0001-gateway-dk-add-board-support.patch \
           file://0002-gateway-dk-add-SFP-Base-X-RCW-profiles.patch \
"
SRCREV = "${NXP_LF_SRCREV_RCW}"

BOARD = "gateway_dk"

do_compile() {
    oe_runmake BOARDS="${BOARD}"
}

inherit deploy

do_deploy() {
    oe_runmake BOARDS="${BOARD}" DESTDIR=${DEPLOYDIR}/rcw install
}

addtask deploy after do_compile
