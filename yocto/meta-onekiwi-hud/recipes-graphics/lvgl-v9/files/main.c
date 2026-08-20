/*
 * OneKiWi HUD - minimal automotive interface (LVGL v9.2.2 on /dev/fb0).
 *
 * The panel is projected onto the car windshield: the driver sees a mirrored
 * image, so the panel must be pre-flipped (vertical flip = rotate 180 + mirror
 * left/right). Instead of flipping each UI element, the ENTIRE frame is
 * flipped in the flush callback below — any widget added later (clock, logo,
 * gauges...) is automatically flipped too.
 *
 * Layout (640x480, 32-bit):
 *   +--------------------------------------------------------------+
 *   |  [logo]                                            HH:MM:SS  |  status bar
 *   |                                                  DD/MM/YYYY  |
 *   +--------------------------------------------------------------+
 *   |                    (clean dark main area)                    |
 *   +--------------------------------------------------------------+
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/fb.h>

#include "lvgl.h"
#include "logo_icon.h"

#ifndef LVGL_FBDEV
#define LVGL_FBDEV "/dev/fb0"
#endif

/* ---- Geometry --------------------------------------------------------- */
#define DISP_W          640
#define DISP_H          480
#define STATUSBAR_H     44          /* taskbar / status bar height (px)    */
#define ACCENT_H        2           /* accent stripe under the status bar  */
#define BAR_PAD         6           /* inner padding of the status bar     */

/* ---- Palette ---------------------------------------------------------- */
#define COL_BG          lv_color_hex(0x0A0E14)   /* main area background   */
#define COL_BAR         lv_color_hex(0x161B26)   /* status bar background  */
#define COL_ACCENT      lv_color_hex(0x00C0C0)   /* brand teal             */
#define COL_TEXT        lv_color_hex(0xFFFFFF)   /* primary text (clock)   */
#define COL_TEXT_DIM    lv_color_hex(0x9AA3B2)   /* secondary text (date)  */

#define CLOCK_FONT      (&lv_font_montserrat_16)

/* ---- Framebuffer ------------------------------------------------------ */
static struct {
    int fd;
    unsigned char *mem;
    unsigned long len;
    unsigned int stride;        /* bytes per fb row (line_length)     */
    unsigned int bpp;           /* bytes per pixel                    */
    unsigned int xres, yres;
} fb;

/* Full-screen LVGL draw buffer (DIRECT render mode). */
static uint8_t draw_buf[DISP_W * DISP_H * 4] __attribute__((aligned(64)));

/*
 * Vertical-flip flush: copy each dirty row to the mirrored fb row.
 * (yres-1-y, same x). All UI content is thereby pre-flipped for the
 * windshield projection in one single place.
 */
static void vflip_flush(lv_display_t * disp, const lv_area_t * area, uint8_t * px_map)
{
    int32_t aw = lv_area_get_width(area);
    int32_t ah = lv_area_get_height(area);

    for (int32_t i = 0; i < ah; i++) {
        int32_t src_y = area->y1 + i;
        int32_t dst_y = (int32_t)fb.yres - 1 - src_y;

        memcpy(fb.mem + (unsigned long)dst_y * fb.stride + (unsigned long)area->x1 * fb.bpp,
               px_map + ((unsigned long)src_y * DISP_W + (unsigned long)area->x1) * fb.bpp,
               (size_t)aw * fb.bpp);
    }
    lv_display_flush_ready(disp);
}

static int fb_init(void)
{
    struct fb_var_screeninfo vinfo;
    struct fb_fix_screeninfo finfo;

    fb.fd = open(LVGL_FBDEV, O_RDWR);
    if (fb.fd < 0) {
        perror("open " LVGL_FBDEV);
        return -1;
    }
    if (ioctl(fb.fd, FBIOGET_VSCREENINFO, &vinfo) ||
        ioctl(fb.fd, FBIOGET_FSCREENINFO, &finfo)) {
        perror("fb ioctl");
        close(fb.fd);
        return -1;
    }

    fb.bpp   = vinfo.bits_per_pixel / 8;
    fb.stride = finfo.line_length;
    fb.xres  = vinfo.xres;
    fb.yres  = vinfo.yres;
    fb.len   = (unsigned long)finfo.line_length * vinfo.yres;

    if (fb.bpp != 4) {
        fprintf(stderr, "lvgl-v9: unsupported fb bpp %u (need 32)\n",
                vinfo.bits_per_pixel);
        close(fb.fd);
        return -1;
    }

    fb.mem = mmap(NULL, fb.len, PROT_READ | PROT_WRITE, MAP_SHARED, fb.fd, 0);
    if (fb.mem == MAP_FAILED) {
        perror("mmap");
        close(fb.fd);
        return -1;
    }
    return 0;
}

/* ---- Logo ------------------------------------------------------------- */
#define LOGO_ROW_PITCH  (LOGO_ICON_W * 4 + 64)
static uint8_t logo_buf[LOGO_ICON_H * LOGO_ROW_PITCH]
    __attribute__((aligned(64)));

static void load_logo_into_canvas(lv_obj_t * canvas)
{
    lv_canvas_set_buffer(canvas, logo_buf, LOGO_ICON_W, LOGO_ICON_H,
                         LV_COLOR_FORMAT_ARGB8888);

    lv_draw_buf_t * dbuf = lv_canvas_get_draw_buf(canvas);
    if (dbuf == NULL) {
        fprintf(stderr, "lvgl-v9: logo canvas has no draw buffer\n");
        return;
    }

    const uint32_t src_pitch = (uint32_t)LOGO_ICON_W * 4u;
    const uint32_t dst_stride = dbuf->header.stride;
    uint8_t * dst = (uint8_t *)dbuf->data;

    for (int32_t y = 0; y < LOGO_ICON_H; y++) {
        memcpy(dst + (uint32_t)y * dst_stride,
               &logo_icon_data[(uint32_t)y * src_pitch],
               src_pitch);
    }
    lv_obj_invalidate(canvas);
}

/* ---- Clock ------------------------------------------------------------ */
static void clock_timer_cb(lv_timer_t * timer)
{
    lv_obj_t * label = (lv_obj_t *)lv_timer_get_user_data(timer);
    if (label == NULL) {
        return;
    }

    time_t now = time(NULL);
    if (now == (time_t)-1) {
        return;
    }

    struct tm tm_local;
    if (localtime_r(&now, &tm_local) == NULL) {
        return;
    }

    char buf[48];
    strftime(buf, sizeof(buf), "%H:%M:%S   %d/%m/%Y", &tm_local);
    lv_label_set_text(label, buf);
}

/* ---- UI construction -------------------------------------------------- */
static void build_hud(lv_obj_t * scr)
{
    lv_obj_set_style_bg_color(scr, COL_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_set_style_pad_all(scr, 0, 0);
    lv_obj_set_style_border_width(scr, 0, 0);
    lv_obj_set_scrollbar_mode(scr, LV_SCROLLBAR_MODE_OFF);

    /* --- Status bar (top, full width) -------------------------------- */
    lv_obj_t * bar = lv_obj_create(scr);
    lv_obj_remove_flag(bar, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_size(bar, DISP_W, STATUSBAR_H);
    lv_obj_set_pos(bar, 0, 0);
    lv_obj_set_style_bg_color(bar, COL_BAR, 0);
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(bar, 0, 0);
    lv_obj_set_style_border_width(bar, 0, 0);
    lv_obj_set_style_pad_all(bar, 0, 0);
    lv_obj_set_style_shadow_width(bar, 0, 0);

    lv_obj_t * logo = lv_canvas_create(bar);
    load_logo_into_canvas(logo);
    lv_obj_align(logo, LV_ALIGN_LEFT_MID, BAR_PAD, 0);

    lv_obj_t * clock_lbl = lv_label_create(bar);
    lv_obj_set_style_text_font(clock_lbl, CLOCK_FONT, 0);
    lv_obj_set_style_text_color(clock_lbl, COL_TEXT, 0);
    lv_obj_set_style_text_align(clock_lbl, LV_TEXT_ALIGN_RIGHT, 0);
    lv_label_set_text(clock_lbl, "--:--:--   --/--/----");
    lv_obj_align(clock_lbl, LV_ALIGN_RIGHT_MID, -BAR_PAD, 0);

    /* Seed immediately. */
    {
        time_t now = time(NULL);
        if (now != (time_t)-1) {
            struct tm tm_local;
            if (localtime_r(&now, &tm_local) != NULL) {
                char buf[48];
                strftime(buf, sizeof(buf), "%H:%M:%S   %d/%m/%Y", &tm_local);
                lv_label_set_text(clock_lbl, buf);
            }
        }
    }

    lv_timer_create(clock_timer_cb, 1000, clock_lbl);

    /* --- Accent stripe under the status bar -------------------------- */
    lv_obj_t * accent = lv_obj_create(scr);
    lv_obj_remove_flag(accent, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_size(accent, DISP_W, ACCENT_H);
    lv_obj_set_pos(accent, 0, STATUSBAR_H);
    lv_obj_set_style_bg_color(accent, COL_ACCENT, 0);
    lv_obj_set_style_bg_opa(accent, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(accent, 0, 0);
    lv_obj_set_style_border_width(accent, 0, 0);
    lv_obj_set_style_pad_all(accent, 0, 0);
}

int main(void)
{
    lv_init();

    if (fb_init() != 0) {
        return EXIT_FAILURE;
    }

    lv_display_t * disp = lv_display_create(DISP_W, DISP_H);
    lv_display_set_flush_cb(disp, vflip_flush);
    lv_display_set_buffers(disp, draw_buf, NULL, sizeof(draw_buf),
                           LV_DISPLAY_RENDER_MODE_DIRECT);

    build_hud(lv_screen_active());

    printf("lvgl-v9: OneKiWi HUD rendering to %s (vflip, display=%p)\n",
           LVGL_FBDEV, (void *)disp);
    fflush(stdout);

    for (;;) {
        uint32_t delay_ms = lv_timer_handler();
        usleep((delay_ms ? delay_ms : 5u) * 1000u);
    }
    return EXIT_SUCCESS;
}
