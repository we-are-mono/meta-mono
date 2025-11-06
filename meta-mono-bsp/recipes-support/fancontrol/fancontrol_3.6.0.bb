SUMMARY = "Minimal fancontrol utility from lm-sensors"
HOMEPAGE = "https://github.com/lm-sensors/lm-sensors"
LICENSE = "GPL-2.0-or-later & LGPL-2.1-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=751419260aa954499f7abaabaa882bbe \
                    file://COPYING.LGPL;md5=4fbd65380cdd255951079008b364516c"

inherit systemd

DEPENDS = "bison-native flex-native"
RDEPENDS:${PN} = "bash"
INSANE_SKIP:${PN} += "ldflags useless-rpaths"

SRC_URI = "git://github.com/lm-sensors/lm-sensors.git;protocol=https;branch=master \
           file://S99fancontrol \
           file://fancontrol.conf"

SRCREV = "1667b850a1ce38151dae17156276f981be6fb557"

S = "${WORKDIR}/git"

do_compile() {
    # Build userspace components (library + programs)
    oe_runmake user \
               BUILD_STATIC_LIB=0 \
               CC="${CC}" \
               AR="${AR}" \
               CFLAGS="${CFLAGS}" \
               CPPFLAGS="${CPPFLAGS}" \
               LDFLAGS="${LDFLAGS}" \
               PREFIX="${prefix}" \
               ETCDIR="${sysconfdir}" \
               LIBDIR="${libdir}" \
               BINDIR="${bindir}" \
               SBINDIR="${sbindir}" \
               INCLUDEDIR="${includedir}" \
               MANDIR="${mandir}"
}

do_install() {
    install -d ${D}${libdir}
    install -d ${D}${bindir}
    install -d ${D}${sbindir}
    install -d ${D}${sysconfdir}

    # libsensors shared library
    install -m 0755 ${S}/lib/libsensors.so.* ${D}${libdir}/
    ln -sf libsensors.so.5.0.0 ${D}${libdir}/libsensors.so.5
    ln -sf libsensors.so.5 ${D}${libdir}/libsensors.so

    # binaries
    install -m 0755 ${S}/prog/sensors/sensors ${D}${bindir}/
    install -m 0755 ${S}/prog/pwm/fancontrol ${D}${sbindir}/    

    # configuration
    install -m 0644 ${UNPACKDIR}/fancontrol.conf ${D}${sysconfdir}/fancontrol

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${S}/prog/init/fancontrol.service ${D}${systemd_system_unitdir}/
    else
        install -d ${D}${sysconfdir}/init.d
        install -d ${D}${sysconfdir}/rcS.d
        install -m 0755 ${UNPACKDIR}/S99fancontrol ${D}${sysconfdir}/init.d/
        ln -sf ../init.d/S99fancontrol ${D}${sysconfdir}/rcS.d/S99fancontrol
    fi
}

FILES:${PN} = "${libdir}/libsensors.so.* \
               ${bindir}/sensors \
               ${sbindir}/fancontrol \
               ${sysconfdir}/fancontrol"

FILES:${PN}:append = "${@bb.utils.contains('DISTRO_FEATURES', 'systemd', \
    ' ${systemd_system_unitdir}/fancontrol.service', \
    ' ${sysconfdir}/init.d/S99fancontrol ${sysconfdir}/rcS.d/S99fancontrol', d)}"

# Enable systemd service automatically when applicable
SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "fancontrol.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"
