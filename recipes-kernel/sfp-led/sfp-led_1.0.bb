SUMMARY = "SFP LED Control Kernel Module"
DESCRIPTION = "Controls SFP port LEDs based on module presence and optical signal state"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

inherit module

SRC_URI = " \
    file://sfp-led.c \
    file://Makefile \
    file://S99sfp-led \
"

S = "${UNPACKDIR}"

# Depends on SFP bus being available
DEPENDS += "virtual/kernel"

do_install:append() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/S99sfp-led ${D}${sysconfdir}/init.d/
    install -d ${D}${sysconfdir}/rcS.d
    ln -sf ../init.d/S99sfp-led ${D}${sysconfdir}/rcS.d/S99sfp-led
}

FILES:${PN} += "${sysconfdir}/init.d/S99sfp-led ${sysconfdir}/rcS.d/S99sfp-led"
