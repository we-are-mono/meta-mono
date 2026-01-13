SUMMARY = "Frame Manager Configuration tool"
DESCRIPTION = "FMC (Frame Manager Configuration) is a command-line tool that \
parses and applies FMan hardware configurations from XML policy files."
HOMEPAGE = "https://github.com/nxp-qoriq/fmc"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=a504ab5a8ff235e67c7301214749346c"

DEPENDS = "libxml2 fmlib tclap"

SRC_URI = "git://github.com/nxp-qoriq/fmc.git;protocol=https;nobranch=1 \
           file://01-mono-ask-extensions.patch \
           "
# Tag: lf-6.12.49-2.2.0
SRCREV = "5b9f4b16a864e9dfa58cdcc860be278a7f66ac18"

S = "${WORKDIR}/git"

# Convert CRLF to LF before patching (upstream has Windows line endings)
do_convert_crlf() {
    for f in ${S}/source/*.cpp ${S}/source/*.h ${S}/source/spa/*.c ${S}/source/spa/*.h; do
        if [ -f "$f" ]; then
            sed -i 's/\r$//' "$f"
        fi
    done
}
addtask convert_crlf after do_unpack before do_patch

EXTRA_OEMAKE = 'FMD_USPACE_HEADER_PATH="${STAGING_INCDIR}/fmd" \
    FMD_USPACE_LIB_PATH="${STAGING_LIBDIR}" \
    LIBXML2_HEADER_PATH="${STAGING_INCDIR}/libxml2" \
    TCLAP_HEADER_PATH="${STAGING_INCDIR}"'

# Platform mapping
EXTRA_OEMAKE_PLATFORM ?= ""
EXTRA_OEMAKE_PLATFORM:ls1043a = "ls1043"
EXTRA_OEMAKE_PLATFORM:ls1046a = "ls1046"

do_compile() {
    oe_runmake MACHINE=${EXTRA_OEMAKE_PLATFORM} -C source
}

do_install() {
    install -d ${D}${bindir}
    install -m 755 ${S}/source/fmc ${D}${bindir}

    install -d ${D}${sysconfdir}/fmc/config
    if [ -d ${S}/etc/fmc/config ]; then
        install -m 644 ${S}/etc/fmc/config/* ${D}${sysconfdir}/fmc/config/
    fi

    install -d ${D}${includedir}/fmc
    install -m 644 ${S}/source/fmc.h ${D}${includedir}/fmc/

    install -d ${D}${libdir}
    install -m 644 ${S}/source/libfmc.a ${D}${libdir}/
}

# Disable parallel make - fmc has build dependencies issues
PARALLEL_MAKE = ""

FILES:${PN} = "${bindir}/fmc ${sysconfdir}/fmc"
FILES:${PN}-dev += "${includedir}/fmc"
FILES:${PN}-staticdev += "${libdir}/libfmc.a"

COMPATIBLE_MACHINE = "(ls1043a|ls1046a|qoriq)"
