DESCRIPTION = "Frame Manager User Space Library"
SECTION = "fman"
LICENSE = "BSD-3-Clause & GPL-2.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=9c7bd5e45d066db084bdb3543d55b1ac"

SRC_URI = "git://github.com/nxp-qoriq/fmlib.git;protocol=https;nobranch=1 \
           file://01-mono-ask-extensions.patch \
           "
# Tag: lf-6.12.49-2.2.0
SRCREV = "7a58ecaf0d90d71d6b78d3ac7998282a472c4394"

S = "${WORKDIR}/git"

# Avoid embedding build paths in static library
CFLAGS += "-fmacro-prefix-map=${STAGING_KERNEL_DIR}=/usr/src/kernel \
           -fdebug-prefix-map=${STAGING_KERNEL_DIR}=/usr/src/kernel"

EXTRA_OEMAKE = "DESTDIR=${D} PREFIX=${prefix} LIB_DEST_DIR=${libdir} \
        CROSS_COMPILE=${TARGET_PREFIX} KERNEL_SRC=${STAGING_KERNEL_DIR}"

# Architecture mapping
TARGET_ARCH_FMLIB ?= "arm"

FMLIB_TARGET = "libfm-${TARGET_ARCH_FMLIB}"

do_compile() {
    oe_runmake ${FMLIB_TARGET}.a
}

do_install() {
    oe_runmake install-${FMLIB_TARGET}
    # Remove kernel headers that shouldn't be installed by fmlib
    rm -rf ${D}/usr/src
}

do_compile[depends] += "virtual/kernel:do_shared_workdir"

# Disable debug package split - the prefix-mapped kernel paths confuse debugsrc extraction
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

ALLOW_EMPTY:${PN} = "1"

PACKAGE_ARCH = "${MACHINE_ARCH}"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
