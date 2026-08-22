SUMMARY = "OneKiWi static link-local IP for eth0 (169.254.10.2/16)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://onekiwi-net-init"

S = "${WORKDIR}"

inherit update-rc.d

INITSCRIPT_NAME = "onekiwi-net"
INITSCRIPT_PARAMS = "defaults 15"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/onekiwi-net-init ${D}${sysconfdir}/init.d/onekiwi-net
}

FILES_${PN} = "${sysconfdir}/init.d/onekiwi-net"

RDEPENDS_${PN} = "iproute2"
