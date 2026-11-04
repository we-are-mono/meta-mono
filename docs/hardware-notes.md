# Hardware Notes

Facts about the Gateway Development Kit that the code relies on. Several look like bugs when you first read them. They aren't. Each one was verified on hardware.

## SFP+ cages

### `CONFIG_SFP` must stay enabled

Optical SFP+ modules work in the recovery kernel **with** `CONFIG_SFP=y`. Code reading suggests the opposite. The SDK DPAA MAC never attaches a phylink, so `sfp.c` never reaches `SFP_DEV_UP` and leaves TX_DISABLE asserted. The obvious "fix" is to drop `CONFIG_SFP` and hog `gpio2 13/14` low, per the `tx-disable-gpios = <... GPIO_ACTIVE_HIGH>` description. That was tried, and it broke fiber: light was present, but `rx_packets` stayed at zero.

So the hardware needs those lines driven high for 10G optical operation, and the DTS polarity description does not match the board. Either the line is inverted, or those pins aren't TX_DISABLE at all. Until a schematic check settles it:

- Never remove `sfp.c` from the recovery defconfig.
- Never hog `gpio2 13/14` low.
- If you correct the polarity, retest with an **optical** module. A DAC ignores these pins and hides the problem.

### 10G MACs are fixed links

`fm1_mac9` and `fm1_mac10` (`ethernet@f0000` / `@f2000`) use `/delete-property/ managed;` and `fixed-link = <0 1 10000 0 0>`. The XFI SerDes is fixed by the RCW and has no in-band status, so phylink runs them as static links and never manages the module. `ethtool -m` returns "Operation not supported" on these ports. That is expected.

### Copper SFP+ modules must be seated at boot

A copper RJ45 SFP+ module (10GBASE-T) links only if it is in the cage at power-on. Inserted later, it is detected (MOD_DEF0 and EEPROM work, and dmesg prints the module ID), but LOS stays asserted and it never links. Bouncing the interface doesn't help.

The module only reaches a working state through the cold-boot path: the RCW-fixed SerDes, U-Boot's retimer init, and the FMan probe with the module present. Hotplug support would mean reworking FMan for phylink-managed SFP, which is days of driver work for a recovery image. Treat it as a known limitation and seat the module before boot.

### Reading XFI link state

The XFI PCS answers clause 45, devad 3, register 1 (`MDIO_STAT1`), at PHY address 0:

| Cage | MAC | MDIO bus |
|---|---|---|
| SFP0 | `fm1-mac9` (`ethernet@f0000`) | `mdio@f1000` |
| SFP1 | `fm1-mac10` (`ethernet@f2000`) | `mdio@f3000` |

- The link bit (`0x4`) reports up only after the MAC has been opened. Before that the register reads `0x2` even with a live peer. In U-Boot any net command opens the MAC, and `eth_halt()` afterwards does not undo it.
- `MDIO_STAT1` **latches low**. The first read after a link drop returns the stale value, so always read it twice.
- A cage running 1000BASE-X (port 1 under the `1g` RCW) uses the same PCS address, but you read it through the clause 22 BMSR instead.

### Port LEDs (`sfp-led`)

`recipes-kernel/sfp-led/files/sfp-led.c` drives each cage's link and activity LEDs from three sources: module presence from MOD_DEF0, link from the PCS, and activity from the netdev packet counters. It never touches i2c, on purpose. A module caught mid-transfer by a reset can hold SDA low until it loses power, and its port, and the neighbouring port on the same bus, must still light up.

| State | LEDs |
|---|---|
| No module | both off |
| Module, no link | solid orange |
| Link up | green on, orange blinks on traffic |

Design points:

- There is one platform device per port (`compatible = "mono,sfp-led-port"`), because 6.18 no longer exports `of_led_get()`.
- MOD_DEF0 is borrowed **non-exclusively** from `sfp.c`, which owns the line. The port waits until the sfp driver has bound to the cage, and the borrowed descriptor is never released: gpiolib hands a non-exclusive borrower the owner's descriptor without taking a reference.
- It does not take RTNL. The netdev is looked up under RCU with `dev_hold`. Taking RTNL several times a second starves other `rtnl_trylock` users.
- A user-selected LED trigger overrides the monitor.

The main OS kernel carries an **identical copy** of this file. A change to one copy must go to both, so diff them before editing.

U-Boot drives the same LEDs at the prompt (u-boot patch 0008), using the same three sources. To get a link reading on the XFI cages it opens their MACs at boot, and `board_quiesce_devices()` closes them before the kernel starts.

### Wedged SFP i2c bus

The cages stay powered across a warm reset. A module caught mid-transfer keeps driving SDA low, and nothing on `i2c1` answers, not even the PCA9545 mux's own control register. The U-Boot self-test recovers by pulsing the mux's `RESET#` on `GPIO3_24` and reports "Reset OK (recovered wedged bus)".

## Fan

The DK has **one fan**. The second header is for a later board revision and is never populated on the DK.

- `fan1_input` reports real RPM. `fan2_input` reading `0` is correct. The driver returns 0 both for an unwired tach and for a stalled fan, so the zero tells you nothing.
- The U-Boot fan self-test checks only channel 0, on purpose. Checking channel 1 would fail on every DK.
- The thermal cooling maps drive both channels, so `pwm2` mirrors `pwm1` while commanding an empty header.
- The part is an **EMC2302** (2 channels, i2c `0x2E`). The DTS says `microchip,emc2305`, which is register-compatible for the channels that exist.
- Thermal headroom is large. With four cores loaded for eight minutes the SoC reached 55 °C. The fan curve runs from 40 °C to full speed at 80 °C, and CPU throttling starts at 85 °C.
- The chip's `DRIVE_FAIL` fan-ageing detection (register `0x27`) needs the closed-loop RPM mode, which neither U-Boot nor the mainline driver enables, so `fanN_fault` is inert here.

## DDR4 timings

The static DDR4 configuration in `recipes-bsp/atf/files/0002-gateway-dk-add-DDR4-initialization.patch` comes from NXP's DDR calibration tool and has been stress-tested for months on real hardware. Some values look marginal against generic JEDEC tables, such as tRFC around 450 ns, fixed 1x refresh and maxed turnaround times. They are correct for the DRAM part fitted. Raise a concern only if a change makes the geometry or values inconsistent with each other.

## STUSB4500 USB-PD controller

The self-test (u-boot patch 0004) reprograms the STUSB4500's NVM when its contents differ from the expected configuration. That only happens on a unit's first boot, after factory programming. On every later boot the test only verifies, so NVM wear is not a concern.

## EEPROM write protect

The EEPROM's WP line (`GPIO3_00`) shares a GPIO bank with the port LEDs. The GPIO driver writes the whole data register back from its own copy, so a raw register write to WP gets undone. Always drive WP through the GPIO driver.

## CAAM job rings

When it is built in, OP-TEE takes CAAM job ring 2 (`CFG_JR_INDEX=2`) before Linux runs. The recovery DTS therefore disables `&sec_jr2`. Without that, the kernel logs "failed to flush job ring 2" on every boot. With OP-TEE left out the ring sits unused, which costs nothing. Any other kernel booted on this board needs the same line in its device tree.
