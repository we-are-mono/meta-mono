# Backporting a Linux Kernel Driver into the Yocto Kernel Recipe

This documents the process used to backport the LP5812 LED driver from a newer upstream Linux kernel into the linux-mono kernel used by the Mono Yocto layer.

The example assumes:

- The Yocto kernel recipe is linux-mono.
- The target is the NXP Linux Factory tag pinned in `conf/include/nxp-base.inc` (`NXP_LF_TAG`, here `lf-6.18.20-2.0.0`), not a moving branch.
- The desired driver exists in a newer kernel branch.
- The driver is being added as a normal Yocto kernel patch.

The driver should be built into the kernel (`CONFIG_LEDS_LP5812=y`).

## 1. Identify the upstream commit

Find the commit containing the driver in the newer kernel tree.

For LP5812:
```
git log --oneline --all -- drivers/leds/rgb/leds-lp5812.c
```

This identified:
```
a0309dc699bc leds: Add basic support for TI/National Semiconductor LP5812 LED Driver
```

Inspect the commit before backporting:
```
git show --stat --oneline a0309dc699bc
git show --format=fuller --no-ext-diff a0309dc699bc -- \
    drivers/leds/rgb/leds-lp5812.c \
    drivers/leds/rgb/leds-lp5812.h \
    drivers/leds/rgb/Kconfig \
    drivers/leds/rgb/Makefile
```

Check whether the target kernel already contains the driver:
```
git log --oneline stable/linux-6.18.y -- drivers/leds/rgb/leds-lp5812.c
```
If there is no result, the driver needs to be backported.

Check for later upstream fixes to the same driver, and carry them with it:
```
git log --oneline a0309dc699bc..origin/master -- drivers/leds/rgb/leds-lp5812.*
git log --oneline --grep='Fixes: a0309dc699bc' origin/master
```

## 2. Start from the pinned tag

Create a dedicated branch from the tag the kernel recipe builds:
```
git switch -c lp5812-backport lf-6.18.20-2.0.0
```

Verify:
```
git log -1 --oneline
git status
```

The working tree should be clean before proceeding.

## 3. Cherry-pick the upstream commit

Apply the upstream driver commit, recording where it came from:
```
git cherry-pick -x a0309dc699bc
```

If conflicts occur, resolve them and inspect the result:
```
git status
git diff
```

For LP5812 the only conflict was in MAINTAINERS: its LP5812 entry arrives with a later binding commit, so 6.18 has no context for the hunk, and it was dropped.

Once all conflicts are resolved:
```
git add <resolved-files>
git cherry-pick --continue
```

Verify:
```
git status
git show --stat --oneline HEAD
git show --check HEAD
```

For the LP5812 backport, the resulting commit added:
```
drivers/leds/rgb/Kconfig
drivers/leds/rgb/Makefile
drivers/leds/rgb/leds-lp5812.c
drivers/leds/rgb/leds-lp5812.h
```

## 4. Add the Yocto patch status

Yocto's patch QA requires an Upstream-Status tag, placed before the Signed-off-by line. Add it to the commit message in the kernel tree, not to the exported patch, so that regenerating the patch keeps it:
```
git commit --amend
```
```
Upstream-Status: Backport [a0309dc699bc]
```

For a backport of a specific upstream commit, identifying the original commit is useful for tracking where the code came from.

## 5. Export the backport as a Yocto patch

Generate a patch from the backport commit:
```
git format-patch -1 --stdout HEAD > /tmp/0004-leds-lp5812.patch
```

Copy it into the Yocto layer:
```
cp /tmp/0004-leds-lp5812.patch \
    /path/to/meta-mono/recipes-kernel/linux/files/
```

## 6. Add the patch to the kernel recipe

The linux-mono recipe already uses patches from `recipes-kernel/linux/files/`.

Add the new patch to SRC_URI in linux-mono_6.18.bb:
```
SRC_URI = "git://github.com/nxp-qoriq/linux.git;protocol=https;nobranch=1 \
           file://defconfig \
           file://mono-gateway-dk.dts \
           file://001-hwmon-ina2xx-Add-INA234-support.patch \
           file://002-hwmon-emc2305-read-pwm-min-from-dt.patch \
           file://003-thermal-add-linear-governor.patch \
           file://0004-leds-lp5812.patch \
          "
```

## 7. Verify that Yocto applies the patch

Run bitbake through kas, which supplies the layer paths, machine and sstate:
```
kas shell -c 'bitbake -c patch linux-mono'
```

Or, when necessary to force re-execution:
```
kas shell -c 'bitbake -c patch -f linux-mono'
```

The patched kernel source is available under `build/tmp/work-shared/gateway-dk/kernel-source/`.

Verify that the driver is present:
```
grep -n 'config LEDS_LP5812' \
    build/tmp/work-shared/gateway-dk/kernel-source/drivers/leds/rgb/Kconfig
ls -l \
    build/tmp/work-shared/gateway-dk/kernel-source/drivers/leds/rgb/leds-lp5812.c
```

The patches are applied as commits, so the only change `git status` should show is the board DTS the recipe copies in, untracked:
```
git -C build/tmp/work-shared/gateway-dk/kernel-source status --short
```

## 8. Match the board DTS to the upstream binding

An upstream driver comes with its own binding, which need not match what an out-of-tree driver accepted. Check `recipes-kernel/linux/files/mono-gateway-dk.dts` against the binding before building.

For LP5812 the compatible is `ti,lp5812`, and `reg` is the address the driver shifts left by two before adding the register's upper bits. The node therefore carries `reg = <0x1b>`, where the out-of-tree driver took the shifted `0x6c`; left at `0x6c`, the chip is probed at the wrong address.

## 9. Enable the driver in the Yocto defconfig

The kernel recipe uses `recipes-kernel/linux/files/defconfig`.

Check the existing LED configuration:
```
grep -E '^CONFIG_(I2C|LEDS|LEDS_CLASS|LEDS_CLASS_MULTICOLOR)=' \
    recipes-kernel/linux/files/defconfig
```

For LP5812, the required dependencies include:
```
CONFIG_I2C=y
CONFIG_LEDS_CLASS=y
CONFIG_LEDS_CLASS_MULTICOLOR=y
```

Enable the driver built-in, paste this into recipe defconfig:
```
CONFIG_LEDS_LP5812=y
```

## 10. Build the kernel/image with Yocto

Once the patch and configuration are in place, build through kas:
```
kas shell -c 'bitbake linux-mono'
```

Or build the complete image:
```
kas build
```

Any architecture-specific compilation should be performed by the Yocto build system using the target toolchain.

## 11. Commit the Yocto-layer changes

Review the final changes:
```
git status
git diff -- recipes-kernel/linux/linux-mono_6.18.bb
git diff -- recipes-kernel/linux/files/defconfig
git diff -- recipes-kernel/linux/files/mono-gateway-dk.dts
```

Check that the new patch is present:
```
git status --short
```

The resulting layer change consists of:
```
recipes-kernel/linux/linux-mono_6.18.bb
recipes-kernel/linux/files/0004-leds-lp5812.patch
recipes-kernel/linux/files/defconfig
recipes-kernel/linux/files/mono-gateway-dk.dts
```

For LP5812 it also removed the out-of-tree driver it replaced, `recipes-kernel/lp5812-driver`, and that recipe's entry in the recovery image.

The kernel-tree backport commit remains separate from the Yocto-layer commit if the project maintains those histories separately.

## LP5812 backport summary

The complete flow was:
```
Newer kernel
    │
    ├── find driver commit and later fixes
    │      a0309dc699bc
    │
    ▼
Pinned kernel tag
    │
    ├── git switch -c lp5812-backport lf-6.18.20-2.0.0
    ├── git cherry-pick -x a0309dc699bc
    ├── resolve conflicts (MAINTAINERS hunk dropped)
    └── add Upstream-Status to the commit message
    │
    ▼
Export kernel commit
    │
    └── git format-patch
    │
    ▼
Yocto layer
    │
    ├── copy patch into recipes-kernel/linux/files/
    ├── add patch to SRC_URI
    ├── match the DTS to the binding (reg 0x1b)
    └── enable CONFIG_LEDS_LP5812=y
    │
    ▼
Yocto
    │
    ├── kas shell -c 'bitbake -c patch linux-mono'
    └── kas build
```
