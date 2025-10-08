# We need to add this, otherwise bitbake will only look in the original layer
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://defconfig"
SRC_URI += "file://mono-gateway-dk.dts"
SRC_URI += "file://mono-gateway-dk-sdk.dts"
SRC_URI += "file://mono-gateway-dk-usdpaa-xg-only.dts"

# We override the DELTA_KERNEL_DEFCONFIG loop completely
do_configure () {
    cp ${UNPACKDIR}/mono-gateway-dk.dts ${S}/arch/arm64/boot/dts/freescale/
    cp ${UNPACKDIR}/mono-gateway-dk-sdk.dts ${S}/arch/arm64/boot/dts/freescale/
    cp ${UNPACKDIR}/mono-gateway-dk-usdpaa-xg-only.dts ${S}/arch/arm64/boot/dts/freescale/
    cp ${UNPACKDIR}/defconfig ${B}/.config
    
    oe_runmake -C ${S} O=${B} olddefconfig
}

deltask do_merge_delta_config
