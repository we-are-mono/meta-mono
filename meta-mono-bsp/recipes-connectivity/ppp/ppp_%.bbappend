# NXP ASK extensions for tunnel interface index support

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://01-nxp-ask-ifindex.patch \
    file://01-tunnel-up.sh \
    file://01-tunnel-down.sh \
    "

do_install:append() {
    install -m 0755 ${UNPACKDIR}/01-tunnel-up.sh ${D}${sysconfdir}/ppp/ip-up.d/01-tunnel.sh
    install -m 0755 ${UNPACKDIR}/01-tunnel-down.sh ${D}${sysconfdir}/ppp/ip-down.d/01-tunnel.sh
}
