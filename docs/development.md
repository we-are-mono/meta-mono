# Development Workflow

Conventions for changing this layer. For a worked example of adding a kernel driver, see [backporting.md](backporting.md).

## Build through kas only

Run `kas build` from the layer root. Don't run `bitbake` directly, because it won't have the layer paths, machine config and sstate settings that kas sets up. To run bitbake commands, go through kas:

```
kas shell -c 'bitbake -e u-boot'          # inspect a recipe's variables
kas shell -c 'bitbake -c patch linux-mono' # apply patches only
```

The kas stack is deliberately small: oe-core, meta-openembedded and this layer. Everything from NXP is built from upstream git. Don't add meta-arm, meta-freescale or similar layers to get a recipe. Write a recipe here that mirrors the existing ones.

## NXP Linux Factory base

The NXP-tracked recipes (`atf`, `rcw`, `u-boot`, `linux-mono`, `optee-os`) all pin to one NXP Linux Factory release. The tag and the per-repo SHAs live in [`conf/include/nxp-base.inc`](../conf/include/nxp-base.inc).

To move to a newer LF release:

1. Look up the new tag's commit in each repo:
   `git ls-remote https://github.com/nxp-qoriq/<repo>.git "refs/tags/lf-*"`.
   The lines ending in `^{}` are the underlying commits; those are the SRCREVs.
2. Update every `NXP_LF_*` variable in `conf/include/nxp-base.inc`.
3. Update `NXP_LF_LINUX_VERSION`, and rename `recipes-kernel/linux/linux-mono_<X.Y>.bb` to the new major.minor.
4. Run `kas build`. If a patch fails to apply, fix that one patch (see below). Don't regenerate the whole series.
5. Check the backports that carry a "drop once upstream" note, such as OP-TEE's patch 0003, and remove the ones the new tag already contains.

The board patches are written to apply across LF releases: they are additive board ports plus small Kconfig and Makefile changes, and fuzz absorbs context drift.

## Changing patches

Every NXP-tracked recipe is an upstream git tree at a pinned SRCREV, plus a series of patches under its `files/` directory. To change that series, change commits in a git tree and regenerate the patches from them.

**Never hand-edit a `.patch` file.** Hand edits leave stale metadata behind, such as wrong index hashes, and cause surprises at apply time.

These rules apply whichever workflow you use:

- Every patch needs an `Upstream-Status:` line before `Signed-off-by:`, or `do_patch` fails QA. Put it in the commit message, so regenerating keeps it.
- Keep each existing patch's file name, because `SRC_URI` refers to it.
- A new patch gets the next free number in its series and goes at the end of `SRC_URI`.
- Commit only the patches whose content actually changed.

| Recipe | Repo (`github.com/nxp-qoriq/`) | SRCREV | Patches |
|---|---|---|---|
| `u-boot` | `u-boot` | `NXP_LF_SRCREV_UBOOT` | `recipes-bsp/u-boot/files/` |
| `atf` | `atf` | `NXP_LF_SRCREV_ATF` | `recipes-bsp/atf/files/` |
| `rcw` | `rcw` | `NXP_LF_SRCREV_RCW` | `recipes-bsp/rcw/files/` |
| `optee-os` | `optee_os` | `NXP_LF_SRCREV_OPTEE` | `recipes-security/optee/files/` |
| `linux-mono` | `linux` | `NXP_LF_SRCREV_LINUX` | `recipes-kernel/linux/files/` |

### Option A: plain git

This works for every recipe. These steps use U-Boot as the example; change the variables for the other recipes.

1. Check out the pinned commit and apply the layer's series:

   ```
   LAYER=/path/to/meta-mono
   SRCREV=$(sed -n 's/^NXP_LF_SRCREV_UBOOT *= *"\(.*\)"/\1/p' $LAYER/conf/include/nxp-base.inc)
   git clone https://github.com/nxp-qoriq/u-boot.git && cd u-boot
   git checkout -b work $SRCREV
   git am $LAYER/recipes-bsp/u-boot/files/*.patch
   ```

   `git am` warns about trailing whitespace in some existing patches. Ignore the warnings.

2. Make your change:
   - to amend an existing patch, `git commit --fixup=<commit>` and then `git rebase -i --autosquash $SRCREV`;
   - to add a patch, commit on top.

3. Regenerate the series:

   ```
   git format-patch --zero-commit -N -o ../out $SRCREV..
   ```

   `--zero-commit` writes `From 0000…` instead of each commit's SHA, so a rebase doesn't touch the patches after the one you edited. `-N` writes `[PATCH]` with no `n/m` numbering.

4. Copy the results back. `format-patch` names files after the full subject, while the layer keeps shorter names, so match them by position:

   ```
   paste -d' ' <(ls $LAYER/recipes-bsp/u-boot/files/*.patch) <(ls ../out/*.patch) |
       while read dst src; do cmp -s "$src" "$dst" || cp "$src" "$dst"; done
   ```

   A new patch is the extra file at the end of `../out`. Copy it in under the next number and add it to `SRC_URI`.

5. Check the result: `git -C $LAYER diff --stat` should list only the patches you meant to change.

Without any change, this round trip gives back byte-identical files for the U-Boot series. If your git version differs, the `-- ` signature line at the end of each patch differs too, which is another reason to copy only the patches whose content changed.

**Kernel patches** are independent backports and fixes rather than one series, so export the one commit you changed with `git format-patch -1 --stdout <commit>`. [backporting.md](backporting.md) walks through a complete example.

### Option B: devtool

`devtool` works under kas for `u-boot`, `atf`, `rcw` and `optee-os`. **Don't use it for `linux-mono`.** `kernel-yocto` applies the patches itself, so devtool can't find the start of the series, and `devtool update-recipe linux-mono` deletes every patch from the recipe. Use option A for the kernel.

```
kas shell                                   # opens a shell in build/
devtool modify u-boot                       # tree in workspace/sources/u-boot, one commit per patch
cd workspace/sources/u-boot
# ... amend commits (fixup + autosquash, as above) or commit on top ...
cd -
devtool update-recipe u-boot                # rewrites the changed patches in place
devtool reset u-boot                        # atf and rcw need: devtool reset -n <recipe>
```

While a recipe is in the workspace, `kas build` builds it from `workspace/sources/`. Run `devtool reset` before you build anything you mean to ship. For `atf` and `rcw`, plain `devtool reset` fails because the source tree's `make clean` doesn't work outside the recipe's environment. `-n` skips that step.

`update-recipe` keeps existing file names, but it differs from the layer's conventions in three ways. Clean up before committing:

1. It writes each commit's real SHA into the `From` line, for the patch you edited and every patch after it. Restore the zero SHA:

   ```
   sed -i '1s/^From [0-9a-f]\{40\} /From 0000000000000000000000000000000000000000 /' \
       ../recipes-bsp/u-boot/files/*.patch
   ```

2. It drops the `-- ` signature footer. After the `sed`, any patch whose only remaining change is the missing footer can be restored with `git checkout`.
3. A new commit becomes `0001-<subject>.patch` at the end of `SRC_URI`. Rename it to the next number in the series and update `SRC_URI` to match.

### Checking that the patches apply

```
kas shell -c 'bitbake -c patch -f u-boot'
```

Then build and check the artifact, as described below.

## Verify the artifact, not just the build

A successful build is not the test. After `kas build`, check the deployed images:

- `grep` the `firmware-*.bin` for strings that must, or must not, be present.
- When a change is meant to leave the binary unchanged, compare bytes. Hash equivalence skipping the downstream rebuilds is the expected sign.

Comparing whole `firmware-*.bin` files only works when nothing else in the change touches package metadata. Even editing a recipe's `DESCRIPTION` re-runs `do_package`, then `do_rootfs`, then `do_bundle_initramfs`, so every image changes. In that case compare the component instead: `atf/fip.bin`, `fitImage` or the microcode. Or build the component both ways and `cmp` the results.

## Debug log levels

| Variable | Default | Debug | Notes |
|---|---|---|---|
| `ATF_LOG_LEVEL` | `20` | `40` | 40 makes BL2 print every image it loads and the load address. It also prints BL31's random numbers, so never ship it. |
| `OPTEE_LOG_LEVEL` | `1` | `3` | 3 adds the debug trace, including where ASLR mapped the core. |

Set them for a bench build in a kas override or in `site.conf`, not in the recipe.

## Where a recipe belongs

A recipe belongs in this layer if it changes what `kas build` produces here: U-Boot config, kernel patches, the device tree, ATF, OP-TEE, or a package one of this layer's images installs. A userspace recipe that only the main OS consumes belongs in that OS's layer, even if its values come from this layer's flash layout.

## Code comments

Write comments in the present tense, about what the code does now. Don't write "no longer", "used to", "replaces the old X" or "don't re-add Y". That history goes stale and belongs in the commit message. If something is no longer needed, delete it without leaving a note behind.

## Testing a build on the device

Sign your images so the device accepts them. `site.example.conf` shows how to generate a key and set `FIRMWARE_SIGNING_KEY` / `FIRMWARE_SIGNING_PUBKEY`. The build refuses a pubkey that differs from the one `firmware-tools` installs on the device. For local testing, replace `recipes-support/firmware-tools/files/firmware-signing.pub` with your own pubkey and flash that build once by hand.

Then serve the build output and update from recovery:

```
make serve                                     # on the build host, serves dist/ on :8000
firmware update --url http://<build-host>:8000 # on the device
```

`firmware update` writes the medium opposite the one it booted from. See [boot-model.md](boot-model.md).
