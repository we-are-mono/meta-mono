SUMMARY = "Firmware update and management utility for Mono Gateway"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

RDEPENDS:${PN} = "bash curl mtd-utils mmc-utils"

SRC_URI = "file://firmware \
           file://firmware-signing.pub \
          "

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${UNPACKDIR}/firmware ${D}${sbindir}/firmware
    sed -i 's/@MACHINE@/${MACHINE}/' ${D}${sbindir}/firmware

    install -d ${D}${sysconfdir}/firmware
    install -m 0644 ${UNPACKDIR}/firmware-signing.pub ${D}${sysconfdir}/firmware/firmware-signing.pub
    echo "${FIRMWARE_VERSION}" > ${D}${sysconfdir}/firmware/version
}

FILES:${PN} = "${sbindir}/firmware ${sysconfdir}/firmware/"
