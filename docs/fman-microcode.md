# FMan Microcode

The LS1046A's Frame Manager runs a microcode blob that U-Boot loads at boot. It lives in its own flash region at `0x400000`. U-Boot loads it for **every** OS that boots afterwards, not just recovery.

## Two sources

| Build | Microcode | Where it comes from |
|---|---|---|
| Public (this repo as cloned) | NXP stock, `108.4.9` | `recipes-dpaa/fm-ucode/fm-ucode_git.bb` fetches `github.com/NXP/qoriq-fm-ucode` at a pinned SRCREV |
| Official Mono releases | Licensed microcode with hardware offload | A local, gitignored `fm-ucode_git.bbappend` |

The licensed microcode is commercial and can't be distributed in source form, only inside built images. The public build path works end to end without it, and you lose only the offload features it enables. That is why official image downloads are behind a per-device login: the `firmware` tool sends the device's MAC as the password, which is a licence-compliance barrier, not a security control.

## How the override works

The bbappend replaces `do_deploy` and installs a different file under the **same deployed name** (`${FMAN_UCODE}`). The file name in the deploy directory therefore does not tell you which blob you have.

## Checking which microcode an image has

Read the U-Boot boot log:

```
Fman1: Uploading microcode version 108.4.9
```

`108.4.9` is the stock NXP blob. If you maintain an override, check that this line shows your version before you publish a build.

## Pitfall: git worktrees and fresh clones

`git worktree add` and `git clone` don't copy ignored files. A build from a worktree therefore loses the override **silently**: the image is signed, looks correct and passes traffic, but carries the stock microcode. If you keep a local override, build from the checkout that holds it, or copy the bbappend (and its blob) into the worktree first. Then check the boot banner.
