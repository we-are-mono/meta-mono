SUMMARY = "Cisco-like command-line interface library"
DESCRIPTION = "libcli provides a shared library for including a Cisco-like \
command-line interface into other software. It supports telnet or local \
connection, handles command history, and provides tab completion."
HOMEPAGE = "https://github.com/dparrish/libcli"
LICENSE = "LGPL-2.1-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=cb8aedd3bced19bd8026d96a8b6876d7"

SRC_URI = "git://github.com/dparrish/libcli.git;protocol=https;nobranch=1"
SRCREV = "6a3b2f96c4f0916e2603a96bf24d704f6a904e7a"
PV = "1.10.0+git"

S = "${WORKDIR}/git"

# libcli requires libcrypt for password hashing
DEPENDS = "libxcrypt"

# libcli uses a simple Makefile, not autotools
inherit pkgconfig

# The Makefile respects these variables
# Disable calloc-transposed-args warning - upstream code uses sizeof as first arg
EXTRA_OEMAKE = " \
    CC='${CC}' \
    AR='${AR}' \
    PREFIX='${prefix}' \
    DESTDIR='${D}' \
    CFLAGS='${CFLAGS} -Wno-calloc-transposed-args' \
    LDFLAGS='${LDFLAGS}' \
"

do_compile() {
    oe_runmake
}

do_install() {
    oe_runmake install
}

# Package the shared library and development files
FILES:${PN} = "${libdir}/libcli.so.*"
FILES:${PN}-dev += "${includedir}/libcli.h"
