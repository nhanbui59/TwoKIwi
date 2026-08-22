# AGENTS.md — OneKiWi RZ/V2L HUD Build Guide

Mọi thay đổi build MUST follow config này. Đọc kỹ trước khi sửa bất cứ gì.

## Nghiệm thu image — những phép kiểm ĐÚNG vs VÔ DỤNG (bài học 22/08/2026)

**VÔ DỤNG (đừng dùng lại):**
- Ngày build trong `/proc/version` (`#1 SMP PREEMPT ... Feb 27 2021`) — Yocto reproducible-builds ghim SOURCE_DATE_EPOCH/KERNEL_BUILD_TIMESTAMP, kernel cũ và mới ghi GIỐNG HỆT nhau
- Timestamp file trong rootfs — cũng bị ghim (thấy `Mar 9 2018` là bình thường)

**ĐÚNG (dùng các phép này):**
1. `md5sum /boot/Image` trên bo == md5 của `tmp/deploy/images/onekiwi-rzv2l/Image` trên máy build
2. `strings Image | grep "Tx request in listen-only"` — string chỉ tồn tại ở kernel có patch 0003
3. `cat /proc/cmdline` — kernel của repo này có `CONFIG_CMDLINE_FORCE=y` nên cmdline **bắt buộc** chứa `fbcon=disable vt.global_cursor_default=0`. Thiếu 2 chuỗi đó = board đang chạy KERNEL KHÁC (vendor, eMMC/SPI) dù rootfs là của mình (dấu hiệu: timeout có mà listen-only vẫn `Operation not supported` = driver trong kernel đang chạy không có `BERR_REPORTING` — mọi bản driver từ repo này kể cả không patch đều có nó)
4. Mổ WIC khi nghi flash: tách partition (`fdisk -l`, P1 FAT @sector 2048, P2 ext4 @sector 1026048), rút kernel từ P1 (FAT16 parse / pyfatfs) và P2 (`debugfs -R "cat /boot/Image" p2.img`), so md5 với deploy

## HUD App (onekiwi-hud-svc — commit d1931857)

- Binary prebuilt aarch64 `/home/root/hud_app` (md5 `a5057d8d`), `/etc/default/hud` (HUD_ARGS + CAN_LISTEN_ONLY=yes + HUD_FLIP="--flip v"), init `S99hud`
- Init script tự: đợi `/dev/fb0` (10s), đưa can0 lên **listen-only** nếu đang down, **unbind vtcon** khỏi fb0 (tách console khỏi màn), tắt cursor blink
- `pkg_postinst_ontarget` hạ lvgl-v9 S98→K98 ở first boot (chống giành fb0)
- HUD_FLIP v = ảnh lat sẵn cho kính; hud_app tự dò tốc độ (--probe on) rồi tự rời listen-only
- Sửa config trên bo: `/etc/default/hud` rồi `/etc/init.d/hud restart` — KHÔNG sửa init script

## Build Environment

```bash
cd /home/nhanbv/Workspace/HUD/30.Img/TwoKIwi/yocto
source poky/oe-init-build-env build
bitbake onekiwi-hud-image
```

**QUAN TRỌNG:**
- Luôn source từ `yocto/`, KHÔNG source từ trong `build/` (sẽ tạo `build/build` lồng nhau → layers mất → "Nothing PROVIDES")
- Yocto **Dunfell 3.1** — override syntax dùng `_` (ví dụ `FILES_${PN}`), KHÔNG dùng `:` (Honister+)
- Distro dùng **sysvinit**, KHÔNG phải systemd. Init script dùng `inherit update-rc.d`, không dùng `inherit systemd`
- Build host: 20 cores → `BB_NUMBER_THREADS = "20"`, `PARALLEL_MAKE = "-j 20"` (đã set trong local.conf)

## Output & Flash

```
Image:  build/tmp/deploy/images/onekiwi-rzv2l/onekiwi-hud-image-onekiwi-rzv2l.wic
Flash:  sudo dd if=<image>.wic of=/dev/sdX bs=4M status=progress conv=fsync
```

## Hardware / Feature Config

| Feature | Trạng thái | Config |
|---|---|---|
| **CAN listen-only + berr-reporting** | OK (patch 0003) | `CCTR.CTME` bit 24 test-mode = listen-only (không ACK, không error flag, không thể bus-off — cơ chế Renesas FSP); BEIE gate theo cờ; TX guard drop |
| **CAN Classic + FD mixed** | OK | Patch `0002` — clear FDOE → mixed mode (cả 2 chạy song song). FD tĩnh trong DT (KHÔNG dùng `renesas,no-can-fd` — software tự điều khiển classic/FD theo lần cấu hình) |
| **USB host/gadget** | OK | `onekiwi-gpu-dsi.cfg` + DTS sẵn |
| **GPU Mali-G31 (Panfrost)** | OK | `CONFIG_DRM_PANFROST=y` + mesa bbappend `panfrost kmsro` |
| **LVGL v9.2.2 + ThorVG** | OK | `recipes-graphics/lvgl-v9/`, fbdev `/dev/fb0` (đã bị hạ K98 nhường fb0 cho hud_app) |
| **eth0 IP tĩnh** | OK | `onekiwi-net` init: `169.254.10.2/16`, host PC để `169.254.10.1/16` |
| **coreutils (timeout)** | OK | Trong IMAGE_INSTALL |
| **CAN clock** | 50 MHz | DTS: Classic 500kbps + FD data 2Mbps OK |

### CAN test sau boot
```bash
#listen-only (không cần cắm CAN cũng phải THÀNH CÔNG — chỉ cấu hình kernel)
ip link set can0 down
ip link set can0 type can bitrate 500000 dbitrate 2000000 fd on listen-only on berr-reporting on
ip link set can0 up
ip -d link show can0     # phải hiện listen-only berr-reporting
# Nếu "Operation not supported" → kernel đang chạy KHÔNG phải kernel từ image (xem phần nghiệm thu)
cansend can0 123#DEADBEEF        # bị chặn: dmesg "Tx request in listen-only mode"
```

## HUD Windshield Projection (V-Flip)

Board hắt hình lên kính ô tô → hình bị đảo ngược. Chiến lược:

1. **LVGL UI**: custom `vflip_flush()` trong `main.c` flip TOÀN BỘ màn hình lúc ghi ra fb (mọi widget tự động đúng). Logo icon trong LVGL **KHÔNG flip** (flush đã flip)
2. **Splash logo** (`logo_data.h`): data **pre-flipped** (rotate 180° + mirror ngang)
3. **Kernel boot logo** (`logo_linux_clut224.ppm`): **pre-flipped**, format **ASCII P3** (kernel pnmtologo KHÔNG nhận binary P6!)

Regen logo từ PNG gốc:
```python
# V-transform: img.rotate(180).transpose(Image.FLIP_LEFT_RIGHT) cho splash + ppm
# Icon LVGL: KHÔNG flip (vflip_flush lo)
```

## Boot Flow (Release mặc định)

```
Màn đen → splash logo cty (init S01) → LVGL HUD (status bar + clock + logo)
```

KHÔNG log, KHÔNG login trên màn hình. Serial UART (ttySC0 115200) + SSH vẫn login được.

### Kiểm soát boot log: `ONEKIWI_DEBUG_LOG` trong `build/conf/local.conf`
- `"0"` hoặc không set (mặc định/release): `quiet loglevel=3 fbcon=disable`
- `"1"` (dev): `loglevel=7` — full kernel log trên serial UART. Màn hình VẪN sạch (fbcon=disable luôn bật)

Toggle xong phải rebuild kernel:
```bash
bitbake -c cleansstate virtual/kernel && bitbake onekiwi-hud-image
```

### Cơ chế tắt log màn hình (3 lớp — ĐỪNG bỏ layer nào)
1. `CONFIG_CMDLINE_FORCE=y` + `quiet loglevel=3 fbcon=disable` trong `onekiwi-cmdline-quiet.cfg` (U-Boot env bootargs ĐÈ bootargs DTB — patch DTB vô dụng, phải force qua kernel config)
2. `remove_tty1_getty` trong image recipe — xóa getty tty1 (nó in "login:" lên màn)
3. fbcon=disable trong CẢ 2 mode

## Known Gotchas (đã ăn phải)

| Vấn đề | Nguyên nhân | Fix |
|---|---|---|
| `lvgldemo-service` package rỗng | Recipe dùng `:` syntax (Honister+) trên Dunfell | Dùng `_` syntax |
| Service không install | `inherit systemd` nhưng distro sysvinit → `rm_systemd_unitdir` xóa file | `inherit update-rc.d` + init.d script |
| `update-rc.d: not found` khi do_install | Thiếu inherit | `inherit update-rc.d` |
| LVGL "Possible failure to include lv_conf.h" | Template `#if 0` không flip thành `#if 1` | `sed -i 's/^#if 0 .../#if 1/'` |
| LVGL `LV_OS_LINUX undeclared` | v9.2.2 dùng `LV_OS_PTHREAD` | `#define LV_USE_OS LV_OS_PTHREAD` |
| `Binary PNM is not supported` | Kernel pnmtologo chỉ nhận ASCII | Viết P3 bằng Python/Pillow |
| Fetch fail `lvgl.git branch=master` | SRCREV tag v9.2.2 không nằm ở master | `branch=release/v9.2`, SRCREV `c98ab243621a2a948674da5339c15da88832f928` |
| `lv_display_set_draw_buffers` sai args | v9.2.2 API là `lv_display_set_buffers(disp, buf1, buf2, size, mode)` | Check `src/display/lv_display.h` |
| Logo bị text boot đè | fbcon console in log lên fb | `fbcon=disable` + `quiet` |
| "login:" hiện trên màn | getty tty1 | Xóa bằng ROOTFS_POSTPROCESS_COMMAND |
| **"please waiting boot img..."** | ĐÃ XÁC ĐỊNH: từ **vendor bootloader cũ trong SPI flash** (string KHÔNG tồn tại trong bất kỳ binary nào Yocto build — đã grep raw toàn bộ deploy/*.bin). Board boot U-Boot từ SPI, WIC chỉ ghi kernel+rootfs vào SD nên không đụng tới nó | **Fix: reflash SPI** bằng FIP/BL2 do Yocto build (thủ tục XLS2 bên dưới). Stock Renesas U-Boot không vẽ gì lên màn → boot flow sạch hoàn toàn |

## Build Optimization (đã set trong local.conf)

```bitbake
DL_DIR ?= "${TOPDIR}/../downloads"       # sống qua rm -rf build
SSTATE_DIR ?= "${TOPDIR}/../sstate-cache"
INHERIT += "rm_work"                      # dọn workdir sau build
BB_HASHSERVE = "auto"                     # hash equivalence
BB_NUMBER_THREADS ?= "20"
PARALLEL_MAKE ?= "-j 20"
```

**LƯU Ý:** `IMAGE_FSTYPES` giữ `wic` (KHÔNG dùng wic.bz2) vì `fix_wic_boot` patch raw bytes MBR sau khi build.

## Layer Structure

```
meta-onekiwi-hud/          ← Layer chính (mọi custom)
├── conf/layer.conf
├── recipes-core/images/onekiwi-hud-image.bb     ← image recipe + wic fix + tty1 remove
├── recipes-graphics/lvgl-v9/                    ← LVGL v9.2.2 + ThorVG + vflip
├── recipes-hud/splash/                          ← splash logo program
├── recipes-kernel/linux/
│   ├── linux-renesas_5.10.bbappend              ← patches + cfg fragments + logo
│   └── linux-renesas/
│       ├── onekiwi-gpu-dsi.cfg                  ← DRM/DSI/CAN/USB/Panfrost/LOGO
│       ├── onekiwi-cmdline-quiet.cfg            ← release cmdline
│       ├── onekiwi-cmdline-debug.cfg            ← dev cmdline
│       ├── 0001-drm-panel-st7703-*.patch
│       ├── 0002-can-rcar_canfd-*.patch          ← CAN mixed mode
│       ├── logo_linux_clut224.ppm               ← boot logo (P3, V-flipped)
│       └── onekiwi-rzv2l.dtb                    ← prebuilt DTB (deploy qua bbappend)
└── wic/onekiwi-rzv2l.wks
```

## UI Layout (640x480, LVGL v9)

```
┌──────────────────────────────────────────────┐
│ [logo 31x36]                       HH:MM:SS  │ ← status bar 44px #161B26
│                                  DD/MM/YYYY  │
├──────────────────────────────────────────────┤ ← teal accent 2px #00C0C0
│                                              │
│          (dark main area #0A0E14)            │
│                                              │
└──────────────────────────────────────────────┘
```

- Clock update mỗi giây qua `lv_timer_create()`
- Font: Montserrat 16 (enable trong lv_conf override: `LV_FONT_MONTSERRAT_16`)
- Canvas logo: copy row-by-row theo `header.stride` (tránh skew do padding)

## Khi Thêm Package Mới

1. Recipe dùng `_` syntax (Dunfell)
2. Init script: `inherit update-rc.d` + `INITSCRIPT_NAME` + `INITSCRIPT_PARAMS`
3. Không grab `/dev/fb0` trùng lvgl-v9 (conflict → treo)
4. Add vào `IMAGE_INSTALL` trong `onekiwi-hud-image.bb`
5. Rebuild: `bitbake <recipe> && bitbake onekiwi-hud-image`

## Reflash SPI Bootloader (xóa "please waiting boot img...")

Board = PMIC variant (`PMIC_SUPPORT=1`, DDR4 2GB). Files trong `build/tmp/deploy/images/onekiwi-rzv2l/`:
- `Flash_Writer_SCIF_RZV2L_SMARC_PMIC_DDR4_2GB_1PCS.mot` ← flash writer
- `bl2_bp-onekiwi-rzv2l_pmic.srec` ← BL2, Program Top `H'11E00`, QSPI Save `H'0`
- `fip-onekiwi-rzv2l_pmic.srec` ← FIP (ATF+U-Boot), Program Top `H'0`, QSPI Save `H'20000`

### Thủ tục
1. Gắn cable serial UART (SCIF, 115200 8N1), mở terminal (Tera Term/minicom)
2. Đặt boot switch sang **SCIF download mode** (xem silk trên board), reset
3. Boot ROM xin file → XMODEM send `Flash_Writer_..._PMIC_DDR4_2GB_1PCS.mot`
4. Flash writer chạy, hiện prompt `>`. Ghi BL2:
   ```
   > XLS2
   Program Top Address : 11E00
   Qspi Save Address   : 0
   → XMODEM send bl2_bp-onekiwi-rzv2l_pmic.srec
   ```
5. Ghi FIP:
   ```
   > XLS2
   Program Top Address : 0
   Qspi Save Address   : 20000
   → XMODEM send fip-onekiwi-rzv2l_pmic.srec
   ```
6. Tắt nguồn, boot switch về **QSPI boot mode**, rút serial (hoặc để debug), bật lên

### Sau reflash
- Stock Renesas U-Boot KHÔNG vẽ lên màn hình → boot flow: **màn đen → splash logo → HUD**, sạch hoàn toàn
- Serial (ttySC0) vẫn có U-Boot log để debug
- **Khôi phục nếu fail**: SCIF mode là mask-ROM (luôn available) — lặp lại thủ tục là được, không brick vĩnh viễn
- Lưu ý: U-Boot dùng `smarc-rzv2l_defconfig` (stock) — bootcmd load kernel từ SD ext4 `/boot/Image`, giống hệt vendor bootflow nên kernel/rootfs không đổi

### ĐÃ VERIFY (15/08/2026) — DEFERRED, fix sau
- Đã confirm 100% string không có trong WIC/firmware Yocto (grep raw = 0 match)
- Đã login serial qua CH340 (/dev/ttyUSB0 qua usbipd, busid 6-4, COM9), reboot board, bắt được banner: **U-Boot vendor bản 06/10/2025 trong SPI** là thủ phạm (custom OneKiWi vẽ chữ lên LCD)
- **Tool flash tự động đã viết sẵn**: `TwoKIwi/flash_spi.py` (XMODEM qua serial, tự gửi Flash Writer + BL2 + FIP). Cần: `pip3 install xmodem pyserial` (đã cài)
- Chạy khi fix: gạt SCIF mode + reset → `sg dialout -c "python3 ~/Workspace/HUD/30.Img/TwoKIwi/flash_spi.py"` → switch về QSPI → reboot
- CH340 hay detach khỏi WSL — re-attach: chạy `C:\Users\Public\usbip-attach.ps1` elevated (đã đặt sẵn), hoặc `usbipd attach --wsl --busid 6-4`
- User `nhanbv` đã ở group dialout
