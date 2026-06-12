SUMMARY = "Texas Instruments LP5812 LED Matrix Driver"
DESCRIPTION = "Linux kernel driver for TI LP5812 LED controller that supports 4x3 LED matrix via I2C"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module

SRC_URI = " \
    file://leds-lp5812.c \
    file://leds-lp5812.h \
    file://Makefile \
    file://S05lp5812 \
"

S = "${UNPACKDIR}"

do_install:append() {
    # S05 runs before S95status-led so the LED nodes exist by the time
    # any consumer looks for them.
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/S05lp5812 ${D}${sysconfdir}/init.d/
    install -d ${D}${sysconfdir}/rcS.d
    ln -sf ../init.d/S05lp5812 ${D}${sysconfdir}/rcS.d/S05lp5812
}

FILES:${PN} += "${sysconfdir}/init.d/S05lp5812 ${sysconfdir}/rcS.d/S05lp5812"
