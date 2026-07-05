# Changelog

## 2026-07-05 — Code review follow-ups: build correctness, patch hygiene, self-test fixes
- firmware-image: recreate the image file every build (stale gap-residue fix), assert each component fits its flash window
- u-boot: env templates enter the task signature (SRC_URI); editing them now retriggers the build
- u-boot 0007 rework: adds TARGET_GATEWAY_DK alongside ls1046afrwy instead of replacing it; drops FSL_DDR_INTERACTIVE/SUPPORT_SPL debug selects (binary-identical)
- u-boot: fallback CONFIG_BOOTARGS matches the real 32MB flash layout (was RDB's 64MB + phantom NAND); board README rewritten for the actual hardware; dead RDB carry-over removed (board_eth_init, wrong RTC defines); erratum relabelled A-010539; XFI fixed-links 10G
- u-boot self-tests: DDR probe flushes dcache so it round-trips real DRAM both polarities; temperature test decodes signed TMP431 values, floor 0 °C (was 15 — cold rooms no longer fail), 60 °C ceiling documented as cooling-failure bound
- atf: drop DEBUG:=1/LOG_LEVEL:=40 leftovers (standalone builds now match the recipe); repair patch whitespace/modes (binary-identical)
- kernel: drop MODULE_FORCE_LOAD/UNLOAD; add SENSORS_TMP401, POWER_RESET_GPIO (poweroff now works), PANIC_TIMEOUT=10; thermal governor step_wise; remove unused usdpaa dtb and dead bootmem nodes
- fancontrol: resolve emc2305/cluster_thermal hwmon by name at boot (probe-order shifts no longer kill it); startup failures print to console, output logged
- lp5812: set led->chip before classdev registration (NULL-deref window); sfp-led: remove unreachable EPROBE_DEFER branch
- firmware-tools: explicit openssl-bin RDEPENDS; machine-arch packaging for firmware-tools/fancontrol/status-led; fm-ucode deploy task ordering; require (not include) cve-exclusion.inc
- MTD partitions: the kernel dts is the single source of truth — remove the never-expanded `mtdparts=${mtdparts}` from the environment (hush expands one level; the kernel always fell back to the dts table) and the mtdparts clause from the fallback CONFIG_BOOTARGS, since cmdline partitions would delete the `flash`/`backup` labels the firmware updater resolves by name
- Validated on hardware: QSPI flash, full self-test pass, closed-loop fan control, mt25qu512abb SPI-NOR id confirmed needed, /proc/mtd partition table confirmed dts-sourced
- Bump FIRMWARE_VERSION to 2026.07.1

## 2026-06-13 — Recovery UX polish: colored prompt, vim-tiny, firmware-tool output
- Recovery shell prompt: colored PS1 via /etc/profile.d — orange host (matches the /etc/issue banner, signals "you're in recovery") + gray working dir
- Add vim-tiny to the recovery image, with a minimal root vimrc so it doesn't fail to source defaults.vim (E1187) on startup
- firmware tool: red DO NOT INTERRUPT on the flash step, green ✓ on each success line, gray banner values — one palette shared with the prompt
- Bump FIRMWARE_VERSION to 2026.06.3

## 2026-06-13 — Security review follow-ups: supply-chain pinning, CVE scanning
- Pin external kas layers (bitbake, openembedded-core, meta-openembedded) to explicit commits, not just moving branches — the toolchain/core recipes that build everything can no longer shift between builds; bump deliberately like nxp-base.inc
- Enable CVE scanning via the `sbom-cve-check` fragment (`OE_FRAGMENTS` in site.conf/site.example.conf); non-gating — unpatched CVEs surface as build warnings plus a manifest in the deploy dir
- linux-mono: include upstream `cve-exclusion.inc` so the kernel report drops not-applicable/wrong-platform CVEs (83 → 14) with upstream's triage attached
- firmware: stage downloads in a private `mktemp` dir instead of the fixed `/tmp/firmware-update` path
- base-files: drop the sysctl.d drop-ins (ip_forward, nf_conntrack) — CMM fast-path tuning that has no place in the recovery/firmware image
- recovery: add IPv6 nameservers (Google + Cloudflare) to resolv.conf so DNS resolves on IPv6-only networks (#12)
- Bump FIRMWARE_VERSION to 2026.06.2

## 2026-06-12 — Migrate to wrynose
- Upgrade Yocto layer from walnascar to wrynose (bitbake 2.18)
- Switch file:// recipes to `S = ${UNPACKDIR}`; drop explicit `S = ${WORKDIR}/git` from git:// recipes
- Set `INIT_MANAGER = mdev-busybox` so wrynose's systemd default doesn't pollute DISTRO_FEATURES and break busybox-init services (fan, LEDs)
- Flatten systemd vs busybox conditionals in fancontrol, sfp-led, lp5812-driver
- Add S05lp5812 init script (walnascar loaded it via udev coldplug, wrynose tightened that path)
- Add `BB_HASHSERVE_DB_DIR = ${SSTATE_DIR}` so sstate reuse works
- Add `--url URL` flag to `firmware update` for local dev servers
- Rename BSP patches to drop redundant prefixes ("mono", CPU family)
- New Makefile with `make build` and `make serve`

## 2026-05-15 — Enable bootcount tracking in U-Boot for RAUC
- Enable `CONFIG_BOOTCOUNT_LIMIT` and `CONFIG_BOOTCOUNT_ENV` so Pilot's RAUC mark path has something to reset
- Leave `BOOTLIMIT=0` (no auto-rollback); slot policy stays runtime-configured via fw_setenv
- Bump FIRMWARE_VERSION to 2026.05.1

## 2026-04-19 — Bump FIRMWARE_VERSION to 2026.04.10
- Captures the BSP-recipe migrations to nxp-qoriq and the case-insensitive-USB fix from PR #11

## 2026-04-19 — Merge PR #11 (case-insensitive USB)
- firmware: fix false duplicate detection on exFAT/FAT32 USB drives

## 2026-04-19 — recipes-bsp/u-boot: track nxp-qoriq, carry Gateway-DK as patches
- Switch SRC_URI from we-are-mono/u-boot to nxp-qoriq/u-boot
- Carry Gateway-DK board support as a seven-patch series (board core, self-test harness, per-component self-tests, USB-PD/EEPROM, defconfig, DTS, upstream-tree wiring)
- Read per-boottype env templates directly from `${THISDIR}/files/` instead of via SRC_URI
- Pinned to NXP LF lf-6.12.49-2.2.0

## 2026-04-19 — recipes-bsp/rcw: track nxp-qoriq, carry Gateway-DK as a patch
- Switch SRC_URI from we-are-mono/rcw to nxp-qoriq/rcw
- Gateway-DK Makefile entry + QSPI/eMMC 2100 MT/s RCW files as a single patch
- Pinned to NXP LF lf-6.12.49-2.2.0

## 2026-04-19 — recipes-bsp/atf: track nxp-qoriq, carry Gateway-DK as patches
- Switch SRC_URI from we-are-mono/atf to nxp-qoriq/atf
- Gateway-DK board support split into two patches: board support, DDR4 initialization
- Pinned to NXP LF lf-6.12.49-2.2.0

## 2026-04-19 — Add NXP Linux Factory base config (conf/include/nxp-base.inc)
- Hoist NXP-tracked SRCREVs and kernel version into one include
- Lets atf/rcw/u-boot/linux all bump to a new NXP LF release by editing one place
- Migrates the linux recipe to use the inc; bsp recipes follow

## 2026-04-19 — Remove PROGRESS.md
- Dev-journal artifact, not useful as a tracked file

## 2026-04-18 — firmware: fix false duplicate detection on case-insensitive USB drives
- Probing for "firmware/", "Firmware/", and "FIRMWARE/" matched all three on exFAT/FAT32
- Track inode of each matched directory and skip duplicates
- Case-sensitive filesystems unaffected

## 2026-04-18 — Default firmware update to full rewrite, add --preserve-env
- `firmware update` now wipes the u-boot environment by default
- Old behavior (backup + restore) available via `--preserve-env`
- Banner shows preserve-env status; warning prints when env will be wiped
- Help text gains dedicated u-boot environment options section

## 2026-04-18 — Recovery: enable IPv6 in busybox tools (fixes #9)
- Add `ipv4 ipv6` to DISTRO_FEATURES so OE-core enables CONFIG_FEATURE_IPV6 in busybox
- Without this, ping/nslookup/wget rejected IPv6 literals as "bad address"
- Relax `firmware update`'s check_network to accept IPv4 or IPv6 default route

## 2026-04-18 — Add USB firmware update path
- Add `--usb` (auto-discover USB drive) and `--from PATH` (mounted dir) to `firmware update`
- USB search looks at root + firmware/ (case variants), mounts read-only
- Explicit errors: no match, multiple matches, incomplete pair
- Kernel: add CONFIG_NLS_CODEPAGE_437 and CONFIG_NLS_ISO8859_1 (without them all FAT/exFAT mounts fail with EINVAL)
- ATF: build with DEBUG=0 LOG_LEVEL=20 to quiet the boot logs

## 2026-04-18 — Fail build when firmware signing is required but unconfigured
- Add `FIRMWARE_SIGNING_REQUIRED` knob; when set, build aborts at parse time if key/pubkey missing
- Default stays "0" so dev workflows unchanged
- Document why recovery-image leaves root password empty

## 2026-04-18 — Inherit kernel-yocto and use alldefconfig mode
- Enables `.cfg` fragment support so downstream layers can inject kernel configs
- alldefconfig mode fills unspecified symbols from Kconfig defaults
- Drop the explicit defconfig copy in do_configure:prepend — kernel.bbclass handles it

## 2026-04-15 — Fix unbound MTD_ENV and MTD_FLASH in firmware tool
- Previous commit left two references to removed globals
- With `set -u`, this aborted the update after the main flash but before env restore
- Bump FIRMWARE_VERSION to 2026.04.6

## 2026-04-15 — Add USB storage support, GPIO poweroff, and fix regressions
- Kernel: enable SCSI/USB_STORAGE/UAS so USB drives appear as /dev/sda
- DTS: add gpio-poweroff so `shutdown -h` actually cuts power
- Recovery: add xz for wider archive support
- sfp-led: per-port counter (replaces static debug_count[2]), rtnl_trylock to avoid workqueue deadlock
- firmware tool: look up MTD partitions by label, target "flash" partition for whole-chip write
- Bump FIRMWARE_VERSION to 2026.04.5

## 2026-04-12 — Use machine-suffixed firmware filenames and fix BusyBox dd compatibility
- Firmware files now named `firmware-{qspi,emmc}-gateway-dk.bin` (MACHINE baked in at build time)
- Remove short-name symlinks from firmware-image deploy
- Replace `dd conv=fsync` with `dd + sync` for BusyBox
- Remove DEPLOY_DIR_IMAGE override (was causing pseudo inode conflicts)

## 2026-04-12 — Move SFP LED references into sfp-led-controller child nodes
- `sff,sfp` DT binding is `additionalProperties: false`; our custom `leds` property on SFP nodes violated the schema
- Move LED-to-SFP association into per-port child nodes under sfp_led_controller
- Driver updated to parse the new structure

## 2026-04-11 — Add boot medium detection and simplify firmware update tool
- U-Boot passes `boot_medium=qspi` or `boot_medium=emmc` on the kernel cmdline
- Firmware tool auto-detects and always targets the other medium
- Eliminates `--qspi`/`--emmc` flags
- Adds trap-based cleanup, conv=fsync on eMMC writes, consolidates docs to docs.mono.si

## 2026-04-11 — Harden firmware security, fix RTC, improve build quality
- Remove `curl -k` from firmware tool (TLS certs now verified)
- Make signature verification mandatory (die if pubkey missing)
- Enable hardware RTC + CONFIG_RTC_HCTOSYS so the clock is right at boot (fixes TLS date validation)
- Add `panic=10` bootarg so kernel reboots on panic instead of hanging
- Replace `dd bs=1` with `truncate + bs=1K` in firmware-image.bb
- Add LAYERDEPENDS, add COMPATIBLE_MACHINE to BSP recipes
- Bump FIRMWARE_VERSION to 2026.04.2

## 2026-04-10 — Document build artifacts output directory in README
- README points users at `dist/` for build artifacts

## 2026-04-10 — Add custom login banner and set hostname to recovery
- Orange ASCII art banner in `/etc/issue`
- Override hostname from gateway-dk to recovery

## 2026-04-10 — Add dist/ output directory and suppress boot console noise
- DEPLOY_DIR_IMAGE example in site.example.conf pointing to `./dist`
- Add `dist/` to .gitignore
- `loglevel=0` in kernel boot args suppresses non-panic messages

## 2026-04-10 — Add bitbake-cookerdaemon.log to .gitignore
- Stop tracking the bitbake cooker daemon log file

## 2026-04-10 — Flatten layer structure and separate ASK into standalone SDK
- Merge meta-mono-bsp and meta-mono-sdk into a single meta-mono layer at the repo root
- Remove all ASK fast-path networking recipes (cdx, fci, auto-bridge, cmm, etc.) and related bbappends — these move to a separate SDK repo
- Delete unused recipes: rp-pppoe, iproute2, mono-sdk-image, all meta-mono-sdk recipes
- Consolidate kas configs into a single `.config.yaml`
- Slim kernel defconfig (drop virtualization, WiFi, Bluetooth, unused filesystems; switch module compression from zstd to gzip)
- Use weak assignment for KERNEL_IMAGETYPE
- Update README, .gitignore, site.example.conf

## 2026-03-30 — Add firmware management tool and CalVer versioning
- Add `firmware` CLI to recovery image: signature verification, env backup/restore
- Supports both QSPI and eMMC targets
- Introduce FIRMWARE_VERSION (CalVer YYYY.MM.N) in machine config

## 2026-03-30 — Add optional ECDSA P-256 signing for firmware images
- firmware-image recipe signs images when FIRMWARE_SIGNING_KEY is configured
- Builds without keys produce unsigned images as before

## 2026-03-30 — Remove ENETC and TSN support
- LS1046A doesn't have ENETC hardware
- Drop ENETC/TSN patch from ASK kernel patch
- Drop CONFIG_TSN, CONFIG_FSL_ENETC, CONFIG_FSL_ENETC_VF from defconfig

## 2026-03-29 — Merge SDK device tree into base DT and remove unused board DTS patches
- Consolidate mono-gateway-dk.dts and mono-gateway-dk-sdk.dts into one file
- Single DT contains both hardware definitions and SDK DPAA overlays
- Remove all non-Mono board DT patches (dgw, rgw, rdb-w906x, ls1043a, ls1046a-rdb)

## 2026-03-28 — Update kernel patches and defconfig
- Fix INA234 register values (bus_voltage_shift, lsb, power_lsb_factor)
- Fix null-pointer check in br_fdb_can_expire callback
- Revise defconfig: drop KVM and ARM64_ERRATUM_834220, add UFS and NLS support

## 2026-03-28 — Remove fman ethernet alias ordering patch and DT aliases
- Deferred netdev registration hack no longer needed
- Remove 003-fman-respect-ethernet-aliases patch and DT ethernet aliases

## 2026-03-28 — Split U-Boot environment for QSPI and eMMC boot media
- Recovery command was hardcoded to NOR (`sf read`), wrong for eMMC boot
- Split env into a common base + per-boottype snippets
- Each firmware image gets the correct recovery command for its boot medium

## 2026-02-06 — Fix package name collision for DEB/APT backends (#5)
- Renames in lp5812-driver, auto-bridge, fci, cdx, sfp-leds to avoid collisions

## 2026-02-06 — Updated u-boot dependency
- Bumped u-boot to latest

## 2026-01-25 — Revised kernel config to better reflect NXP's suggestions for ASK
- Defconfig aligned with NXP's recommended config for ASK fast-path networking

## 2026-01-25 — Automated deployment to development target
- New tool automates build + flash cycle to a bench device

## 2026-01-25 — Add ASK patches for iproute2, ppp, and rp-pppoe
- Userspace patches required by the ASK kernel patch

## 2026-01-24 — Patch bugfixes
- Bugfixes in the ASK patch series

## 2026-01-24 — Cleaned up deadcode in ASK patch
- Removed unreachable / dead branches in the ASK port

## 2026-01-24 — Ported the rest of the ASK kernel patch
- Full port of ASK (5.4 → 6.12)
- Required updates to netfilter and auto-bridge to comply with newer APIs

## 2026-01-23 — Fixed a couple of recipe bugs, updated docs
- Recipe bugfixes and accompanying doc updates

## 2026-01-22 — Added memory map to README.md
- Documented the QSPI/eMMC memory map for firmware images

## 2026-01-22 — Added original 5.4 ASK patch
- Imported the upstream 5.4 ASK patch as the starting point for the port

## 2026-01-20 — Remove fm-ucode_git.bbappend from tracking
- File was already in .gitignore but still tracked

## 2026-01-18 — Updated to mt-6.12.49-2.2.0 release, documented versioning scheme
- Bumped to mt release tag
- README documents the versioning scheme

## 2026-01-18 — Consistent naming
- Naming consistency pass across the layer

## 2026-01-17 — Added name resolution to recovery
- DNS resolution now works in recovery image

## 2026-01-17 — Removed a bunch of stale patches
- Cleanup of patches that have been merged upstream

## 2026-01-17 — Fixed LED driver, added ethtool to recovery for debugging
- LED driver bugfix
- ethtool added to recovery image

## 2026-01-16 — kernel: OpenWRT 25.12.0-rc2 patched base (6.12.63) regenerated
- Kernel base regenerated on top of OpenWRT 25.12.0-rc2 patches

## 2026-01-16 — Minor improvements to u-boot environment, DT and SFP LED driver
- Small fixes across u-boot env, device tree, and SFP LED driver

## 2026-01-15 — ASK patch cleanup, SFP led driver done
- ASK patch cleanup
- SFP LED driver complete

## 2026-01-14 — SFP daemon removed in favor of a driver
- Replaced userspace SFP daemon with kernel driver

## 2026-01-13 — ASK patched in (needs separate firmware)
- ASK fast-path networking patch applied; depends on separate firmware

## 2026-01-09 — Added ASK device tree nodes
- DT nodes required for ASK

## 2025-11-12 — eMMC firmware image rework
- Reworked the eMMC firmware image layout

## 2025-11-09 — eMMC FW now builds a basic partition table, Erratum A-008127 bugfix
- Basic GPT included in the eMMC firmware image
- Fix for Erratum A-008127

## 2025-11-06 — Adapted fan control for the SDK image
- SDK image now has matching fan control

## 2025-11-03 — MAC assignment in order of ports, left to right
- MAC addresses now assigned to ports left-to-right as labeled on the chassis

## 2025-11-03 — Bash now default shell
- /bin/sh -> /bin/bash for root

## 2025-11-02 — Updated u-boot
- u-boot bump

## 2025-11-02 — Nicer recovery boot
- Cleanups to the recovery boot sequence

## 2025-11-02 — Changed hostname to simpler variant
- Simpler hostname

## 2025-11-02 — Recovery master LED now pulsing amber
- Visual indicator that we're in recovery

## 2025-11-01 — Proper fan control (by lm-sensors)
- lm-sensors based fan control replaces the simple fixed-speed setup

## 2025-11-01 — CAAM now built in (rather than a module)
- CAAM crypto driver compiled into the kernel

## 2025-11-01 — Added simple fan control for recovery (50% fixed speed)
- Fixed 50% fan speed in recovery

## 2025-10-22 — Master LED pulses in OS according to test status
- Master LED indicates self-test status by pulsing

## 2025-10-21 — Patched drivers for power sensor INA234
- Patches to enable INA234 power sensor

## 2025-10-21 — Improved CPU fan curve
- Tuned CPU fan curve

## 2025-10-21 — Fan 1 RPM is now tied to the core temperature
- Fan 1 RPM modulated by core temperature

## 2025-10-20 — Two layers are better than one, right?
- Split into multiple meta layers (meta-mono-bsp + meta-mono-sdk)

## 2025-10-19 — Pre-release housekeeping
- Cleanups before initial release

## 2025-10-15 — SFP LEDs now behave properly
- SFP LED behavior corrected

## 2025-10-10 — Latest u-boot (improved tests)
- u-boot bump with improved self-tests

## 2025-10-09 — Adds instruction for installing dependencies to README.md
- README gains dependency install instructions (incl. pipx)

## 2025-10-08 — First commit
- Initial import of the meta-mono Yocto layer
