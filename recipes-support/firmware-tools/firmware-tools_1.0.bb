SUMMARY = "Firmware update and management utility for Mono Gateway"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# libubootenv-bin supplies fw_printenv/fw_setenv, which are useless without
# the fw_env.config this recipe generates at boot, so they ship together.
RDEPENDS:${PN} = "bash curl mtd-utils mmc-utils openssl-bin libubootenv-bin"

# The package embeds MACHINE and FIRMWARE_VERSION (machine-conf variables),
# so it must not be shared across machines from tune-arch sstate.
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://firmware \
           file://firmware-signing.pub \
           file://S02fw-env \
          "

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${UNPACKDIR}/firmware ${D}${sbindir}/firmware
    sed -i 's/@MACHINE@/${MACHINE}/' ${D}${sbindir}/firmware

    install -d ${D}${sysconfdir}/firmware
    install -m 0644 ${UNPACKDIR}/firmware-signing.pub ${D}${sysconfdir}/firmware/firmware-signing.pub
    echo "${FIRMWARE_VERSION}" > ${D}${sysconfdir}/firmware/version

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${UNPACKDIR}/S02fw-env ${D}${sysconfdir}/init.d/
    install -d ${D}${sysconfdir}/rcS.d
    ln -sf ../init.d/S02fw-env ${D}${sysconfdir}/rcS.d/S02fw-env
}

FILES:${PN} = "${sbindir}/firmware ${sysconfdir}/firmware/ \
               ${sysconfdir}/init.d/S02fw-env ${sysconfdir}/rcS.d/S02fw-env"
