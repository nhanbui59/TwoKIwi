SUMMARY = "OneKiWi LVGL Demo auto-start service"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://lvgldemo-init"

S = "${WORKDIR}"

inherit update-rc.d

INITSCRIPT_NAME = "lvgldemo"
INITSCRIPT_PARAMS = "defaults 99"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/lvgldemo-init ${D}${sysconfdir}/init.d/lvgldemo
}

FILES_${PN} = "${sysconfdir}/init.d/lvgldemo"

RDEPENDS_${PN} = "lvgldemo"
