DESCRIPTION = "Minimal BusyBox initramfs for Gateway Development Kit"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

# Plain image, not core-image: core-image pulls in packagegroup-base and
# a set of IMAGE_FEATURES this rescue system has no use for.
inherit image

# Keep it minimal - just BusyBox and essential packages that should be
# sufficient for a rescue system; Basic networking, partitioning and compression.
IMAGE_INSTALL = "busybox base-files resolv-conf-static bash shadow kmod \
                parted util-linux-fdisk util-linux-lsblk util-linux-blkid \
                e2fsprogs e2fsprogs-resize2fs mmc-utils mtd-utils i2c-tools \
                ethtool curl gzip xz tar vim-tiny firmware-tools \
                lmsensors-sensors sfp-led status-led lp5812-driver \
                tcpdump iproute2 dosfstools stressapptest \
                pciutils usbutils dropbear \
                "

# usbutils recommends udev-hwdb, which resolves to eudev-hwdb and brings back
# the daemon plus a 9MB hwdb.bin; dropbear recommends xauth. lsusb falls back
# to numeric IDs, and there is no X11 here. Name the packages, not the provide.
BAD_RECOMMENDATIONS += "eudev-hwdb xauth"

# Empty root password is intentional: recovery is only reachable via
# `run recovery` from the u-boot console, which itself requires UART
# (physical) access. A password wouldn't add any security beyond what
# physical access to the device already implies. That holds only while
# nothing listens on the network, which is why dropbear is installed but
# not started -- set a root password before starting it by hand.
IMAGE_FEATURES += "empty-root-password"

# Strip locale, saves us ~3MB
IMAGE_LINGUAS = ""

# Remove package management and other bloat
IMAGE_FEATURES:remove = "package-management"

# We want compressed version of initramfs
IMAGE_FSTYPES = "cpio.gz"

# Create static device nodes (console, null, etc.) so kernel can open
# /dev/console before userspace mounts devtmpfs
USE_DEVFS = "0"

# Optional, but if we don't set it, it has machine in the name by default
IMAGE_NAME = "${IMAGE_BASENAME}${IMAGE_NAME_SUFFIX}"

# This is an initramfs image, bundled into the kernel.
# By including the kernel module for lp5812 above, bitbake
# will try to pull the same kernel into /boot. Kernelception!
PACKAGE_EXCLUDE += "kernel-${KERNEL_VERSION}"
PACKAGE_EXCLUDE += "kernel-image-${KERNEL_VERSION}"
PACKAGE_EXCLUDE += "kernel-image-image*"

# We have bash in this image, might as well use it
ROOTFS_POSTPROCESS_COMMAND += "fix_root_shell;"

# dropbear must never listen by default: root has no password here. Nothing
# links its init script today (no sysvinit in DISTRO_FEATURES, so update-rc.d
# is absent), and rcS runs rc5.d -- this guards against that changing.
ROOTFS_POSTPROCESS_COMMAND += "disable_dropbear_autostart;"

fix_root_shell() {
    sed -i '/^root:/s|sh$|bash|' ${IMAGE_ROOTFS}/etc/passwd
}

disable_dropbear_autostart() {
    rm -f ${IMAGE_ROOTFS}${sysconfdir}/rc5.d/S??dropbear
}
