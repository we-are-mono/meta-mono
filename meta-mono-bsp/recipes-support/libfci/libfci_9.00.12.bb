SUMMARY = "FCI userspace library for NXP ASK fast path"
DESCRIPTION = "Userspace library for communicating with the FCI kernel module"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-or-later;md5=fed54355545ffd980b814dab4a3b312c"

SRC_URI = "git://github.com/we-are-mono/ASK.git;protocol=https;branch=mono-patched"
SRCREV = "cac8275e43251e17afaeec2cc1b8384303b20df0"

S = "${WORKDIR}/git/fci-9.00.12/lib"

inherit autotools pkgconfig

# Copy README and clean leftover build files before autoreconf runs
do_configure:prepend() {
    cp ${UNPACKDIR}/README ${S}/
    # Clean any leftover build files from git repo
    if [ -f ${S}/Makefile ]; then
        cd ${S} && make distclean || true
    fi
    rm -f ${S}/config.log ${S}/config.status
}

# Install header manually (autotools config doesn't include it)
do_install:append() {
    install -d ${D}${includedir}
    install -m 0644 ${S}/include/libfci.h ${D}${includedir}/
}

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
