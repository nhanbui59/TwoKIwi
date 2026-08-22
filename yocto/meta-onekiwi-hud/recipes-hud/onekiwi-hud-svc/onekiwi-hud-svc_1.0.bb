SUMMARY = "HUD RZ/V2L — binary dung san + cau hinh + tu khoi dong"
DESCRIPTION = "Cai hud_app (bien dich cheo tu repo rzv2l-hud), tep cau hinh \
/etc/default/hud va init script, roi bat tu khoi dong. Muc dich: nap the xong \
la HUD chay ngay, khong phai cau hinh tay lan nao."
LICENSE = "CLOSED"

SRC_URI = "file://hud_app file://hud-default file://hud-init"
S = "${WORKDIR}"

# Binary da bien dich cheo san cho aarch64 — dung de Yocto tu dong lam gi voi no.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INHIBIT_SYSROOT_STRIP = "1"
# Binary lien ket voi thu vien he thong nhung khong khai DEPENDS o day: no da
# duoc kiem "glibc doi hoi <= cua bo" boi tools/build_board.sh truoc khi chep sang.
INSANE_SKIP_${PN} += "already-stripped ldflags file-rdeps arch"
INSANE_SKIP:${PN} += "already-stripped ldflags file-rdeps arch"

RDEPENDS_${PN} = "can-utils iproute2"
RDEPENDS:${PN} = "can-utils iproute2"

inherit update-rc.d

INITSCRIPT_NAME = "hud"
# 99 = chay SAU khi mang va cac dich vu khac da len. Doi /dev/fb0 la viec cua
# chinh init script (no co vong doi toi 10 giay), khong phai cua thu tu khoi dong.
INITSCRIPT_PARAMS = "start 99 2 3 4 5 . stop 01 0 1 6 ."

do_install() {
    # Binary o /home/root cho khop voi duong dan trong init script va voi thoi
    # quen nap tay bang scp khi phat trien.
    install -d ${D}/home/root
    install -m 0755 ${WORKDIR}/hud_app ${D}/home/root/hud_app

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${WORKDIR}/hud-default ${D}${sysconfdir}/default/hud

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/hud-init ${D}${sysconfdir}/init.d/hud
}

# Ha demo lvgl-v9 xuong: no giu /dev/fb0 va chay TRUOC HUD (S98 < S99), nen
# khong ha thi HUD mo framebuffer khong duoc. Doi S98 -> K98 trong moi runlevel.
pkg_postinst_ontarget_${PN}() {
    for d in 2 3 4 5; do
        if [ -e "$D/etc/rc$d.d/S98lvgl-v9" ]; then
            mv "$D/etc/rc$d.d/S98lvgl-v9" "$D/etc/rc$d.d/K98lvgl-v9"
        fi
    done
    exit 0
}
pkg_postinst_ontarget:${PN}() {
    for d in 2 3 4 5; do
        if [ -e "$D/etc/rc$d.d/S98lvgl-v9" ]; then
            mv "$D/etc/rc$d.d/S98lvgl-v9" "$D/etc/rc$d.d/K98lvgl-v9"
        fi
    done
    exit 0
}

FILES_${PN} = "/home/root/hud_app ${sysconfdir}/default/hud ${sysconfdir}/init.d/hud"
FILES:${PN} = "/home/root/hud_app ${sysconfdir}/default/hud ${sysconfdir}/init.d/hud"
