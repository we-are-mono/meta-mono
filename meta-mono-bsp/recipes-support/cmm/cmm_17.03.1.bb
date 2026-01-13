SUMMARY = "CMM daemon for NXP ASK fast path"
DESCRIPTION = "Connection Manager Module daemon for managing fast path network offloading"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

DEPENDS = "libnetfilter-conntrack libfci libcli libpcap auto-bridge"

SRC_URI = "git://github.com/we-are-mono/ASK.git;protocol=https;branch=master \
           file://001-kernel-6.12-compat.patch;striplevel=2 \
           file://cmm.service \
           file://fastforward \
           "
SRCREV = "56855f3463018937b3fafdfd2fa80a673561bc95"

S = "${WORKDIR}/git/cmm-17.03.1"

inherit autotools pkgconfig systemd

# Disable parallel make - build order issues with libcmm
PARALLEL_MAKE = ""

# CMM needs the FCI header from libfci
# Build flags per NXP ASK Release Notes section 2.3.5:
# -DLS1043: Platform-specific code paths (LS1043A/LS1046A)
# -DFLOW_STATS: Flow statistics tracking
# -DWIFI_ENABLE: WiFi offload support
# -DSEC_PROFILE_SUPPORT: Security profile support
# -DUSE_QOSCONNMARK: QoS connection marking
# -DENABLE_INGRESS_QOS: Ingress QoS support
# -DIPSEC_NO_FLOW_CACHE: IPsec no flow cache
# -DVLAN_FILTER: VLAN filtering support
# -DAUTO_BRIDGE: Automatic L2 flow detection via auto_bridge kernel module
CFLAGS:append = " -I${STAGING_INCDIR} -DLS1043 -DFLOW_STATS -DWIFI_ENABLE -DSEC_PROFILE_SUPPORT -DUSE_QOSCONNMARK -DENABLE_INGRESS_QOS -DIPSEC_NO_FLOW_CACHE -DVLAN_FILTER -DAUTO_BRIDGE"

# Clean any leftover build files from git repo before autoreconf
do_configure:prepend() {
    if [ -f ${S}/Makefile ]; then
        cd ${S} && make distclean || true
    fi
    rm -f ${S}/config.log ${S}/config.status
}

do_install:append() {
    # Install systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/cmm.service ${D}${systemd_system_unitdir}/cmm.service

    # Install configuration file
    install -d ${D}${sysconfdir}/config
    install -m 0644 ${UNPACKDIR}/fastforward ${D}${sysconfdir}/config/fastforward
}

SYSTEMD_SERVICE:${PN} = "cmm.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

FILES:${PN} += "${sysconfdir}/config/fastforward"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
