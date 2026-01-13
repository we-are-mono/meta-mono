# NXP ASK extensions for libnfnetlink
# Adds non-blocking socket mode and heap buffer management for CMM daemon

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://01-nxp-ask-nonblocking-heap-buffer.patch"
