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

## Changing a BSP patch

**Never hand-edit a `.patch` file.** Hand edits leave stale metadata behind, such as wrong index hashes, and cause surprises at apply time. Instead:

1. Clone the upstream repo and check out the pinned `NXP_LF_SRCREV_*`.
2. `git am` the layer's full series for that component.
3. Amend or add commits in that tree.
4. Regenerate the series. For U-Boot: `git format-patch --zero-commit -N <base>..`.
5. Copy each regenerated patch over the layer file **keeping the existing file name**, since `SRC_URI` refers to it.
6. Diff the other regenerated patches against the layer copies to confirm nothing else changed.

Every patch needs an `Upstream-Status:` line before `Signed-off-by:`, or `do_patch` fails QA. Put it in the commit message in your tree, so regenerating keeps it.

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
