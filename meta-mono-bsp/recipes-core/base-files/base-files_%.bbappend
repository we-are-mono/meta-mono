FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

do_install:append() {
    install -m 0644 ${UNPACKDIR}/issue ${D}${sysconfdir}/issue
}

hostname:pn-base-files = "recovery"