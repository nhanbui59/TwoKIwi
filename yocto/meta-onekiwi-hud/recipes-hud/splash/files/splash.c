/*
 * OneKiWi HUD boot splash — renders company logo to /dev/fb0.
 * Stays on screen until another app (LVGL) overwrites it.
 * If no app runs, the logo persists as idle screen.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/fb.h>
#include "logo_data.h"

int main(void)
{
    int fd = open("/dev/fb0", O_RDWR);
    if (fd < 0) { perror("open /dev/fb0"); return 1; }

    struct fb_var_screeninfo vinfo;
    struct fb_fix_screeninfo finfo;
    if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo)) { perror("vinfo"); close(fd); return 1; }
    if (ioctl(fd, FBIOGET_FSCREENINFO, &finfo)) { perror("finfo"); close(fd); return 1; }

    int bytespp = vinfo.bits_per_pixel / 8;
    long screensize = (long)finfo.line_length * vinfo.yres;
    unsigned char *fb = mmap(NULL, screensize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (fb == MAP_FAILED) { perror("mmap"); close(fd); return 1; }

    /* Clear to black */
    memset(fb, 0, screensize);

    /* Center logo */
    int ox = (vinfo.xres - LOGO_W) / 2;
    int oy = (vinfo.yres - LOGO_H) / 2;

    for (int y = 0; y < LOGO_H; y++) {
        int dy = oy + y;
        if (dy < 0 || dy >= (int)vinfo.yres) continue;
        for (int x = 0; x < LOGO_W; x++) {
            int dx = ox + x;
            if (dx < 0 || dx >= (int)vinfo.xres) continue;
            const unsigned char *src = &logo_data[(y * LOGO_W + x) * 4];
            unsigned char *dst = fb + (long)dy * finfo.line_length + dx * bytespp;

            if (bytespp == 4) {
                unsigned char b = src[0], g = src[1], r = src[2], a = src[3];
                if (a == 0) {
                    dst[0] = dst[1] = dst[2] = 0;
                    dst[3] = 0;
                } else if (a == 255) {
                    dst[0] = b; dst[1] = g; dst[2] = r; dst[3] = 255;
                } else {
                    dst[0] = (b * a) / 255;
                    dst[1] = (g * a) / 255;
                    dst[2] = (r * a) / 255;
                    dst[3] = a;
                }
            } else if (bytespp == 2) {
                unsigned char b = src[0], g = src[1], r = src[2], a = src[3];
                unsigned short rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
                if (a > 128)
                    *(unsigned short *)dst = rgb565;
            }
        }
    }

    munmap(fb, screensize);
    close(fd);
    return 0;
}
