SUMMARY = "ARM Trusted Firmware for Mono Gateway board"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://docs/license.rst;md5=83b7626b8c7a37263c6a58af8d19bee1"

COMPATIBLE_MACHINE = "gateway-dk"

DEPENDS = "u-boot-tools-native u-boot rcw"
do_compile[depends] += "u-boot:do_deploy rcw:do_deploy"

# Pinned to an NXP Linux Factory release (see conf/include/nxp-base.inc
# for the tag and SHA). Mono's Gateway-DK board support is applied
# from files/ as patches.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/atf;protocol=https;nobranch=1 \
           file://0001-plat-nxp-ls1046a-gateway-dk-add-board-support.patch \
           file://0002-plat-nxp-ls1046a-gateway-dk-add-DDR4-initialization.patch \
           file://0003-plat-nxp-ls1046a-gateway-dk-add-semihost-boot-suppor.patch \
"
SRCREV = "${NXP_LF_SRCREV_ATF}"

S = "${WORKDIR}/git"

# Local variables
PLATFORM = "gateway_dk"
UBOOT_BINARY = "u-boot.bin"
BOOTTYPE ?= "qspi emmc semihost"

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
            atf_bl2_target="pbl"
            bl2_artifact_src="bl2_${d}.pbl"
            bl2_artifact_dst="bl2_${d}.pbl"
            rcw_arg="RCW=${DEPLOY_DIR_IMAGE}/rcw/gateway_dk/${RCWQSPI}"
            ;;

        emmc)
            atf_bl2_target="pbl"
            bl2_artifact_src="bl2_${d}.pbl"
            bl2_artifact_dst="bl2_${d}.pbl"
            rcw_arg="RCW=${DEPLOY_DIR_IMAGE}/rcw/gateway_dk/${RCWEMMC}"
            ;;

        semihost)
            atf_bl2_target="bl2"
            bl2_artifact_src="bl2.bin"
            bl2_artifact_dst="bl2_${d}.bin"
            rcw_arg=""
            ;;

        esac

        make V=1 realclean
        oe_runmake ${atf_bl2_target} fip PLAT=${PLATFORM} BOOT_MODE="$d" DEBUG=0 LOG_LEVEL=20 ${rcw_arg} BL33=${DEPLOY_DIR_IMAGE}/${UBOOT_BINARY}
        cp ${S}/build/${PLATFORM}/release/${bl2_artifact_src} ./${bl2_artifact_dst}
        cp ${S}/build/${PLATFORM}/release/fip.bin .
    done
}

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}/atf/
    install -m 0644 ${S}/*.pbl ${DEPLOYDIR}/atf/
    if ls ${S}/bl2_*.bin >/dev/null 2>&1; then
        install -m 0644 ${S}/bl2_*.bin ${DEPLOYDIR}/atf/
    fi
    install -m 0644 ${S}/fip.bin ${DEPLOYDIR}/atf/
}

addtask deploy after do_compile
