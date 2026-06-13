FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " file://issue"

do_install:append() {
    # Custom login banner
    install -m 0644 ${UNPACKDIR}/issue ${D}${sysconfdir}/issue
    install -m 0644 ${UNPACKDIR}/issue ${D}${sysconfdir}/issue.net
}

hostname:pn-base-files = "recovery"
