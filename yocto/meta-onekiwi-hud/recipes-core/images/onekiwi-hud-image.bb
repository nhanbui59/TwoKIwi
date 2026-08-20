SUMMARY = "OneKiWi RZ/V2L LVGL Demo Image"
LICENSE = "MIT"

inherit core-image

WKS_FILE = "onekiwi-rzv2l.wks"
IMAGE_FSTYPES = "wic"

IMAGE_INSTALL += " \
    packagegroup-core-boot \
    packagegroup-base \
    openssh openssh-sftp-server \
    can-utils iproute2 \
    python3 python3-pip python3-pillow \
    kernel-modules \
    splash-logo \
    lvgl-v9 \
    lvgldemo \
    usbutils \
"

PACKAGE_EXCLUDE += " xserver-xorg \
    packagegroup-core-x11 packagegroup-core-x11-base"

# Fix MBR partition 1 type: 0x0C → 0x06 (FAT16)
# + Fix FAT hidden_sectors = 2048 (U-Boot SPI cần)
fix_wic_boot() {
    python3 -c "
import struct, sys
wic = open('${IMGDEPLOYDIR}/${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.wic','r+b')
mbr = bytearray(wic.read(512))

# 1. Fix partition type (0x0C FAT32 LBA -> 0x06 FAT16)
p1_type_off = 0x1BE + 4
if mbr[p1_type_off] == 0x0C:
    mbr[p1_type_off] = 0x06
    print('patched P1 type 0x0C->0x06', file=sys.stderr)

# 2. Get P1 start sector from MBR
p1_start = struct.unpack('<I', mbr[0x1C6:0x1CA])[0]
wic.seek(p1_start * 512)
fat_bs = bytearray(wic.read(512))

# 3. Set hidden_sectors = p1_start (LBA của P1)
# BPB hidden_sectors at offset 0x1C (4 bytes LE)
struct.pack_into('<I', fat_bs, 0x1C, p1_start)
current = struct.unpack('<I', fat_bs[0x1C:0x20])[0]
print(f'FAT hidden_sectors: {current}', file=sys.stderr)

wic.seek(p1_start * 512)
wic.write(bytes(fat_bs))
wic.seek(0)
wic.write(bytes(mbr))
wic.close()
print('fix_wic_boot: done', file=sys.stderr)
"
}
IMAGE_POSTPROCESS_COMMAND += "fix_wic_boot;"

# Remove the tty1 getty: it prints "login:" on the framebuffer (HUD screen
# must stay clean). Serial getty (ttySC0) + SSH remain available for login.
remove_tty1_getty() {
    sed -i '/getty.*tty1/d' ${IMAGE_ROOTFS}/etc/inittab
}
ROOTFS_POSTPROCESS_COMMAND += "remove_tty1_getty;"

# Copy kernel + prebuilt DTB vào /boot/ rootfs (ext4)
install_boot_files() {
    install -d ${IMAGE_ROOTFS}/boot
    install -m 0644 ${DEPLOY_DIR_IMAGE}/Image ${IMAGE_ROOTFS}/boot/Image
    install -m 0644 ${TOPDIR}/../meta-onekiwi-hud/recipes-kernel/linux/linux-renesas/onekiwi-rzv2l.dtb \
        ${IMAGE_ROOTFS}/boot/onekiwi-rzv2l.dtb
    ln -sf onekiwi-rzv2l.dtb ${IMAGE_ROOTFS}/boot/r9a07g054l2-smarc.dtb
}
ROOTFS_POSTPROCESS_COMMAND += "install_boot_files;"
