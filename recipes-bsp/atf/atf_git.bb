SUMMARY = "ARM Trusted Firmware for Mono Gateway board"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://docs/license.rst;md5=83b7626b8c7a37263c6a58af8d19bee1"

DEPENDS = "u-boot-tools-native u-boot rcw"
do_compile[depends] += "u-boot:do_deploy rcw:do_deploy"

SRC_URI = "git://github.com/we-are-mono/atf;protocol=https;branch=mt-6.12.34-2.1.0"
SRCREV = "177e43c3ef9315bea37de6603a020e343171eead"

S = "${WORKDIR}/git"

# Local variables
PLATFORM = "${MACHINE}"
UBOOT_BINARY = "u-boot.bin"
BOOTTYPE ?= "qspi emmc"

# requires CROSS_COMPILE set by hand as there is no configure script
export CROSS_COMPILE = "${TARGET_PREFIX}"
export ARCH = "arm64"

# Let the Makefile handle setting up the CFLAGS and LDFLAGS as it is
# a standalone application
CFLAGS[unexport] = "1"
LDFLAGS[unexport] = "1"
AS[unexport] = "1"
LD[unexport] = "1"

do_configure[noexec] = "1"

do_compile() {
    for d in ${BOOTTYPE}; do
        case $d in
        qspi)
            rcwimg="${RCWQSPI}"
            ;;
        emmc)
            rcwimg="${RCWEMMC}"
            ;;
        esac
        
        make V=1 realclean
        oe_runmake pbl fip PLAT=${PLATFORM} BOOT_MODE=${d} RCW=${DEPLOY_DIR_IMAGE}/rcw/gateway_dk/${rcwimg} BL33=${DEPLOY_DIR_IMAGE}/${UBOOT_BINARY}
        cp ${S}/build/${PLATFORM}/release/bl2_${d}.pbl .
        cp ${S}/build/${PLATFORM}/release/fip.bin .
    done
}

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}/atf/
    install -m 644 ${S}/*.pbl ${DEPLOYDIR}/atf/
    install -m 644 ${S}/fip.bin ${DEPLOYDIR}/atf/
}

addtask deploy after do_compile