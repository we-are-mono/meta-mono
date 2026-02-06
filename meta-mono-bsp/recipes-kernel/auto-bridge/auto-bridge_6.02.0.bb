SUMMARY = "Auto Bridge kernel module for NXP ASK fast path"
DESCRIPTION = "Automatic bridge management kernel module for fast path offloading"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

inherit module

SRC_URI = "file://auto_bridge.c \
           file://auto_bridge_private.h \
           file://Makefile \
           file://include \
           "

S = "${WORKDIR}/sources"
UNPACKDIR = "${S}"

# For LS1043A/LS1046A family - per NXP ASK documentation
EXTRA_OEMAKE += "PLATFORM=LS1043A ENABLE_VLAN_FILTER=y"

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${B}/auto_bridge.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/

    # Install header for userspace (CMM)
    install -d ${D}${includedir}
    install -m 0644 ${S}/include/auto_bridge.h ${D}${includedir}/
}

# Separate -dev package for the header
ALLOW_EMPTY:${PN}-dev = "1"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
