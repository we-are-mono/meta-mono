# Security Fuses (SFP)

Development kits ship **unfused**. Blowing a fuse is permanent, and whether to do it is up to the owner. This page covers what the firmware supports and the hardware facts behind it. Read [optee.md](optee.md#where-the-huk-comes-from) first: an OTPMK on its own does not give OP-TEE a device-unique key.

The LS1046A allows **six fuse programming events (PROGFB) in its lifetime**. Each event burns every mirror register that is loaded, not just one word.

## Registers (LS1046A, Chassis 2)

Verified against TF-A's `include/drivers/nxp/sfp/sfp.h` and `plat/nxp/common/include/default/ch_2/soc_default_base_addr.h`. NXP's documentation lists LX2160 only. If you port this to another SoC, check the addresses rather than assuming.

| Register | Address | Notes |
|---|---|---|
| SFP base | `0x01E80000` | |
| SNVS base | `0x01E90000` | |
| `SFP_INGR` | `0x01E80020` | `0x2` = PROGFB, `0x1` = READFB, `0x100` = error |
| `OTPMKR0..7` | `0x01E80234`–`0x01E80250` | mirror words 13–20 |
| `SRKHR0..7` | `0x01E80254`–`0x01E80270` | mirror words 21–28 |
| `SNVS_HPSR` | `0x01E90014` | bit 27 `OTPMK_ZERO`: 1 = not blown |

Mirror words are numbered from `SFP + 0x200`. `OSPR0` is word 0, and ITS is bit 2 of it.

## Facts that cost time to learn

- **Both blocks are big-endian.** `md.l` shows them byte-swapped: `002b0088` on screen is `0x88002b00`.
- **The OTPMK mirrors don't read back.** They return all ones. Confirm that a key loaded by checking that `SNVS_HPSR` bit 27 cleared, not by reading the mirrors.
- **Any reset discards the mirrors**, not only a power cycle. A READFB reloads them from the fuses, which on a blank part means all zeros.
- **No fuse-block access of any kind is allowed while `TA_PROG_SFP` is above GND**, not even reads (LS1046A data sheet, Table 6 note 2). The pin must also be at GND during power-on, because powering up with the voltage applied can burn random bits.
- U-Boot's `ls2_sfp` driver would not probe without a `ta-sfp-prog-supply` regulator, and it shortened the programming pulse width (`SFPCR`) by 25% against the silicon default. u-boot patch 0006 fixes both.

## Hardware: `TA_PROG_SFP`

`TA_PROG_SFP` (POVDD) is pulled to GND through **R330**. The injection point is a **test point between R330 and the SoC**. No GPIO controls it, on purpose: if software could raise the programming voltage, anything with root could blow fuses, and setting ITS on a board with a blank SRK hash bricks it permanently.

From the LS1046A data sheet (Rev 4):

- 1.8 V ±90 mV, measured at the pin.
- 173 mW max while programming (about 96 mA). Use a 200–300 mA current limit.
- Grounded at all times except during programming.
- At least 100 SYSCLKs after `PORESET_B`. At a U-Boot prompt this is always met.

## The `otpmk` command

U-Boot carries a board command (`board/mono/gateway_dk/setup/cmd_otpmk.c`, u-boot patch 0003). There is no generic `fuse` command in the build, and you should not write the mirrors by hand with `mw.l`. A hand-written OTPMK has to be Hamming-encoded first, and a mistake wastes the one shot.

```
=> otpmk gen          # with TA_PROG_SFP at GND
=> md.l 0x01E90014 1  # bit 27 must now be clear (byte-swapped on screen)
   --- apply 1.8 V to the TA_PROG_SFP test point ---
=> otpmk commit       # one PROGFB
   --- return the pin to GND ---
=> reset              # read the "Security fuses" self-test line
```

`gen` draws 256 bits from the CAAM RNG, Hamming-encodes them (a port of TF-A's `otpmk_make_code_word_256()`), and loads the mirrors without ever printing the key. It does all the checks, because they have to run while the pin is at GND. It refuses if any of these is true:

- the OTPMK is already programmed
- the block is write-protected or reports a Hamming error
- `SFPCR` disables fuse writes
- SNVS reads back as zeros
- the mirrors would set ITS with a blank SRK hash
- the RNG output looks implausible

After loading, it confirms the key through `SNVS_HPSR`, checks that the other mirror words are unchanged, and lists every non-zero word that PROGFB will also burn. It stops the U-Boot SFP LED poll, because nothing else may touch the bus once the pin goes up.

`commit` **reads nothing**. It refuses only if no `gen` ran in this session. Whether the burn worked shows up in the **next boot's** self-test. It can't be checked before a reset, because the mirrors still hold the key until then.

### Rules

- Never `reset` between `gen` and `commit`. Committing after a reset programs blanks and probably still uses up a cycle.
- `gen` refuses a second time in the same session. Reset and start over instead.
- Rehearsal is free. A `gen` followed by `reset` changes nothing permanent: `SNVS_HPSR` goes from `0x88002b00` to `0x80002b00` and back.
- A damaged OTPMK after a burn shows as a `[WARN]` in the self-test, not a failure. It is permanent and you can't act on it.
