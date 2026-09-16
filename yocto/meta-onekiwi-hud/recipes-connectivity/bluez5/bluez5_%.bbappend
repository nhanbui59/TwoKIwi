FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

# Cau hinh bluetoothd cua bo. bluez5 5.55 tren Dunfell KHONG cai main.conf
# nao (do tren image 25/08: /etc/bluetooth trong), nen day la them chu khong
# phai ghi de.
SRC_URI += "file://main.conf"

do_install_append() {
    install -d ${D}${sysconfdir}/bluetooth
    install -m 0644 ${WORKDIR}/main.conf ${D}${sysconfdir}/bluetooth/main.conf

    # bluetoothd PHAI chay che do compat (-C): BlueZ 5 bo SDP server socket
    # tru khi co co nay, ma sdptool can no de dang ky ban ghi SPP. Thieu SPP
    # tren SDP thi createRfcommSocketToServiceRecord() ben Android khong bao
    # gio tim ra kenh — app bao "Khong the ket noi" du da ghep doi.
    #
    # Va thang vao init script cua goi de giu nguyen moi logic khac. Mau sed
    # CO Y khong chua ky tu $ nao: dong goc la
    #     SSD_OPTIONS="--oknodo --quiet --exec $DAEMON -- $NOPLUGIN_OPTION"
    # va lan va truoc dung $DAEMON trong mau — no bi bitbake/shell an mat,
    # sinh ra mot lenh sed dut giua chung ("unterminated s command").
    sed -i '/^SSD_OPTIONS=/s/ -- / -- -C /' ${D}${sysconfdir}/init.d/bluetooth

    # Va phai kiem ket qua: sed khong khop la im lang — kieu hong te nhat.
    grep -q -- '-- -C' ${D}${sysconfdir}/init.d/bluetooth || \
        bbfatal "khong chen duoc -C vao init bluetooth — kiem lai mau sed"
}

FILES_${PN} += "${sysconfdir}/bluetooth/main.conf"
