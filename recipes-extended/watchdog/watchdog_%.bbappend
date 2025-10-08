SYSTEMD_AUTO_ENABLE:${PN} = "enable"
SYSTEMD_SERVICE:${PN} = "watchdog.service"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI += "file://watchdog.service"

do_install:append() {
    install -m 0644 ${UNPACKDIR}/watchdog.service ${D}${systemd_system_unitdir}/watchdog.service
}