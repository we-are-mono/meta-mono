# NXP ASK CMM integration for PPPoE relay hardware offload

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Build-time dependency for CMM headers and libcmm
DEPENDS += "cmm"

SRC_URI += "file://01-nxp-ask-cmm-relay.patch"

# pppoe-relay needs libcmm at runtime
RDEPENDS:${PN}-relay += "cmm"
