#!/usr/bin/env python3
"""
OneKiWi RZ/V2L — SPI bootloader reflash qua serial (SCIF mode).

Xóa "please waiting boot img..." bằng cách flash BL2 + FIP sạch từ Yocto.

CÁCH DÙNG:
1. Gạt boot switch sang SCIF download mode
2. Reset board
3. Chạy: python3 flash_spi.py
   (script tự phát hiện boot ROM xin XMODEM, tự gửi flash writer, tự flash BL2+FIP)
4. Khi script báo DONE: tắt nguồn, gạt switch về QSPI mode, bật lại
"""
import serial, time, sys, os
import xmodem

PORT = '/dev/ttyUSB0'
BAUD = 115200
DEPLOY = '/home/nhanbv/Workspace/HUD/30.Img/TwoKIwi/yocto/build/tmp/deploy/images/onekiwi-rzv2l'

FILES = {
    'writer': f'{DEPLOY}/Flash_Writer_SCIF_RZV2L_SMARC_PMIC_DDR4_2GB_1PCS.mot',
    'bl2':    f'{DEPLOY}/bl2_bp-onekiwi-rzv2l_pmic.srec',
    'fip':    f'{DEPLOY}/fip-onekiwi-rzv2l_pmic.srec',
}

for k, p in FILES.items():
    if not os.path.exists(p):
        sys.exit(f'MISSING {k}: {p}')

s = serial.Serial(PORT, BAUD, timeout=1)
s.reset_input_buffer()

def read_until(tokens, timeout, label):
    """Đọc cho tới khi gặp 1 trong tokens (bytes). Trả về buffer."""
    buf = b''
    t0 = time.time()
    while time.time() - t0 < timeout:
        d = s.read(4096)
        if d:
            buf += d
            sys.stdout.write(d.decode('utf-8', errors='replace'))
            sys.stdout.flush()
            for t in tokens:
                if t in buf:
                    return buf
    return buf

def getc(size, timeout=1):
    return s.read(size) or None

def putc(data, timeout=1):
    s.write(data)
    return len(data)

def xsend(path):
    """Gửi file qua XMODEM."""
    with open(path, 'rb') as f:
        data = f.read()
    stream = io.BytesIO(data)
    xm = xmodem.XMODEM(getc, putc)
    ok = xm.send(stream, retry=16, timeout=20)
    return ok

import io

print('=== Bước 1: Đợi Boot ROM xin file (gạt SCIF mode + reset board) ===')
print('    (Boot ROM gửi ký tự C liên tục khi sẵn sàng)')
buf = read_until([b'C', b'please send'], 120, 'bootrom')
if b'C' not in buf and b'please send' not in buf:
    print('\nKHÔNG thấy XMODEM request. Kiểm tra switch SCIF + reset lại.')
    s.close()
    sys.exit(1)

print('\n=== Bước 2: Gửi Flash Writer qua XMODEM ===')
if not xsend(FILES['writer']):
    print('XMODEM gửi flash writer FAIL')
    s.close()
    sys.exit(1)
print('\n[writer sent]')

# Đợi flash writer khởi động + prompt '>'
buf = read_until([b'>'], 30, 'writer boot')
if b'>' not in buf:
    print('\nFlash writer chưa ra prompt. Thoát.')
    s.close()
    sys.exit(1)

def xls2(top_addr, save_addr, path, label):
    print(f'\n=== Bước: Flash {label} ===')
    s.write(b'XLS2\n')
    read_until([b'Program Top Address'], 10, 'xls2')
    s.write(top_addr.encode() + b'\n')
    read_until([b'Qspi Save Address'], 10, 'addr1')
    s.write(save_addr.encode() + b'\n')
    # Flash writer sẽ xóa sector + hỏi gửi file
    read_until([b'please send', b'C'], 60, 'erase+ready')
    time.sleep(0.5)
    if not xsend(path):
        print(f'XMODEM gửi {label} FAIL')
        s.close()
        sys.exit(1)
    print(f'\n[{label} sent — đợi ghi flash]')
    # Đợi ghi xong về prompt '>'
    buf = read_until([b'>'], 120, 'write')
    if b'>' not in buf:
        print(f'{label}: chưa thấy prompt sau khi ghi — kiểm tra output!')
        s.close()
        sys.exit(1)

# BL2: top=11E00, save=0
xls2('11E00', '0', FILES['bl2'], 'BL2')
# FIP: top=0, save=20000
xls2('0', '20000', FILES['fip'], 'FIP')

print('\n========================================')
print('=== FLASH XONG! ===')
print('========================================')
print('1. TẮT NGUỒN board')
print('2. Gạt boot switch về QSPI boot mode')
print('3. Bật lại — chữ "please waiting boot img..." sẽ biến mất')
s.close()
