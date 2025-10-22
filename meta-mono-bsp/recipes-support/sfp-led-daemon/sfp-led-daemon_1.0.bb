SUMMARY = "SFP LED Control Daemon"
DESCRIPTION = "Monitors network interfaces and controls SFP LEDs"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'systemd', '', d)}

SRC_URI = "file://src"

S = "${WORKDIR}/src"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o sfp-led-daemon sfp-led-daemon.c
}

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 sfp-led-daemon ${D}${sbindir}/
    
    # Install systemd service if systemd is enabled
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/src/sfp-led-daemon.service ${D}${systemd_system_unitdir}/
    else
        # For busybox (recovery image), we just place it into /etc/init.d
        install -d ${D}${sysconfdir}/init.d
        install -m 0755 ${WORKDIR}/src/S99sfp-led-daemon ${D}${sysconfdir}/init.d/

        install -d ${D}${sysconfdir}/rcS.d
        ln -sf ../init.d/S99sfp-led-daemon ${D}${sysconfdir}/rcS.d/S99sfp-led-daemon
    fi
}

SYSTEMD_SERVICE:${PN} = "sfp-led-daemon.service"
SYSTEMD_AUTO_ENABLE = "enable"

FILES:${PN} = "${sbindir}/sfp-led-daemon"
FILES:${PN} += "${@bb.utils.contains('DISTRO_FEATURES', 'systemd', '', '${sysconfdir}/init.d/S99sfp-led-daemon ${sysconfdir}/rcS.d/S99sfp-led-daemon', d)}"