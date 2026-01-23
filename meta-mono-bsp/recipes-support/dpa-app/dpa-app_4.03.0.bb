SUMMARY = "DPA App for NXP ASK fast path"
DESCRIPTION = "Data Path Acceleration application for managing fast path offload"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

DEPENDS = "libcli fmc fmlib cdx libxml2"
RDEPENDS:${PN} = "fmc fmlib libcli"

SRC_URI = "git://github.com/we-are-mono/ASK.git;protocol=https;branch=mono-patched \
           file://cdx_cfg.xml \
           "
SRCREV = "cac8275e43251e17afaeec2cc1b8384303b20df0"

S = "${WORKDIR}/git/dpa_app-4.03.0"

# Required defines per ASK documentation - must match cdx flags
# ENDIAN_LITTLE - ARM64 is little-endian
# LS1043 - Platform integration header selector (LS1046 also uses LS1043 DPAA integration)
#          This MUST match fmc's PLATFORM_FLAG to ensure fmc_model_t struct layout matches
# NCSW_LINUX - Select correct types header
# DPAA_DEBUG_ENABLE - For muram_data struct in cdx_ioctl.h
# SEC_PROFILE_SUPPORT - Security profile support
# VLAN_FILTER - VLAN filtering support
CFLAGS:append = " -DENDIAN_LITTLE -DLS1043 -DNCSW_LINUX -DDPAA_DEBUG_ENABLE -DSEC_PROFILE_SUPPORT -DVLAN_FILTER"

# Include paths for fmc, fmlib, and cdx headers
CFLAGS:append = " -I${STAGING_INCDIR}/fmc"
CFLAGS:append = " -I${STAGING_INCDIR}/fmd"
CFLAGS:append = " -I${STAGING_INCDIR}/fmd/integrations"
CFLAGS:append = " -I${STAGING_INCDIR}/fmd/Peripherals"
CFLAGS:append = " -I${STAGING_INCDIR}/fmd/Peripherals/common"
CFLAGS:append = " -I${STAGING_INCDIR}/cdx"

# No configure step needed
do_configure() {
    :
}

# Simple Makefile-based build
# Link order matters for static libs: object files, then libfmc (uses fmlib), then libfm-arm
# libfmc is C++ and uses libxml2, so we need -lstdc++ and -lxml2
do_compile() {
    oe_runmake CC="${CC}" CFLAGS="${CFLAGS}" LDFLAGS="${LDFLAGS} -lfmc -lfm-arm -lstdc++ -lxml2 -lpthread -lcli"
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/dpa_app ${D}${bindir}/

    # Install XML configuration files from source
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/files/etc/*.xml ${D}${sysconfdir}/

    # Overwrite cdx_cfg.xml with Mono Gateway DK specific config
    install -m 0644 ${UNPACKDIR}/cdx_cfg.xml ${D}${sysconfdir}/cdx_cfg.xml
}

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
