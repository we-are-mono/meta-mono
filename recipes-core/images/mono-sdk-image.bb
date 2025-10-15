DESCRIPTION = "A minimal systemd-based root filesystem image for console/UART operation"
LICENSE = "MIT"

inherit core-image extrausers

# TODO: Set proper root password for production
EXTRA_USERS_PARAMS = "usermod -p '' root;"

# Enable package management for runtime updates
IMAGE_FEATURES = "package-management"

# Disable initramfs bundling (defined in machine.conf)
INITRAMFS_IMAGE_BUNDLE = "0"

# Essential system packages
CORE_IMAGE_BASE_INSTALL = "\
    base-files \
    base-passwd \
    coreutils \
    dbus \
    init-ifupdown \
    initscripts \
    kernel-base \
    kernel-modules \
    netbase \
    os-release \
    shadow \
    systemd \
    systemd-compat-units \
    util-linux \
    "

# Base system utilities
IMAGE_INSTALL:append = " \
    findutils \
    glibc-utils \
    grep \
    gzip \
    less \
    sed \
    tar \
    wget \
    which \
    "

# System monitoring and debugging tools
IMAGE_INSTALL:append = " \
    htop \
    procps \
    psmisc \
    stressapptest \
    systemd-analyze \
    "

# Filesystem and storage utilities
IMAGE_INSTALL:append = " \
    e2fsprogs \
    e2fsprogs-resize2fs \
    "

# Kernel and device management
IMAGE_INSTALL:append = " \
    kernel-devicetree \
    kmod \
    udev \
    udev-rules-qoriq \
    modprobe-config \
    mtd-utils \
    "

# System services
IMAGE_INSTALL:append = " \
    systemd-serialgetty \
    openssh \
    "

# Hardware monitoring and control
IMAGE_INSTALL:append = " \
    lmsensors \
    sfp-led-daemon \
    "

# NXP/Freescale specific packages
IMAGE_INSTALL:append = " \
    fmc \
    fmlib \
    packagegroup-fsl-networking-core \
    packagegroup-fsl-tools-extended \
    "

# Networking (Ethernet, WiFi, Bluetooth)
IMAGE_INSTALL:append = " \
    iperf3 \
    hostapd \
    wpa-supplicant \
    iw \
    bluez5 \
    kernel-module-nxp-wlan \
    firmware-nxp-wifi-nxp9098-pcie \
    firmware-nxp-wifi-nxpiw612-sdio \
    wireless-regdb-static \
    "

IMAGE_LINGUAS = ""

# Explicitly exclude unwanted packages
PACKAGE_EXCLUDE = "\
    alsa-utils \
    busybox \
    pulseaudio \
    "

# Generate ext4 filesystem for eMMC
IMAGE_FSTYPES = "ext4"
EXTRA_IMAGECMD:ext4 = "-F -i 4096 -J size=64"

SYSTEMD_DEFAULT_TARGET = "multi-user.target"


sdk_image_postprocess() {
    # Setup hugepages for DPDK
    mkdir -p ${IMAGE_ROOTFS}/mnt/hugepages
    echo "# Hugepages for DPDK" >> ${IMAGE_ROOTFS}${sysconfdir}/fstab
    echo "hugetlbfs /mnt/hugepages hugetlbfs defaults 0 0" >> ${IMAGE_ROOTFS}${sysconfdir}/fstab
}

ROOTFS_POSTPROCESS_COMMAND += "sdk_image_postprocess; "