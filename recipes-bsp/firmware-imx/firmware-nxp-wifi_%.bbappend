# Remove the problematic packages that are causing duplicates
PACKAGES:remove = "${PN}-nxpiw610-usb ${PN}-nxpaw693-pcie"

# Remove them from meta-package dependencies
RDEPENDS:${PN}-all-usb:remove = "${PN}-nxpiw610-usb"
RDEPENDS:${PN}-all-pcie:remove = "${PN}-nxpaw693-pcie"

# Add the orphaned files to the main package so they don't cause "installed-vs-shipped" errors
FILES:${PN} += " \
    ${nonarch_base_libdir}/firmware/nxp/usb*_iw610.bin.se \
    ${nonarch_base_libdir}/firmware/nxp/pcie*aw693* \
    ${nonarch_base_libdir}/firmware/nxp/uart*aw693* \
"