SUMMARY = "ARM Trusted Firmware for Mono Gateway board"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://docs/license.rst;md5=83b7626b8c7a37263c6a58af8d19bee1"

COMPATIBLE_MACHINE = "gateway-dk"

DEPENDS = "u-boot-tools-native openssl-native u-boot rcw"
do_compile[depends] += "u-boot:do_deploy rcw:do_deploy"

# Pinned to an NXP Linux Factory release (see conf/include/nxp-base.inc
# for the tag and SHA). Mono's Gateway-DK board support is applied
# from files/ as patches.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/atf;protocol=https;nobranch=1 \
           file://0001-gateway-dk-add-board-support.patch \
           file://0002-gateway-dk-add-DDR4-initialization.patch \
           file://0003-tools-nxp-create_pbl-fix-unused-variable.patch \
"
SRCREV = "${NXP_LF_SRCREV_ATF}"

# Local variables
PLATFORM = "gateway_dk"
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

# Fix issue w/ missing paths to host toolchain and openssl-native
EXTRA_OEMAKE += "\
    host-cc='${BUILD_CC}' \
    OPENSSL_DIR='${RECIPE_SYSROOT_NATIVE}/usr' \
"

# TF-A falls back to `git describe --dirty` for the version banner it
# prints at every boot. Patches that touch tracked files leave the
# source tree dirty, which would render as "...-2.2.0-dirty" on the
# console, so name the release explicitly instead.
EXTRA_OEMAKE += "BUILD_STRING='${NXP_LF_TAG}'"

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
        oe_runmake pbl fip PLAT=${PLATFORM} BOOT_MODE=${d} DEBUG=0 LOG_LEVEL=20 RCW=${DEPLOY_DIR_IMAGE}/rcw/gateway_dk/${rcwimg} BL33=${DEPLOY_DIR_IMAGE}/${UBOOT_BINARY}
        cp ${S}/build/${PLATFORM}/release/bl2_${d}.pbl .
        cp ${S}/build/${PLATFORM}/release/fip.bin .
    done
}

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}/atf/
    install -m 0644 ${S}/*.pbl ${DEPLOYDIR}/atf/
    install -m 0644 ${S}/fip.bin ${DEPLOYDIR}/atf/
}

addtask deploy after do_compile
