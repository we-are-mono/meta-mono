SUMMARY = "FCI kernel module for NXP ASK fast path"
DESCRIPTION = "Fast path Control Interface kernel module for communication with CMM daemon"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

inherit module

# Depends on cdx for symbol exports
DEPENDS += "cdx"

SRC_URI = "git://github.com/we-are-mono/ASK.git;protocol=https;branch=master \
           file://01-kernel-5.6-proc-ops.patch \
           file://02-makefile-kernel-src.patch \
           "
SRCREV = "56855f3463018937b3fafdfd2fa80a673561bc95"

S = "${WORKDIR}/git/fci-9.00.12"

# For ARM64
EXTRA_OEMAKE += "BOARD_ARCH=arm64"

# Override do_compile to pass KBUILD_EXTRA_SYMBOLS for cdx dependency
# The module.bbclass sets KBUILD_EXTRA_SYMBOLS="" which we need to override
do_compile() {
    unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS
    oe_runmake KBUILD_EXTRA_SYMBOLS="${STAGING_INCDIR}/cdx/Module.symvers"
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${B}/fci.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
}

RPROVIDES:${PN} += "kernel-module-fci"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
