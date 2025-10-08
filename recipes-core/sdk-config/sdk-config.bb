SUMMARY = "SDK system configuration files"
DESCRIPTION = "Configuration files for the SDK Linux distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit systemd

SRC_URI = "file://oe-local.repo \
           file://dnf.conf"

S = "${WORKDIR}/sources"

do_install() {
    # Install DNF configuration
    install -d ${D}${sysconfdir}/dnf
    install -m 0644 ${WORKDIR}/dnf.conf ${D}${sysconfdir}/dnf/dnf.conf

    install -d ${D}${sysconfdir}/yum.repos.d
    install -m 0644 ${WORKDIR}/oe-local.repo ${D}${sysconfdir}/yum.repos.d/oe-local.repo
}

FILES:${PN} = "${sysconfdir}/dnf/dnf.conf \
               ${sysconfdir}/yum.repos.d/oe-local.repo"
