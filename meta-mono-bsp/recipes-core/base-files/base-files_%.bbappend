FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " file://sysctl.d/01-ip-forward.conf \
                   file://sysctl.d/11-nf-conntrack.conf \
                   file://resolv.conf \
                   "

do_install:append() {
    # Install nf_conntrack sysctl configuration for CMM fast path
    install -d ${D}${sysconfdir}/sysctl.d
    install -m 0644 ${UNPACKDIR}/sysctl.d/01-ip-forward.conf ${D}${sysconfdir}/sysctl.d/
    install -m 0644 ${UNPACKDIR}/sysctl.d/11-nf-conntrack.conf ${D}${sysconfdir}/sysctl.d/

    # Static DNS for recovery image
    install -m 0644 ${UNPACKDIR}/resolv.conf ${D}${sysconfdir}/
}

hostname:pn-base-files = "recovery"
