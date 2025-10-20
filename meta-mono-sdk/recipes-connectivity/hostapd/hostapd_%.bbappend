FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI += "file://hostapd.conf"

do_install:append() {
    install -m 0644 ${UNPACKDIR}/hostapd.conf ${D}${sysconfdir}/hostapd.conf
}

FILES:${PN} += "${sysconfdir}/hostapd.conf"