# OP-TEE

OP-TEE is the secure-world OS (BL32). `recipes-security/optee/optee-os_git.bb` builds it from `nxp-qoriq/optee_os` at the same LF tag as the other NXP components (`NXP_LF_SRCREV_OPTEE`), and TF-A loads it through the `opteed` dispatcher. Both boot chains share the FIP, so the secure world is present whichever chain boots. The recipe's comments explain each build flag in detail. This page covers the parts you need before you change anything.

## Integration gotchas

- **BL32 must be `tee-raw.bin`.** `tee.bin` and `tee-header_v2.bin` start with an `OPTE` header, which TF-A strips only on the imx, qemu, rpi3 and arm platforms. Layerscape jumps straight to `BL32_BASE`, so the header would run as code. The symptom is a silent hang right after `INFO: BL31: Initializing BL32`, which looks exactly like a bad memory map.
- **Pass `SPD=opteed BL32=...` to every TF-A `make` call**, not only the one that builds the FIP. BL2 is built per boot mode and loads BL32 itself, so leaving one out makes one PBL differ from the others.
- **Debugging the handoff needs `ATF_LOG_LEVEL=40`.** At the default `20` you can't see which images BL2 loads or where.
- **OP-TEE reserves 64 MiB of DDR.** `plat-ls` forces `CFG_TEE_OS_DRAM0_SIZE` to `0x4000000`, and no build flag gets it back.
- The `ls-ls1046ardb` platform flavour matches this SoC. The board's differences from NXP's reference board don't reach the secure world.

## Where the HUK comes from

`CFG_INSECURE=n` removes OP-TEE's zeros-returning HUK stub, so the build fails to link unless the CAAM driver supplies the HUK. It does, from CAAM's Master Key Verification Blob.

Whether that HUK is **unique to the device** depends on the SoC's security state, not on the build. The SEC derives keys from the OTPMK fuse only when the Security Monitor (SecMon) is in the **Trusted** or **Secure** state, and only a ROM-validated secure boot gets there. TF-A never moves it. In **Non-Secure**, the state of an unfused board and of one with only the OTPMK fused, the SEC uses a hardwired test key that is identical on every LS1046A.

So:

- Burning the OTPMK alone gives OP-TEE no device-unique key. The U-Boot self-test prints the SecMon state, and "OTPMK set, SecMon non-secure" means a burned key that nothing uses yet.
- Don't store real secrets in secure storage on a board that isn't running a validated secure boot.
- A unit's HUK changes when it moves to secure boot. Anything sealed before that, including the RPMB key derived from the HUK, is lost.

## RPMB

The RPMB key is `HMAC(HUK, eMMC CID)`. It is derived at every boot and never stored. Writing it to the eMMC can only be done **once** per chip.

- The release image builds with `CFG_RPMB_WRITE_KEY=n` and never writes a key. With `WRITE_KEY=y`, OP-TEE sends the derived key in the clear to whatever normal-world process answers "key not yet programmed". That answer can't be authenticated, and upstream says not to ship it.
- Keying is meant to happen in a separate provisioning build, with `WRITE_KEY=y`, behind `plat_rpmb_key_is_ready()` (patch 0001). That gate refuses unless SecMon is Trusted or Secure **and** the OTPMK is blown and undamaged. The recipe fails the build if `WRITE_KEY=y` is set but the gate is not linked in.
- The HUK sub-key derivation and the REE FS format are pinned (`CFG_CORE_HUK_SUBKEY_COMPAT=n`, `CFG_REE_FS_HTREE_HASH_SIZE_COMPAT=n`). Each unit stays bound to whatever was in force when it was first keyed, so **never change these after units are keyed.**
- Plan to keep a fused test board for every LF bump, to confirm the HUK and RPMB derivation haven't changed.

Two traps if you write code that reads SNVS:

- The LS1046A's SNVS block is **big-endian**. Code copied from OP-TEE's `imx_snvs.c`, which uses plain `io_read32`, reads byte-swapped values.
- On this platform nothing moves SecMon to Trusted except a ROM-validated boot. TF-A's `transition_snvs_trusted()` has no callers. A gate that waits for some other transition would never open.

## ASLR

`CFG_CORE_ASLR=y`, seeded from TF-A's SEC RNG through the NXP SiP SMC (patch 0002). U-Boot can't provide the seed, because it runs after BL32. If the SMC fails, OP-TEE logs a warning and maps the core at a fixed address.

Patch 0003 is an upstream backport (OP-TEE `05885944756e`). Without it, about one seed in thirty mapped the core across the 2 GB boundary in the 32-bit VA space, left no 1 GB slot for TAs, and panicked in `set_user_va_idx()`. Drop the patch once `NXP_LF_SRCREV_OPTEE` contains that commit.

U-Boot logs `RNG4 SH0 was instantiated w/o prediction resistance. Tearing it down` because TF-A instantiates the RNG without prediction resistance. This message is harmless.

## Not shipped

- No in-tree TAs (`CFG_BUILD_IN_TREE_TA=n`) and no embedded test PTAs (`CFG_ENABLE_EMBEDDED_TESTS=n`).
- `TA_SIGN_KEY` is still OP-TEE's default development key. Set a real `TA_PUBLIC_KEY`, or build with `CFG_WITH_USER_TA=n`, before any TA ships.
