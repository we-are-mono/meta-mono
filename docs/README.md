# Documentation

Notes for contributors to meta-mono. Start with the [development workflow](development.md).

| Document | Covers |
|---|---|
| [development.md](development.md) | Building with kas, bumping the NXP LF base, changing patches (git or devtool), verifying images, conventions |
| [boot-model.md](boot-model.md) | DIP-selected boot chains, `bootcmd`, `firmware update`, environment, image variants |
| [recovery-image.md](recovery-image.md) | Recovery kernel scope, security model, BusyBox/bash rules, the 24 MiB FIT window |
| [hardware-notes.md](hardware-notes.md) | SFP+ cages and LEDs, fan, DDR4, USB-PD, CAAM: board facts that look like bugs but aren't |
| [fman-microcode.md](fman-microcode.md) | Public vs licensed FMan microcode, and how to tell which an image carries |
| [optee.md](optee.md) | OP-TEE integration, HUK and SecMon, RPMB, ASLR |
| [fuses.md](fuses.md) | SFP fuse registers, `TA_PROG_SFP`, the `otpmk` command |
| [backporting.md](backporting.md) | Worked example: backporting a kernel driver |
