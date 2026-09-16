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

## Bluetooth + WiFi (thêm 25/08/2026)

### Trạng thái trước khi thêm — đo trên bo, không đoán

```
zcat /proc/config.gz | grep CONFIG_BT   ->  "# CONFIG_BT is not set"
lsusb  ->  Bus 003 Device 002: ID 0a12:0001 Cambridge Silicon Radio (HCI mode)
dmesg | grep -i bluetooth  ->  KHÔNG một dòng
```

Dongle được USB core liệt kê nhưng **không driver nào nhận** — thiếu hẳn ngăn xếp chứ
không phải thiếu module. `modprobe bluetooth` → `Module not found`. Không có `opkg`/`apt`
để cài BlueZ. Nên bắt buộc phải qua đường build lại image.

### Đã thêm gì

| Chỗ | Nội dung |
|---|---|
| `recipes-kernel/linux/linux-renesas/onekiwi-bt-wifi.cfg` | mảnh cấu hình nhân — BT + WiFi |
| `linux-renesas_5.10.bbappend` | `SRC_URI += "file://onekiwi-bt-wifi.cfg"` |
| `onekiwi-hud-image.bb` | `bluez5 bluez5-testtools wpa-supplicant iw` + 5 gói firmware WiFi |
| `build/conf/local.conf` | `DISTRO_FEATURES_append = " bluetooth wifi"` |

Dùng `=y` chứ không `=m` cho lớp lõi: bo không có initramfs nạp module sớm, và built-in
thì không bao giờ lệch vermagic khi ai đó build lại nhân.

**Ba mục crypto bắt buộc** (`CRYPTO_ECDH`, `CRYPTO_CMAC`, `CRYPTO_USER_API_HASH`) — thiếu
là `CONFIG_BT` bị **tắt âm thầm** lúc `oldconfig`, không một thông báo nào.

`CONFIG_BT_BREDR=y` là bắt buộc: mục tiêu là **SPP**, mà **BLE không có SPP**.

Dongle CSR `0a12:0001` là đời cũ, `btusb` nhận thẳng, **không cần firmware ngoài**. WiFi
thì **cần** — nên có `linux-firmware-*` trong IMAGE_INSTALL.

### Nghiệm thu sau khi flash

```bash
zcat /proc/config.gz | grep -E "^CONFIG_BT=|^CONFIG_BT_RFCOMM=|^CONFIG_BT_HCIBTUSB="
ls /sys/class/bluetooth/          # phải có hci0
hciconfig hci0 up && hciconfig hci0 piscan
```

Không có `/sys/class/bluetooth` = mảnh cấu hình chưa ăn vào → kiểm bằng phép
`md5sum /boot/Image` ở phần nghiệm thu trên.

---

## HUD svc — đồng bộ với bo (25/08/2026)

Ba thứ đã lệch giữa recipe và bo thật, **cái thứ ba nguy hiểm**:

| | Recipe (cũ) | Bo thật |
|---|---|---|
| `hud_app` | `a5057d8d` | `d7b7ff2f` |
| `HUD_ARGS` | `--input null:` | có `--signals` + bàn phím |
| `chan-demo.cfg` | **không có trong recipe** | có trên bo |

`chan-demo.cfg` chưa bao giờ nằm trong image — nó được tạo tay trên bo. Flash image mới là
mất, `--signals` trỏ vào khoảng không, `hud_app` lui về bảng demo `SIGDB_HUD_DEMO` và trên
xe thật nó giải mã khung quảng bá của hãng thành **179,2 km/h "hợp lệ", số D, biển 123
km/h**. Số sai trông như số thật. **Giờ đã được `do_install` cài vào `/etc/hud/`.**

### `HUD_INPUT=auto` — tự dò bàn phím, đừng ghim `event1`

`hud-init` giờ tự tìm bàn phím rồi chèn `--input evdev:...` vào lệnh chạy. Lý do không ghim
cứng: số hiệu `eventN` phụ thuộc **thứ tự cắm**, rút ra cắm lại là nhảy số, và HUD mất nút
bấm mà không một dòng lỗi nào.

Lọc theo `kbd` **VÀ** `leds`: bo có sẵn `gpio-keys` mang handler `kbd` nhưng **không có
`leds`** (đèn Caps Lock) — chỉ bàn phím thật mới có. Lấy cái `kbd` đầu tiên là vớ nhầm
`gpio-keys` ngay (đã dính 24/08).

Không thấy bàn phím → `null:` (an toàn), không để trống vì URI rỗng làm `hud_app` từ chối
khởi động.

Mã phím: `28`=Enter(màn kế) `14`=Backspace(màn trước) `33`=F(xoay lật) `103/108`=độ sáng
`2,3,4`=màn 1/2/3. **Cố ý không gán phím nào vào QUIT** — `start-stop-daemon` không tự khởi
động lại, nên một phím làm tắt HUD giữa đường là quá rủi ro.

### `HUD_ARGS` hiện hành

```
--can socketcan:can0 --probe on --obd2 --signals /etc/hud/chan-demo.cfg --navlink null:
```

**Không** `--loud-probe`: đo 23/08 cho thấy bo cam khẩu được (`can <LISTEN-ONLY,FD>`),
đường fail-closed chưa bao giờ chạy, cờ đó thừa.

**Không** `--active-probe`: `--obd2` đã bao gồm bước bắt tay chuẩn ISO 15765-4. Tách ra làm
cờ riêng là sai — chuẩn không có bước "nghe trước", tester khởi xướng, và bus chỉ-đáp là
một lớp **xe thật** (gateway cách ly cổng OBD) chứ không phải chuyện của bàn giả lập.

---

## Máy build này KHÔNG phải máy 20 core

`.wslconfig`: **8 lõi, 10 GB RAM, 16 GB swap**. AGENTS.md cũ ghi `BB_NUMBER_THREADS = "20"`
và `PARALLEL_MAKE = "-j 20"` cho host 20 core — để nguyên trên máy này là OOM chắc chắn
(20 × 20 = tới 400 tiến trình biên dịch lúc cao điểm).

Đã hạ xuống `4` / `-j 4` trong `local.conf` (tối đa 16 tiến trình, vừa 10 GB).

**Đổi máy build thì nhớ chỉnh lại hai số này theo `nproc` và `free -g`.**

## Bluetooth: để điện thoại THẤY và GHÉP ĐÔI được — hai mảnh, thiếu một là câm (25/08/2026)

Sau khi flash image có `CONFIG_BT`, quét điện thoại **vẫn không thấy** bo. Ba lớp chồng
nhau, lớp nào cũng đủ giết, đo trên bo thật:

1. **`DiscoverableTimeout` mặc định 180 giây** — bo tự ẩn sau 3 phút kể từ boot. Người
   cầm điện thoại quét sau đó không bao giờ thấy.
2. **`Class = 0x000000`** — nhiều điện thoại LỌC BỎ thiết bị class 0 khỏi danh sách quét.
   Bo hiện diện trên sóng mà màn hình vẫn trống.
3. **`hciconfig` bị `bluetoothd` ghi đè** — daemon là chủ; đặt tên/piscan bằng `hciconfig`
   chỉ sống vài giây. Phải nói chuyện qua `main.conf` + `bluetoothctl`.

### Mảnh 1 — `recipes-connectivity/bluez5/bluez5_%.bbappend` + `main.conf`

`Name = HUD-RZV2L`, `Class = 0x000100`, `DiscoverableTimeout = 0`, `PairableTimeout = 0`,
`[Policy] AutoEnable = true`. (bluez5 5.55 Dunfell KHÔNG tự cài main.conf nào — đây là
thêm, không phải ghi đè.)

### Mảnh 2 — recipe `onekiwi-bt`: cú hích lúc boot (S21, sau S20bluetooth)

**Vì sao main.conf một mình KHÔNG đủ:** BlueZ 5.55 không có khoá nào trong main.conf đặt
trạng thái Discoverable/Pairable BAN ĐẦU — chỉ có các *Timeout*. Trạng thái đó nằm trong
`/var/lib/bluetooth/<địa-chỉ>/settings`, mà trên bo VỪA FLASH SẠCH thư mục này rỗng →
adapter lên ở chế độ ẨN. Đo thật: sau reboot, Pairable quay về "no" dù trước đó đã bật tay.

Script đợi `Powered: yes` (tối đa 20 giây, chạy trong NỀN — không kéo dài boot, S99hud
vẫn lên đúng giờ) rồi `bluetoothctl discoverable on` + `pairable on`.

### Nghiệm thu đã chạy trên bo (trước khi đưa vào image)

Reboot sạch, không gõ lệnh nào:
```
Name: HUD-RZV2L   Class: 0x000100   Powered: yes
Discoverable: yes   Pairable: yes   UP RUNNING PSCAN ISCAN
```

### Còn thiếu gì cho SPP

Thấy + ghép đôi được ≠ nối SPP được: chưa có bản ghi SDP nào công bố hồ sơ Serial Port.
Đó là việc của `nl_btspp.c` (backend `btspp:` trong hud_app) khi nó chạy — nhánh
`feat/bluetooth` của repo rzv2l-hud.
