SUMMARY = "CDX kernel module for NXP ASK fast path"
DESCRIPTION = "Control Data Exchange kernel module providing fast path offload infrastructure"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

inherit module

SRC_URI = "git://github.com/we-are-mono/ASK.git;protocol=https;branch=mono-patched"
SRCREV = "cac8275e43251e17afaeec2cc1b8384303b20df0"

S = "${WORKDIR}/git/cdx-5.03.1"

# For LS1043A/LS1046A
EXTRA_OEMAKE += "PLATFORM=LS1043A"

# The CDX Makefile expects KERNELDIR for the build directory
EXTRA_OEMAKE += "KERNELDIR=${STAGING_KERNEL_BUILDDIR}"

# CONFIG_CFLAGS per ASK documentation - must match dpa_app flags
# SEC_PROFILE_SUPPORT - Security profile support (matches dpa_app)
# VLAN_FILTER - VLAN filtering support
EXTRA_OEMAKE += 'CFG_FLAGS="-DSEC_PROFILE_SUPPORT -DVLAN_FILTER -DWIFI_ENABLE -DENABLE_EGRESS_QOS"'

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${B}/cdx.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/

    # Install Module.symvers for dependent modules (e.g., fci)
    install -d ${D}${includedir}/cdx
    install -m 0644 ${B}/Module.symvers ${D}${includedir}/cdx/

    # Install headers for userspace apps (e.g., dpa_app)
    install -m 0644 ${S}/cdx_ioctl.h ${D}${includedir}/cdx/
}

# Export Module.symvers through sysroot for other modules to use
SYSROOT_DIRS += "${includedir}/cdx"
FILES:${PN}-dev += "${includedir}/cdx/Module.symvers ${includedir}/cdx/cdx_ioctl.h"
# Module.symvers contains build paths - this is expected and unavoidable
INSANE_SKIP:${PN}-dev += "buildpaths"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
