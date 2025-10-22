SUMMARY = "Status LED control for boot and online states"
DESCRIPTION = "Controls status LEDs with pulsing animation during boot and constant when online"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://status-led.sh \
    file://S95status-led-daemon \
"

S = "${WORKDIR}/sources"
UNPACKDIR = "${S}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/status-led.sh  ${D}${bindir}/

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${UNPACKDIR}/S95status-led-daemon ${D}${sysconfdir}/init.d/

    install -d ${D}${sysconfdir}/rcS.d
    ln -sf ../init.d/S95status-led-daemon ${D}${sysconfdir}/rcS.d/S95status-led-daemon
}

FILES:${PN} = "${bindir}/status-led.sh ${sysconfdir}/init.d/S95status-led-daemon ${sysconfdir}/rcS.d/S95status-led-daemon"
