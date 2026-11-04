# Boot Model

How the Gateway Development Kit chooses what to boot, and what happens when something on the way is broken.

## Two boot chains, selected by a DIP switch

The board has a DIP switch that selects the **boot chain**: RCW, BL2, the FIP (BL31 + OP-TEE + U-Boot) and the U-Boot environment all come from either QSPI NOR or eMMC. The switch does not select the OS.

| DIP position | Role |
|---|---|
| **QSPI** | The stable chain. It is meant to stay working whatever is on the eMMC. |
| **eMMC** | The developer's chain. Flash your own firmware here and flip the switch to test it. Breaking it is fine; flipping back to QSPI always gives a working bootloader. |

Both chains share one `bootcmd` (`recipes-bsp/u-boot/files/environment.txt`):

```
bootcmd=run emmc || run recovery
```

So the QSPI chain **tries the eMMC OS first** and falls back to the recovery system only if that fails. QSPI's job is to be a reliable bootloader for whatever OS you put on eMMC. It is not a rescue-only path. This is deliberate. Do not change QSPI to `bootcmd=run recovery`.

There is no automatic A/B rollback and no boot counter. The redundancy is manual: flip the DIP, boot recovery, run `firmware update`. That fits a development kit, where the owner has the board and a UART cable. If unattended recovery is ever needed, the path is U-Boot bootcount, plus a userspace health check, plus a kernel fallback across media.

## `boot_medium` and `firmware update`

Each chain's `recovery` command (`environment-qspi.txt`, `environment-emmc.txt`) puts `boot_medium=qspi` or `boot_medium=emmc` on the kernel command line. It names the medium the recovery kernel was loaded from.

`firmware update`, the on-device tool in `recipes-support/firmware-tools/files/firmware`, always writes the medium **opposite** `boot_medium`. It never overwrites the chain it is running from. To update QSPI, boot the eMMC chain, and the reverse.

## OS boot paths on eMMC

The `emmc` command tries two layouts on `mmc 0:1` (ext4):

1. `/boot/Image.gz` + `/boot/mono-gateway-dk.dtb`, started with `booti`.
2. `/boot/kernel.itb`, a FIT started with `bootm`.

Only the FIT path can ever carry a verified signature. A raw `Image.gz` cannot be verified.

## Corrupt U-Boot environment

Each medium has one 8 KiB environment copy at offset `0x300000`, and there is no redundant copy. If the CRC is bad, U-Boot falls back to its compiled-in default environment, which does not autoboot. The board stops at the U-Boot prompt.

This is an accepted risk, not a brick:

- U-Boot itself still comes up.
- The other medium's environment is separate. Flip the DIP, boot that chain's recovery, and run `firmware update`, which rewrites the bad environment.

If this ever needs to change, the cheapest fix is a `CONFIG_BOOTCOMMAND` that tries both media. The most robust fix is `CONFIG_SYS_REDUNDAND_ENVIRONMENT`.

## Image variants

`firmware-image` builds four images (`BOOTTYPE` in `recipes-core/images/firmware-image.bb`):

| BOOTTYPE | Deployed as | RCW SerDes protocol |
|---|---|---|
| `qspi` | `firmware-qspi-gateway-dk.bin` | `0x1133`: both SFP+ cages run XFI (10G) |
| `emmc` | `firmware-emmc-gateway-dk.bin` | `0x1133` |
| `qspi1g` | `1g/firmware-qspi-gateway-dk.bin` | `0x1333`: cage 1 runs 1000BASE-X |
| `emmc1g` | `1g/firmware-emmc-gateway-dk.bin` | `0x1333` |

The `1g` variants differ **only** in `SRDS_PRTCL_S1`. DDR and everything else are identical. They are not builds for a board with less RAM. They are deployed under `1g/` with the standard file names because `firmware update --url <base>` appends the fixed file name to the base URL, so the directory selects the variant.

The eMMC images place RCW+BL2 at a 4 KiB offset, because the SoC reads them from there to stay clear of the partition table. The layout is otherwise the same as NOR (see the memory map in the [README](../README.md)).
