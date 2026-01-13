# NXP ASK extensions for libnetfilter_conntrack
# Adds Comcerto fast path (fp_info), qosconnmark, and XFRM attributes for CMM daemon

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://01-nxp-ask-comcerto-fp-extensions.patch"
