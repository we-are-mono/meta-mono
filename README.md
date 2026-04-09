# Mono Gateway Yocto Layer

Yocto layer for the Mono Gateway development kit, an LS1046A-based device with 3 gigabit ports and 2 10-gigabit SFP+ ports.

## Prerequisites

Install dependencies (Debian/Ubuntu):

```bash
sudo apt-get install build-essential chrpath cpio debianutils diffstat file gawk gcc git iputils-ping libacl1 liblz4-tool locales python3 python3-git python3-jinja2 python3-pexpect python3-pip python3-subunit socat texinfo unzip wget xz-utils zstd pipx

pipx install kas
pipx ensurepath
source ~/.bashrc
```

## Building

Clone the repository:

```bash
git clone https://github.com/we-are-mono/meta-mono.git
cd meta-mono
```

Configure your build environment:

```bash
cp site.example.conf site.conf
# Edit site.conf - ensure DL_DIR and SSTATE_DIR exist with proper permissions
```

Build recovery and firmware images:

```bash
kas build
```

This uses `.config.yaml` (the kas default) which builds both `recovery-image` (BusyBox initramfs) and `firmware-image` (NOR/eMMC flash images).

## Flashing

### NOR Flash (QSPI)

From U-Boot or a running system, write the firmware image to NOR:

```bash
dd if=firmware-qspi.bin of=/dev/mtd0
```

### eMMC

When writing to eMMC, skip the first 4KB to preserve the partition table:

```bash
dd if=firmware-emmc.bin of=/dev/mmcblk0 bs=512 skip=8 seek=8
```

## NOR Flash Memory Map

The 32MB NOR flash image layout:

| Offset | Hex | Component |
|--------|-----|-----------|
| 0 | 0x000000 | RCW + BL2 |
| 1MB | 0x100000 | ATF FIP (BL31 + U-Boot) |
| 3MB | 0x300000 | U-Boot Environment |
| 4MB | 0x400000 | FMAN Microcode |
| 5MB | 0x500000 | Device Tree |
| 6MB | 0x600000 | Unallocated |
| 10MB | 0xA00000 | Kernel + Initramfs |

The eMMC memory map is identical, but RCW+BL2 starts at a 4KB offset because the CPU boots from that offset on eMMC to avoid the partition table region.

## Production Sources

Current Yocto release: **Walnascar** (Yocto 5.1)

| Package | Tag |
|---------|-----|
| [rcw](https://github.com/we-are-mono/rcw) | `mt-6.12.49-2.2.0` |
| [u-boot](https://github.com/we-are-mono/u-boot) | `mt-6.12.49-2.2.0` |
| [atf](https://github.com/we-are-mono/atf) | `mt-6.12.49-2.2.0` |
| [linux](https://github.com/nxp-qoriq/linux) | `lf-6.12.49-2.2.0` |


## Versioning Scheme

NXP uses Linux kernel minor version numbers for development of their firmware components (RCW, U-Boot, ATF) and Linux kernel. Their branches are prefixed with `lf-` and suffixed with `-y` (without patch numbers), e.g., `lf-6.12.y`. Stable releases are tagged with the full kernel version plus an internal SDK version, e.g., `lf-6.12.49-2.2.0`.

We follow the same convention, branching our work from NXP stable releases into `mt-` prefixed branches (e.g., `mt-6.12.y`) and tagging releases accordingly (e.g., `mt-6.12.49-2.2.0`).

Breaking down a tag like `mt-6.12.49-2.2.0`:

- **mt** - Mono Technologies Inc.
- **6.12.49** - Linux kernel version (major.minor.patch)
- **2.2.0** - NXP SDK release the branch is based on

Firmware images follow CalVer: `YYYY.MM.N` (e.g., `2026.03.1`), set via `FIRMWARE_VERSION` in `conf/machine/gateway-dk.conf`.

---

> **Warning: Frame Manager Microcode**
>
> All devices ship with a special build of Frame Manager microcode (fman-ucode) pre-flashed at offset `0x400000` (4MB) on NOR flash.
>
> Because this binary is proprietary to NXP, we cannot distribute it via GitHub. The [default recipe](recipes-dpaa/fm-ucode/fm-ucode_git.bb) builds a generic version.
>
> **Before flashing new firmware, back up the 4-5MB region from your NOR flash to preserve your microcode:**
> ```bash
> dd if=/dev/mtd4 of=/tmp/fman-ucode-backup.bin
> ```
