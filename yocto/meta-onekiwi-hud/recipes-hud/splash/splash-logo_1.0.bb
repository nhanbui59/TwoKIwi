SUMMARY = "OneKiWi boot splash logo"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://splash.c file://logo_data.h file://splash-init"

S = "${WORKDIR}"

inherit update-rc.d

INITSCRIPT_NAME = "splash"
INITSCRIPT_PARAMS = "start 01 S ."

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o splash-logo splash.c
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 splash-logo ${D}${bindir}/splash-logo

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/splash-init ${D}${sysconfdir}/init.d/splash
}

FILES_${PN} = "${bindir}/splash-logo ${sysconfdir}/init.d/splash"
RDEPENDS_${PN} = "fbset"
