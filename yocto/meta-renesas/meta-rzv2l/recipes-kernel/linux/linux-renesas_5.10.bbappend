FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI_append = " \
	${@oe.utils.conditional("USE_ECC", "1", "file://0001-arm64-dts-renesas-r9a07g054l2-dev-smarc-update-memor.patch", "", d)} \
"

# OneKiWi custom device tree
SRC_URI_append_onekiwi-rzv2l = " file://onekiwi-rzv2l.dts"

do_configure_prepend_onekiwi-rzv2l() {
    mkdir -p ${S}/arch/arm64/boot/dts/renesas
    cp -f ${WORKDIR}/onekiwi-rzv2l.dts ${S}/arch/arm64/boot/dts/renesas/onekiwi-rzv2l.dts
    # add to Makefile if not already present
    grep -q 'renesas/onekiwi-rzv2l.dtb' ${S}/arch/arm64/boot/dts/renesas/Makefile || \
        echo 'dtb-$(CONFIG_ARCH_R9A07G054L) += renesas/onekiwi-rzv2l.dtb' \
        >> ${S}/arch/arm64/boot/dts/renesas/Makefile
}
