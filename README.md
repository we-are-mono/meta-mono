# Mono Gateway Yocto Layer

Yocto layer for the Mono Gateway development kit, an LS1046A-based device with 3 gigabit ports and 2 10-gigabit SFP+ ports.

## Layers

- **meta-mono-bsp** - BSP layer that builds the firmware image, flashed to NOR flash or eMMC (with 32MB padding)
- **meta-mono-sdk** - SDK layer that builds a complete development image for exploring device internals, developing networking applications, or experimentation

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
cd meta-mono/kas
```

Configure your build environment:

```bash
cp site.example.conf site.conf
# Edit site.conf - ensure DL_DIR and SSTATE_DIR exist with proper permissions
```

Build firmware (NOR flash image):

```bash
kas shell distro/recovery.yaml
bitbake firmware
```

Build SDK image (eMMC development image):

```bash
kas shell distro/mono-sdk.yaml
bitbake mono-sdk-image
```

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

---

> **Warning: Frame Manager Microcode**
>
> All devices ship with a special build of Frame Manager microcode (fman-ucode) pre-flashed at offset `0x400000` (4MB) on NOR flash. This microcode includes NXP's ASK (Application Solutions Kit) which enables hardware packet acceleration and offloading.
>
> Because this binary is proprietary to NXP, we cannot distribute it via GitHub. The [default recipe](meta-mono-bsp/recipes-dpaa/fm-ucode/fm-ucode_git.bb) builds a version **without hardware offloading support**, which you can flash onto eMMC for learning purposes.
>
> **Before flashing new firmware, back up the 4-5MB region from your NOR flash to preserve the ASK-enabled microcode:**
> ```bash
> dd if=/dev/mtd4 of=/tmp/fman-ucode-backup.bin
> ```
