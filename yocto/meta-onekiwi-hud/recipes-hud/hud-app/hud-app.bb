SUMMARY = "OneKiWi HUD app — LVGL + DRM + EGL/GLES (Mali-G31 Panfrost)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=129500facfb8f5a4645c236d53401797"

SRC_URI = "file://hud-app.c file://CMakeLists.txt file://LICENSE"

S = "${WORKDIR}"

inherit cmake pkgconfig

DEPENDS = "lvgl libdrm virtual/egl virtual/libgles2"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/hud-app ${D}${bindir}/
}

FILES_${PN} = "${bindir}/hud-app"
