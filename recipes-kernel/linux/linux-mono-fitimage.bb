SUMMARY = "Recovery kernel and device tree as a FIT image"
SECTION = "kernel"

# The FIT carries the kernel, which bundles the recovery initramfs, so the
# initramfs licenses apply on top of the kernel's own.
LICENSE = "GPL-2.0-with-Linux-syscall-note"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/files/common-licenses/GPL-2.0-with-Linux-syscall-note;md5=0bad96c422c41c3a94009dcfe1bff992"

inherit linux-kernel-base kernel-fit-image

COMPATIBLE_MACHINE = "gateway-dk"

# Version this recipe after the kernel it packages.
PKGV = "${@get_kernelversion_file("${STAGING_KERNEL_BUILDDIR}")}"
