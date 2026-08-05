# meta-onekiwi-hud

Yocto layer cho OneKiWi RZ/V2L HUD.

## Build

```bash
cd yocto
source poky/oe-init-build-env build

# Build image
bitbake -c cleansstate virtual/kernel
bitbake onekiwi-hud-image

# Output: tmp/deploy/images/onekiwi-rzv2l/onekiwi-hud-image-onekiwi-rzv2l.wic
```

## Flash SD

```bash
sudo dd if=tmp/deploy/images/onekiwi-rzv2l/onekiwi-hud-image-onekiwi-rzv2l.wic \
       of=/dev/sdX bs=4M status=progress conv=fsync
```

## Features đã hoạt động

| Feature | Status |
|---------|--------|
| LCD NHD-3.5-640480EF (ST7703, 640x480) | OK |
| CAN FD (can0/can1, 500kbps + 2Mbps) | OK |
| Weston compositor | OK |
| Python3 + pip + pillow | OK |
| SSH (openssh) | OK |
| Panfrost (Mali-G31 GPU) | OK |

## Cấu trúc layer

```
meta-onekiwi-hud/
├── conf/layer.conf
├── recipes-core/images/
│   └── onekiwi-hud-image.bb          # Image recipe chính
├── recipes-kernel/linux/
│   ├── linux-renesas_5.10.bbappend    # Kernel config + patch + DTB
│   └── linux-renesas/
│       ├── 0001-drm-panel-st7703-add-newhaven-nhd35-640480ef.patch
│       ├── onekiwi-gpu-dsi.cfg
│       └── onekiwi-rzv2l.dtb
├── recipes-hud/
│   ├── lvgldemo/                      # LVGL demo systemd service
│   └── hud-app/                       # HUD app skeleton
├── wic/onekiwi-rzv2l.wks             # SD partition layout
└── recipes-devtools/                  # pseudo + opkg-utils fixes
```

## Environment

- Yocto: Dunfell 3.1.33
- Kernel: 5.10.229-cip54 (renesas-rz)
- Board: OneKiWi RZ/V2L (R9A07G054L2, Mali-G31, 2GB DDR4)
- Panel: Newhaven NHD-3.5-640480EF (Sitronix ST7703, 640x480, 2-lane DSI)
