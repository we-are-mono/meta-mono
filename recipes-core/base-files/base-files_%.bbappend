FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " file://issue \
                   file://profile.d/10-recovery-prompt.sh \
                   file://vimrc \
                   "

do_install:append() {
    # Custom login banner
    install -m 0644 ${UNPACKDIR}/issue ${D}${sysconfdir}/issue
    install -m 0644 ${UNPACKDIR}/issue ${D}${sysconfdir}/issue.net

    # Colored recovery shell prompt
    install -d ${D}${sysconfdir}/profile.d
    install -m 0644 ${UNPACKDIR}/profile.d/10-recovery-prompt.sh ${D}${sysconfdir}/profile.d/

    # Minimal vimrc so vim-tiny (no runtime files) doesn't fail to source
    # defaults.vim (E1187) on startup
    install -d ${D}${ROOT_HOME}
    install -m 0644 ${UNPACKDIR}/vimrc ${D}${ROOT_HOME}/.vimrc
}

hostname:pn-base-files = "recovery"
