SUMMARY = "Mono SDK systemd preset file"
DESCRIPTION = "Systemd preset file to disable network services by default"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://99-mono-sdk.preset"

S = "${WORKDIR}/sources"
UNPACKDIR = "${S}"

inherit allarch

do_install() {
    install -d ${D}${systemd_unitdir}/system-preset
    install -m 0644 ${S}/99-mono-sdk.preset ${D}${systemd_unitdir}/system-preset/
}

FILES:${PN} = "${systemd_unitdir}/system-preset/99-mono-sdk.preset"
