# Recovery Image

The kernel and initramfs this layer builds (`linux-mono` + `recovery-image`, bundled into one FIT) are a **recovery and maintenance system**. The main OS ships separately and lives on eMMC.

## Scope of the recovery kernel

The recovery kernel's job is to bring the board up, show its health, reach the network and reflash firmware. It deliberately leaves out:

- netfilter and NAT
- DPAA hardware offload and fast paths
- anything else a router OS needs

Don't add these to `recipes-kernel/linux/files/defconfig`. Debug conveniences such as SysRq and debugfs are fine, because recovery already assumes physical access.

Some trade-offs follow from this scope:

- **Fan control uses 9 cooling steps** (`<&fan0 2 10>` in the DTS). The main OS may use a finer PWM-byte mapping. Nobody is around to hear a coarser fan ramp on a last-resort system, and the difference does not justify another kernel patch to carry through every NXP release.
- **Copper SFP+ hot-insert is not supported.** See [hardware-notes.md](hardware-notes.md#copper-sfp-modules-must-be-seated-at-boot).

## Security model: physical access

Recovery is reached with `run recovery` from the U-Boot console, or as the fallback when the eMMC OS fails to boot. Getting to it on purpose requires the UART, so the image ships with an **empty root password** (`IMAGE_FEATURES += "empty-root-password"`).

That is only safe while nothing listens on the network. `dropbear` is installed but never started, and `disable_dropbear_autostart` in `recovery-image.bb` guards against that changing. Anyone who starts it by hand must set a root password first. Don't add a network-facing service that lets someone in without the UART.

## Userland: BusyBox applets, bash shell

The utilities are BusyBox applets, but **bash is installed** and is root's login shell. The on-device `firmware` tool is a `#!/bin/bash` script.

| Where | Allowed |
|---|---|
| Scripts with `#!/bin/bash` | Bash syntax: `[[ ]]`, `BASH_REMATCH`, `set -euo pipefail` |
| Init scripts (`/etc/init.d/S*`), run by BusyBox `ash` | POSIX sh only |

External commands are BusyBox applets in both cases, so their flags are limited:

- `dd` supports `conv=`, `iflag=`, `oflag=` and `status=none`/`status=noxfer`. These come from `recipes-core/busybox/files/dd-conv.cfg`. There is no `status=progress`; send `SIGUSR1` to a running `dd` instead.
- `head` needs `-n N`, not `-N`.
- `awk` splits fields differently from GNU awk.
- `devmem` is enabled (`devmem.cfg`) for SoC register access during bring-up.

To close a gap like these, add a BusyBox config fragment under `recipes-core/busybox/files/` and list it in `busybox_%.bbappend`. Full GNU coreutils does not fit, because it pushes the FIT to about 27.3 MB against the 24 MiB window.

On a seekable input, `dd bs=1 skip=<large> count=<small>` is fast: `dd` seeks to the offset rather than reading up to it. Only `count` (or the size of a non-seekable input) costs time.

## The FIT window

`firmware-image.bb` enforces a hard **24 MiB** window for the recovery FIT (`check_size ... fitImage 24576`). The window is a fixed flash partition, so unused space is wasted, not saved. It is fine to fill it with whatever helps recovery. The build fails loudly on an overrun, so there is no risk of a silent overflow.

A few things help when the image is close to the limit:

- The initramfs is gzipped inside the FIT, so a rootfs saving shrinks about 3x by the time it reaches the FIT. For example, removing eudev saved 2,116 KiB of rootfs but only 705 KiB of FIT.
- `INITRAMFS_MAXSIZE` is a separate, earlier check, and it does real work. It once caught a 90 MB initramfs when `usbutils` pulled `eudev-hwdb` back in, which is why that package is listed in `BAD_RECOMMENDATIONS`. Keep it roughly in line with the FIT window instead of raising it on reflex.
- When you add a package, check what it recommends. Name the concrete packages in `BAD_RECOMMENDATIONS`, not the virtual provides.
