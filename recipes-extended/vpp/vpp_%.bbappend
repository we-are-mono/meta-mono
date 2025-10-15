FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://sfp_led_plugin.c \
    file://CMakeLists.txt \
    file://startup.conf \
"

do_configure:prepend() {
    mkdir -p ${S}/src/plugins/sfp_led
    
    cp ${UNPACKDIR}/sfp_led_plugin.c ${S}/src/plugins/sfp_led/
    cp ${UNPACKDIR}/CMakeLists.txt ${S}/src/plugins/sfp_led/CMakeLists.txt
}

do_install:append() {
    install -m 0644 ${UNPACKDIR}/startup.conf ${D}${sysconfdir}/vpp/startup.conf
}