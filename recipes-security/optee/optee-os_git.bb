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

# CAAM supplies the HUK. core/drivers/crypto/caam/blob/caam_blob.c defines
# tee_otp_get_hw_unique_key as a strong symbol, overriding the weak stub in
# core/kernel/otp_stubs.c, and reads the key from the Master Key Verification
# Blob. CFG_INSECURE stays set because the OTPMK fuse is unblown, so that
# master key is not device-unique and there is no NV counter behind rollback
# protection: OP-TEE announces the insecure state at boot rather than implying
# its secure storage means anything. Nothing sealed now is trustworthy after
# the fuse, having been sealed under a key the SoC did not keep secret.
#
# CFG_CORE_HUK_SUBKEY_COMPAT selects a derivation kept for devices already in
# the field: huk_compat() skips the usage tag for HUK_SUBKEY_RPMB and feeds a
# literal pattern in place of the die ID for the storage key. Both subkeys
# differ between the two settings, and each unit is bound to whichever was in
# force when its files were sealed and its one-shot eMMC RPMB key was written.
# Mono has no fielded units, so take the real derivation while it is free.
#
# CFG_CAAM_INC_PRIBLOB raises CAAM's PRIBLOB field once OP-TEE has read the
# master key blob, leaving nothing that runs afterwards able to read it.
#
# CFG_RPMB_FS keeps its default of n. Every RPMB frame is an RPC to normal
# world (OPTEE_RPC_CMD_RPMB_FRAMES), so it needs a tee-supplicant, which the
# recovery image does not carry; turning it on would also default
# CFG_REE_FS_INTEGRITY_RPMB to y and anchor REE FS in an unkeyed RPMB.
EXTRA_OEMAKE = " \
    PLATFORM=${OPTEE_PLATFORM} \
    CFG_ARM64_core=y \
    CFG_INSECURE=y \
    CFG_CORE_HUK_SUBKEY_COMPAT=n \
    CFG_CAAM_INC_PRIBLOB=y \
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
