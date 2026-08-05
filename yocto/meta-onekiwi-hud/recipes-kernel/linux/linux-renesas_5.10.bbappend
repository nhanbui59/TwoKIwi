FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://onekiwi-gpu-dsi.cfg"
SRC_URI += "file://0001-drm-panel-st7703-add-newhaven-nhd35-640480ef.patch"

SRC_URI += "file://onekiwi-rzv2l.dtb"

do_deploy_append() {
    install -D -m 0644 ${WORKDIR}/onekiwi-rzv2l.dtb \
        ${DEPLOYDIR}/onekiwi-rzv2l.dtb
    rm -f ${DEPLOYDIR}/renesas/onekiwi-rzv2l.dtb
    install -d ${DEPLOYDIR}/renesas
    install -m 0644 ${WORKDIR}/onekiwi-rzv2l.dtb \
        ${DEPLOYDIR}/renesas/onekiwi-rzv2l.dtb
}
