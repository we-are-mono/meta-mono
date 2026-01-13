# Add QOSMARK/qosconnmark extensions for ASK fast path QoS marking
# These extensions work with kernel's nf_conntrack qosconnmark support

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://001-qosmark-extensions.patch"
