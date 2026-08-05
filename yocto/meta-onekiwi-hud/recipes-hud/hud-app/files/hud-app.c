/* HUD app stub: LVGL + DRM + EGL/GLES (Mali-G31 Panfrost).
   Build ra image chạy được, sau mày thay app thật vào. */
#include "lvgl.h"
#include "lv_drivers/linux/lv_linux_drm.h"

int main(void)
{
    lv_init();

    lv_display_t *disp = lv_linux_drm_create();
    lv_linux_drm_set_file(disp, "/dev/dri/card0", -1);

    /* Demo widget để verify GPU render */
    lv_obj_t *label = lv_label_create(lv_screen_active());
    lv_label_set_text(label, "OneKiWi HUD - GPU Panfrost OK");
    lv_obj_center(label);

    while (1) {
        uint32_t ms = lv_timer_handler();
        usleep(ms * 1000);
    }
    return 0;
}
