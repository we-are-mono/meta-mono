SUMMARY = "OP-TEE secure world OS for Mono Gateway board"
LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=c1f21c4f72f372ef38a5a4aee55ec173"

COMPATIBLE_MACHINE = "gateway-dk"

# Pinned to the same NXP Linux Factory release as the other BSP components
# (see conf/include/nxp-base.inc). NXP tags optee_os alongside atf, rcw,
# u-boot and linux, so it bumps with them rather than on its own schedule.
require conf/include/nxp-base.inc
SRC_URI = "git://github.com/nxp-qoriq/optee_os;protocol=https;nobranch=1 \
           file://0001-plat-ls-refuse-to-program-the-RPMB-key-unless-the-bo.patch \
           file://0002-plat-ls-seed-core-ASLR-from-TF-A-s-SEC-RNG.patch \
           file://0003-core-lpae-retry-ASLR-when-no-user-mapping-entry-rema.patch \
           "
SRCREV = "${NXP_LF_SRCREV_OPTEE}"

PV = "${NXP_LF_TAG}"

DEPENDS = "python3-cryptography-native python3-pyelftools-native openssl-native"

inherit deploy python3native

B = "${WORKDIR}/build"

# The ls platform's ls1046ardb flavour matches this SoC: 4 cores, ARM64,
# CFG_WITH_ARM_TRUSTED_FW. Board differences from the RDB do not reach the
# secure world, which only touches the SoC.
OPTEE_PLATFORM = "ls-ls1046ardb"

# 1 prints errors only. 3 adds the debug trace, including where ASLR mapped
# the core ("Mapping core at ... offs ...").
OPTEE_LOG_LEVEL ?= "1"

# CAAM supplies the HUK: core/drivers/crypto/caam/blob/caam_blob.c defines
# tee_otp_get_hw_unique_key and reads the key from the Master Key
# Verification Blob. CFG_INSECURE=n removes the only alternative, the
# zeros-returning stub in core/kernel/otp_stubs.c, so the build fails to link
# unless the CAAM path is present -- a compile-time guarantee that the HUK
# never comes from a constant. Whether that HUK is device-unique is a property
# of the SecMon state and the fuse, not the build: the SEC derives keys from
# the OTPMK only while a ROM-validated boot has left the SecMon Trusted or
# Secure, and from a public test key otherwise, and the RPMB gate below acts
# on both. Nothing sealed under the test key is trustworthy afterwards.
#
# CFG_CORE_HUK_SUBKEY_COMPAT selects a derivation kept for devices already in
# the field: huk_compat() skips the usage tag for HUK_SUBKEY_RPMB and feeds a
# literal pattern in place of the die ID for the storage key. Both subkeys
# differ between the two settings, and each unit is bound to whichever was in
# force when its files were sealed and its one-shot eMMC RPMB key was written.
# Mono has no fielded units, so take the real derivation while it is free.
#
# The storage format is pinned for the same reason. At its default, y,
# CFG_REE_FS_HTREE_HASH_SIZE_COMPAT authenticates only 16 of the 32 bytes of
# the REE FS root hash, a layout kept for files written by old releases, and
# CFG_REE_FS_INTEGRITY_RPMB, which anchors that hash in RPMB, is inherited
# from CFG_RPMB_FS. Either default moving in a later release would leave
# existing files unreadable, and nothing is sealed yet.
#
# CFG_CAAM_INC_PRIBLOB raises CAAM's PRIBLOB field once OP-TEE has read the
# master key blob, leaving nothing that runs afterwards able to read it.
#
# CFG_RPMB_FS is on and CFG_RPMB_WRITE_KEY is off. OP-TEE reaches the eMMC
# only through the normal world, and with WRITE_KEY it hands the derived RPMB
# key, in clear, to whatever answers "authentication key not yet programmed"
# -- a status that is unauthenticated by nature, so a lying supplicant on any
# fused unit collects the key at will; upstream's config.mk says not to ship
# it. The fleet image therefore never writes a key. RPMB is keyed once per
# unit by the separate provisioning image, this recipe with WRITE_KEY=y,
# booted under a validated chain and signed with a revocable SRK slot; from
# then on this image only ever authenticates with the key.
#
# The two builds reach the eMMC differently. Without WRITE_KEY OP-TEE uses
# the probe interface, so on this kernel RPMB frames travel through the
# in-kernel RPMB class (OPTEE_RPC_CMD_RPMB_FRAMES), with no tee-supplicant
# involved. With it, OP-TEE always takes legacy_rpmb_init(): frames go out
# as OPTEE_RPC_CMD_RPMB, which only tee-supplicant answers, and the key is
# written on the first secure storage access -- with CFG_REE_FS on, the
# device PTA never starts RPMB itself. So the provisioning image also needs
# tee-supplicant, libteec and a TA that touches RPMB storage.
#
# The gate in the plat-ls patch, plat_rpmb_key_is_ready(), is what the
# provisioning image relies on: it refuses unless the boot was trusted
# (SecMon Trusted or Secure, which only a ROM-validated boot reaches) and
# the OTPMK is blown and sound.
#
# CFG_RPMB_TESTKEY is pinned off although it defaults off: on, it would key
# every eMMC this image reaches with a key published in the OP-TEE source.
# CFG_ENABLE_EMBEDDED_TESTS defaults on in plat-ls/conf.mk and links the
# self-test PTAs into the core, callable from the normal world.
# CFG_BUILD_IN_TREE_TA=n skips the TAs under ta/ -- pkcs11, avb and the rest
# -- which nothing deploys and which would be signed with the development key.
#
# CFG_TEE_CORE_DEBUG stays at its default, y, so assertions stay in: an
# internal inconsistency panics the TEE rather than carrying on.
#
# CFG_CORE_ASLR stays at its default, y. The core's map offset is seeded from
# TF-A's SEC RNG by the second plat-ls patch; without it plat-ls had no seed
# and the core sat at the same address on every boot. The third patch, an
# upstream backport, keeps a randomized layout from taking every 1GB slot TAs
# can use: in a 32-bit VA space with the identity map in the top slot, a core
# mapping that straddles 2GB would otherwise panic the boot in
# set_user_va_idx(), for roughly one seed in thirty.
#
# CFG_JR_INDEX names the CAAM job ring OP-TEE takes, which the recovery
# kernel's DTS disables (&sec_jr2). The CAAM driver forces it per platform, and
# a forced value that differs from the command line stops the build, so an LF
# release that moves the ring fails here rather than sharing it with Linux.
EXTRA_OEMAKE = " \
    PLATFORM=${OPTEE_PLATFORM} \
    CFG_ARM64_core=y \
    CFG_INSECURE=n \
    CFG_CORE_HUK_SUBKEY_COMPAT=n \
    CFG_REE_FS_HTREE_HASH_SIZE_COMPAT=n \
    CFG_REE_FS_INTEGRITY_RPMB=y \
    CFG_CAAM_INC_PRIBLOB=y \
    CFG_RPMB_FS=y \
    CFG_RPMB_WRITE_KEY=n \
    CFG_RPMB_TESTKEY=n \
    CFG_ENABLE_EMBEDDED_TESTS=n \
    CFG_BUILD_IN_TREE_TA=n \
    CFG_TEE_CORE_LOG_LEVEL=${OPTEE_LOG_LEVEL} \
    CFG_JR_INDEX=2 \
    CROSS_COMPILE=${HOST_PREFIX} \
    CROSS_COMPILE64=${HOST_PREFIX} \
    CROSS_COMPILE_core=${HOST_PREFIX} \
    CROSS_COMPILE_ta_arm64=${HOST_PREFIX} \
    HOST_PREFIX=${HOST_PREFIX} \
    COMPILER=gcc \
    LIBGCC_LOCATE_CFLAGS='${HOST_CC_ARCH}${TOOLCHAIN_OPTIONS}' \
    ta-targets=ta_arm64 \
    O=${B} \
    V=1 \
"

# The OP-TEE build drives its own toolchain flags. OE's would only add a
# second -O2 and -g ahead of OP-TEE's own -Os and -g3, and prefix maps that
# stretch every assertion's file name to a /usr/src/debug path.
CFLAGS[unexport] = "1"
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

# A build with CFG_RPMB_WRITE_KEY=y writes the eMMC's one-shot key, and must
# only ever do it behind plat_rpmb_key_is_ready(). The linker drops the gate
# whenever nothing calls it -- as in the fleet build, or after an LF bump that
# stops upstream calling the hook -- so refuse such a build without it.
do_compile:append() {
    if grep -q '^CFG_RPMB_WRITE_KEY=y$' ${B}/conf.mk; then
        ${NM} ${B}/core/tee.elf | grep -qw plat_rpmb_key_is_ready || \
            bbfatal "CFG_RPMB_WRITE_KEY=y, but plat_rpmb_key_is_ready() is not linked into tee.elf"
    fi
}

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
