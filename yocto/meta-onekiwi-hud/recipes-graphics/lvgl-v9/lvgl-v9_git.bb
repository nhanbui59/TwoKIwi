SUMMARY = "LVGL v9.2.2 fbdev demo for the OneKiWi HUD (RZ/V2L)"
DESCRIPTION = "Minimal LVGL v9 application that renders to /dev/fb0 using LVGL's \
built-in linux framebuffer driver (src/drivers/display/fb), with the bundled \
ThorVG vector-graphics engine enabled. Self-contained: it fetches LVGL v9.2.2, \
generates lv_conf.h from the version-matched template with OneKiWi overrides, \
and builds a tiny demo. It does NOT depend on the old v8 port \
(meta-rz-demo-lvgl) nor on SDL/libdrm."
HOMEPAGE = "https://github.com/lvgl/lvgl"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENCE.txt;md5=bf1198c89ae87f043108cea62460b03a"

# v9.2.2 release tag commit (from github.com/lvgl/lvgl tags).
SRCREV = "c98ab243621a2a948674da5339c15da88832f928"
PV = "9.2.2"

SRC_URI = "git://github.com/lvgl/lvgl.git;protocol=https;branch=release/v9.2 \
           file://main.c \
           file://logo_icon.h \
           file://CMakeLists.txt \
           file://lvgl-v9-init \
"

S = "${WORKDIR}/git"

# This image uses sysvinit (systemd is NOT in DISTRO_FEATURES), so autostart the
# demo via an /etc/init.d script + update-rc.d, exactly like the legacy v8
# lvgldemo-service recipe.
inherit cmake update-rc.d

INITSCRIPT_NAME = "lvgl-v9"
INITSCRIPT_PARAMS = "defaults 98"

# LVGL v9 vs v8 has substantial API changes (e.g. lv_scr_act -> lv_screen_active,
# lv_btn_create -> lv_button_create, new lv_display_t model, built-in fbdev
# driver). The old v8 demo in meta-rz-demo-lvgl does NOT compile against v9, so
# this recipe ships its own minimal main.c rather than trying to port the v8 demo.

do_configure_prepend() {
    # 1) Generate lv_conf.h from the template shipped with THIS exact LVGL
    #    version, then append OneKiWi overrides. Using #undef / #define is
    #    robust: it does not depend on the precise formatting or current values
    #    of the template lines, and survives template edits across patch releases.
    cp ${S}/lv_conf_template.h ${S}/lv_conf.h

    # The whole template body is wrapped in:
    #     #if 0 /*Set it to "1" to enable content*/
    #         ...all defaults + #define LV_CONF_H...
    #     #endif /*End of "Content enable"*/
    # Flip the guard to 1 so the defaults actually take effect and LV_CONF_H
    # is defined (otherwise lv_conf_internal.h prints its "Possible failure to
    # include lv_conf.h" pragma on every translation unit and all defaults are
    # dead, leaving only our appended overrides active).
    sed -i 's/^#if 0 \/\*Set it to "1" to enable content\*\//#if 1/' ${S}/lv_conf.h

    cat >> ${S}/lv_conf.h <<'EOF'

/* ===== OneKiWi HUD overrides (LVGL v9.2.2, fbdev target) ===== */

/* 32-bit colour (BGRA in the framebuffer) */
#undef LV_COLOR_DEPTH
#define LV_COLOR_DEPTH 32

/* Use the POSIX/Linux OS layer so LVGL runs its own tick thread; the main
 * thread only has to pump lv_timer_handler().
 * NOTE: in v9.2.2 the Linux/pthread OS layer is LV_OS_PTHREAD (== 1), NOT
 * LV_OS_LINUX (that name does not exist in this version). */
#undef LV_USE_OS
#define LV_USE_OS LV_OS_PTHREAD

/* Linux framebuffer display driver -> renders straight to /dev/fb0 */
#undef LV_USE_LINUX_FBDEV
#define LV_USE_LINUX_FBDEV 1

/* ===== ThorVG vector-graphics backend (LVGL's bundled ThorVG copy) ===== */
/* Enables the vector draw path and the internal ThorVG engine. LV_USE_THORVG
 * is derived automatically as (LV_USE_THORVG_INTERNAL || LV_USE_THORVG_EXTERNAL)
 * inside lv_conf_internal.h. The bundled ThorVG C++ sources live under
 * src/libs/thorvg and are built into the separate 'lvgl_thorvg' library by
 * env_support/cmake/custom.cmake. */
#undef LV_USE_VECTOR_GRAPHIC
#define LV_USE_VECTOR_GRAPHIC 1
#undef LV_USE_THORVG_INTERNAL
#define LV_USE_THORVG_INTERNAL 1

/* LV_USE_VECTOR_GRAPHIC requires LV_USE_MATRIX (transform matrix support),
 * otherwise lv_draw_vector.h hard-#errors out. */
#undef LV_USE_MATRIX
#define LV_USE_MATRIX 1

/* LV_USE_MATRIX in turn requires LV_USE_FLOAT (float-based math instead of
 * fixed-point) -- see the #error in lv_matrix.h. On aarch64/Cortex-A55 with
 * hard-float + NEON this is the preferred path anyway. */
#undef LV_USE_FLOAT
#define LV_USE_FLOAT 1

/* ThorVG needs a bigger draw-thread stack than the 8 KB default, otherwise
 * lv_conf_internal.h emits a #warning at compile time. */
#undef LV_DRAW_THREAD_STACK_SIZE
#define LV_DRAW_THREAD_STACK_SIZE (32 * 1024)

/* ===== OneKiWi HUD widgets =================================================
 * The status-bar logo is drawn on an lv_canvas (inherits from lv_image), so
 * both the image and canvas widgets must be enabled. The wall-clock uses the
 * Montserrat 16 font for legibility on the 640x480 panel. */
#undef LV_USE_IMAGE
#define LV_USE_IMAGE 1
#undef LV_USE_CANVAS
#define LV_USE_CANVAS 1
#undef LV_USE_LABEL
#define LV_USE_LABEL 1
#undef LV_FONT_MONTSERRAT_16
#define LV_FONT_MONTSERRAT_16 1
EOF

    # 2) Swap in our wrapper CMakeLists (builds the 'lvgl' library + our demo)
    #    and drop our main.c next to it. Keep a one-time backup of the original.
    if [ ! -f ${S}/CMakeLists.txt.lvgl.orig ]; then
        cp ${S}/CMakeLists.txt ${S}/CMakeLists.txt.lvgl.orig
    fi
    cp ${WORKDIR}/CMakeLists.txt ${S}/CMakeLists.txt
    cp ${WORKDIR}/main.c ${S}/main.c
    cp ${WORKDIR}/logo_icon.h ${S}/logo_icon.h
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/lvgl_v9_demo ${D}${bindir}/lvgl-v9-demo

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/lvgl-v9-init ${D}${sysconfdir}/init.d/lvgl-v9
}

FILES_${PN} = " \
    ${bindir}/lvgl-v9-demo \
    ${sysconfdir}/init.d/lvgl-v9 \
"

# Autostart v9 by default (via update-rc.d). The legacy v8 lvgldemo service is
# kept installed but disabled (see lvgldemo-service_1.0.bb) so the two demos
# never fight over the framebuffer/console at boot.
