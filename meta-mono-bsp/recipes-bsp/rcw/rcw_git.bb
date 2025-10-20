SUMMARY = "Reset Configuration Word for Mono Gateway board"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=44a0d0fad189770cc022af4ac6262cbe"

DEPENDS = "tcl-native"

SRC_URI = "git://github.com/we-are-mono/rcw;protocol=https;branch=mt-6.12.34-2.1.0"
SRCREV = "0795a3215ba19014de82195f5e89da58b82c1f1d"

S = "${WORKDIR}/git"

BOARD = "gateway_dk"

do_compile() {
    oe_runmake BOARDS="${BOARD}"
}

inherit deploy

do_deploy() {
    oe_runmake BOARDS="${BOARD}" DESTDIR=${DEPLOYDIR}/rcw install
}

addtask deploy after do_compile