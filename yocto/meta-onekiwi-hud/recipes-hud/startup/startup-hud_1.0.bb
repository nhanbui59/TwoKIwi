SUMMARY = "HUD systemd autostart service"
LICENSE = "MIT"

SRC_URI = "file://hud-app.service"

inherit systemd

SYSTEMD_SERVICE:${PN} = "hud-app.service"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/hud-app.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${systemd_system_unitdir}/hud-app.service"
