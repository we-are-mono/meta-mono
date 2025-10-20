DESCRIPTION = "Fman microcode binary"
LICENSE = "NXP-Binary-EULA"
LIC_FILES_CHKSUM = "file://LICENSE;md5=12e248d404ce1ea8bed0148fcf127e69"
NO_GENERIC_LICENSE[NXP-Binary-EULA] = "LICENSE"

inherit deploy

PR = "r1"

SRC_URI = "git://github.com/NXP/qoriq-fm-ucode.git;nobranch=1;protocol=https"
SRCREV = "41d603a1ad78e0bb61365500828d9f484bf9bf10"

S = "${WORKDIR}/git"

do_deploy () {
    install -d ${DEPLOYDIR}/
    install -m 644 ${B}/${FMAN_UCODE} ${DEPLOYDIR}
}

addtask deploy before do_build