DESCRIPTION = "udev rules for Gateway Development Kit"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://70-fsl-dpaa-persistent-networking.rules"

INHIBIT_DEFAULT_DEPS = "1"

RULE = "70-fsl-dpaa-persistent-networking.rules"

do_install () {
    install -d ${D}${sysconfdir}/udev/rules.d/

    for r in ${RULE};do
        install -m 0644 ${UNPACKDIR}/${r} ${D}${sysconfdir}/udev/rules.d/
    done
}