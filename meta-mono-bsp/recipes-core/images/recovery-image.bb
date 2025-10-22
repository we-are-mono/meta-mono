DESCRIPTION = "Minimal BusyBox initramfs for Gateway Development Kit"
LICENSE = "MIT"

# Inherit initramfs image class (leaner) instead of core-image
inherit image

# Keep it minimal - just BusyBox and essential packages that should be
# sufficient for a rescue system; Basic networking, partitioning and compression.
IMAGE_INSTALL = "busybox base-files shadow kmod udev udev-rules-mono \
                parted util-linux-fdisk util-linux-lsblk util-linux-blkid \
                e2fsprogs mmc-utils mtd-utils i2c-tools \                 
                curl gzip tar \
                sfp-led-daemon status-led-daemon kernel-module-leds-lp5812 \
                "

# We don't want any root password for the rescue system
EXTRA_USERS_PARAMS = "usermod -p '' root;"

# This line ensures no root password is needed for login
IMAGE_FEATURES += "empty-root-password"

# Strip locale, saves us ~3MB
IMAGE_LINGUAS = ""

# Remove package management and other bloat
IMAGE_FEATURES:remove = "package-management"

# We want compressed version of initramfs
IMAGE_FSTYPES = "cpio.gz"

# Optional, but if we don't set it, it has machine in the name by default
IMAGE_NAME = "${IMAGE_BASENAME}${IMAGE_NAME_SUFFIX}"

# This is an initrafms image, bundled into the kernel.
# By including the kernel module for lp5812 above, bitbake
# will try to pull the same kernel into /boot. Kernelception!
PACKAGE_EXCLUDE += "kernel-${KERNEL_VERSION}"
PACKAGE_EXCLUDE += "kernel-image-${KERNEL_VERSION}"
PACKAGE_EXCLUDE += "kernel-image-image*"