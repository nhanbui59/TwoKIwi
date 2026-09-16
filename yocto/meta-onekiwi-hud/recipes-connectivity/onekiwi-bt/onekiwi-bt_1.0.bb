SUMMARY = "Cho HUD duoc tim thay va ghep doi Bluetooth ngay tu boot"
DESCRIPTION = "BlueZ 5.55 khong dat duoc trang thai Discoverable/Pairable ban dau tu main.conf, va tren bo flash sach thi /var/lib/bluetooth rong nen adapter len o che do an. Script nay hich hai co do sau khi bluetoothd len."
LICENSE = "CLOSED"

SRC_URI = "file://onekiwi-bt"
S = "${WORKDIR}"

inherit update-rc.d

INITSCRIPT_NAME = "onekiwi-bt"
# 21 = ngay sau S20bluetooth. Phan doi chay trong NEN nen khong keo dai boot.
INITSCRIPT_PARAMS = "start 21 2 3 4 5 ."

# bluez5-testtools mang sdptool — thieu no thi buoc cong bo SPP im lang hong.
RDEPENDS_${PN} = "bluez5 bluez5-testtools"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/onekiwi-bt ${D}${sysconfdir}/init.d/onekiwi-bt
}

FILES_${PN} = "${sysconfdir}/init.d/onekiwi-bt"
