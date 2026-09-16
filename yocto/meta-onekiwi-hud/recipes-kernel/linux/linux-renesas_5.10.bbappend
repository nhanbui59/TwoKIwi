FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://onekiwi-gpu-dsi.cfg"
SRC_URI += "file://onekiwi-bt-wifi.cfg"
SRC_URI += "file://0001-drm-panel-st7703-add-newhaven-nhd35-640480ef.patch"

# Kernel boot log mode:
#   ONEKIWI_DEBUG_LOG = "0" (default) -> quiet release cmdline
#   ONEKIWI_DEBUG_LOG = "1"            -> full log on serial UART (dev mode)
# Screen NEVER shows kernel text in either mode (fbcon=disable in both).
python () {
    if d.getVar("ONEKIWI_DEBUG_LOG") == "1":
        d.appendVar("SRC_URI", " file://onekiwi-cmdline-debug.cfg")
    else:
        d.appendVar("SRC_URI", " file://onekiwi-cmdline-quiet.cfg")
}
SRC_URI += "file://0002-can-rcar_canfd-fix-controller-mode-for-rzg2l.patch"
SRC_URI += "file://0003-can-rcar_canfd-add-listen-only-and-berr-reporting.patch"

SRC_URI += "file://onekiwi-rzv2l.dtb"

# Custom OneKiWi boot logo (kernel CLUT224 format, 640x480, <=224 colours).
SRC_URI += "file://logo_linux_clut224.ppm"

# Replace the stock Tux logo with the OneKiWi logo. The kernel's
# drivers/video/logo/Makefile converts logo_linux_clut224.ppm -> .c via the
# scripts/pnmtologo host tool when CONFIG_LOGO_LINUX_CLUT224=y (set in
# onekiwi-gpu-dsi.cfg). This must happen before do_compile so the logo is in
# the source tree when the logo objects are built.
do_configure_prepend() {
    install -m 0644 ${WORKDIR}/logo_linux_clut224.ppm \
        ${S}/drivers/video/logo/logo_linux_clut224.ppm
}

do_deploy_append() {
    install -D -m 0644 ${WORKDIR}/onekiwi-rzv2l.dtb \
        ${DEPLOYDIR}/onekiwi-rzv2l.dtb
    rm -f ${DEPLOYDIR}/renesas/onekiwi-rzv2l.dtb
    install -d ${DEPLOYDIR}/renesas
    install -m 0644 ${WORKDIR}/onekiwi-rzv2l.dtb \
        ${DEPLOYDIR}/renesas/onekiwi-rzv2l.dtb
}
