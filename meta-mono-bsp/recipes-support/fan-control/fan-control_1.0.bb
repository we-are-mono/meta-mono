SUMMARY = "Simple fan control init script"
DESCRIPTION = "Fixed fan speed for recovery Linux"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://S99fan-control"

S = "${WORKDIR}/sources"
UNPACKDIR = "${S}"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${UNPACKDIR}/S99fan-control ${D}${sysconfdir}/init.d/
    
    install -d ${D}${sysconfdir}/rcS.d
    ln -sf ../init.d/S99fan-control ${D}${sysconfdir}/rcS.d/S99fan-control
}

FILES:${PN} += "${sysconfdir}/init.d/S99fan-control"