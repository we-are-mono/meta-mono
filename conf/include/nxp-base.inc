# NXP Linux Factory release that the BSP recipes track. Bump every
# SRCREV here when moving to a newer LF release; the recipes under
# recipes-bsp/ and recipes-kernel/ all pin to the variables below
# rather than carrying their own SHAs.
#
# Each SRCREV is the commit that the lf-* tag points to in the
# corresponding nxp-qoriq repo. Look them up with:
#   git ls-remote https://github.com/nxp-qoriq/<repo>.git "refs/tags/lf-*"
#
# The patch series under each recipes-*/files/ directory is intended to
# apply across multiple LF releases — git's fuzzy matching tolerates
# upstream context drift on additive patches. If a patch ever fails to
# apply after a bump, fix that single patch in place, not the whole set.

NXP_LF_TAG = "lf-6.18.20-2.0.0"

NXP_LF_SRCREV_ATF   = "0779f89a5475a03193f7707f3bbb50cec11707c0"
NXP_LF_SRCREV_RCW   = "82fa0c6d03b00dc84b6a64ba91f42c35762dd903"
NXP_LF_SRCREV_UBOOT = "6eeef838dac4ddbc06ff14450531a95e8c5cb346"
NXP_LF_SRCREV_LINUX = "b096ce610e956cc2596006343df8a2a26ed6e019"

# Kernel version baked into PV. Bump in lockstep with NXP_LF_SRCREV_LINUX
# so the package metadata reflects the actual kernel.
NXP_LF_LINUX_VERSION = "6.18.20"
