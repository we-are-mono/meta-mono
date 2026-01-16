# Local override: use custom FMan microcode for ASK
# This file should be gitignored - it's machine-specific

do_deploy () {
    install -d ${DEPLOYDIR}/
    install -m 0644 ${TOPDIR}/../sources/fsl_fman_ucode_r2.bin ${DEPLOYDIR}/${FMAN_UCODE}
}
