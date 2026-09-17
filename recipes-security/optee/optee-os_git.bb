SUMMARY = "OP-TEE secure world OS for Mono Gateway board"
LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=c1f21c4f72f372ef38a5a4aee55ec173"

COMPATIBLE_MACHINE = "gateway-dk"

# Pinned to the same NXP Linux Factory release as the other BSP components
# (see conf/include/nxp-base.inc). NXP tags optee_os alongside atf, rcw,
# u-boot and linux, so it bumps with them rather than on its own schedule.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/optee_os;protocol=https;nobranch=1"
SRCREV = "${NXP_LF_SRCREV_OPTEE}"

PV = "${NXP_LF_TAG}"

DEPENDS = "python3-cryptography-native python3-pyelftools-native openssl-native"

inherit deploy python3native

B = "${WORKDIR}/build"

# The ls platform's ls1046ardb flavour matches this SoC: 4 cores, ARM64,
# CFG_WITH_ARM_TRUSTED_FW. Board differences from the RDB do not reach the
# secure world, which only touches the SoC.
OPTEE_PLATFORM = "ls-ls1046ardb"

# plat-ls implements no tee_otp_get_hw_unique_key, so the only definition
# available is the zeros-returning stub in core/kernel/otp_stubs.c, compiled
# solely under CFG_INSECURE. Until the OTPMK fuse is blown and CAAM supplies a
# real key this is the honest setting: OP-TEE announces the insecure state at
# boot rather than pretending its secure storage means anything.
EXTRA_OEMAKE = " \
    PLATFORM=${OPTEE_PLATFORM} \
    CFG_ARM64_core=y \
    CFG_INSECURE=y \
    CROSS_COMPILE=${HOST_PREFIX} \
    CROSS_COMPILE64=${HOST_PREFIX} \
    CROSS_COMPILE_core=${HOST_PREFIX} \
    CROSS_COMPILE_ta_arm64=${HOST_PREFIX} \
    HOST_PREFIX=${HOST_PREFIX} \
    COMPILER=gcc \
    LIBGCC_LOCATE_CFLAGS='${HOST_CC_ARCH}${TOOLCHAIN_OPTIONS}' \
    AFLAGS='${CFLAGS}' \
    ta-targets=ta_arm64 \
    O=${B} \
    V=1 \
"

# The OP-TEE build drives its own toolchain flags; inheriting OE's breaks it.
LDFLAGS[unexport] = "1"
CPPFLAGS[unexport] = "1"
AS[unexport] = "1"
LD[unexport] = "1"

# python3-cryptography needs the legacy provider to sign the embedded TAs.
export OPENSSL_MODULES = "${STAGING_LIBDIR_NATIVE}/ossl-modules"

do_compile() {
    oe_runmake -C ${S} all
}
do_compile[cleandirs] = "${B}"

do_install[noexec] = "1"

# BL32 must be headerless. The Layerscape platform never calls
# parse_optee_header() -- only imx, qemu, rpi3 and arm do -- so BL31 jumps
# straight to BL32_BASE and the first byte there has to be an instruction.
# tee.bin and tee-header_v2.bin both start with an "OPTE" magic that would be
# executed as code; tee-raw.bin is the one without it.
do_deploy() {
    install -d ${DEPLOYDIR}/optee
    install -m 0644 ${B}/core/tee-raw.bin ${DEPLOYDIR}/optee/tee-raw.bin
}

addtask deploy after do_compile before do_build
