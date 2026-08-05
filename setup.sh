#!/bin/bash
set -e

# OneKiWi RZ/V2L HUD - Automated Build Environment Setup
# Clone về máy mới, chạy: ./setup.sh

YOCTO_DIR="yocto"
BRANCH="dunfell"

echo "========================================="
echo " OneKiWi RZ/V2L HUD - Build Setup"
echo "========================================="
echo ""

# Ensure yocto dir exists
mkdir -p "$YOCTO_DIR"
cd "$YOCTO_DIR"

# 1. Clone poky (Yocto core)
if [ ! -d "poky" ]; then
    echo "[1/5] Cloning poky ($BRANCH)..."
    git clone -b $BRANCH git://git.yoctoproject.org/poky.git
else
    echo "[1/5] poky: OK"
fi

# 2. Clone Renesas RZ BSP
if [ ! -d "meta-renesas" ]; then
    echo "[2/5] Cloning meta-renesas..."
    git clone https://github.com/renesas-rz/meta-renesas.git
    cd meta-renesas
    git checkout rz-dunfell
    cd ..
else
    echo "[2/5] meta-renesas: OK"
fi

# 3. Clone meta-openembedded
if [ ! -d "meta-openembedded" ]; then
    echo "[3/5] Cloning meta-openembedded ($BRANCH)..."
    git clone -b $BRANCH https://github.com/openembedded/meta-openembedded.git
else
    echo "[3/5] meta-openembedded: OK"
fi

# 4. meta-onekiwi-hud (already in repo)
echo "[4/5] meta-onekiwi-hud: OK (from repo)"

# 5. meta-lvgl-v9 (LVGL demo)
if [ ! -d "meta-lvgl-v9/conf" ]; then
    echo "[5/5] WARNING: meta-lvgl-v9 missing or incomplete."
    echo "      If LVGL is needed, ensure meta-lvgl-v9 layer is present."
else
    echo "[5/5] meta-lvgl-v9: OK"
fi

echo ""
echo "========================================="
echo " Setup Complete!"
echo "========================================="
echo ""
echo "Next steps:"
echo "  cd yocto"
echo "  source poky/oe-init-build-env build"
echo "  bitbake onekiwi-hud-image"
echo ""
echo "Output: tmp/deploy/images/onekiwi-rzv2l/onekiwi-hud-image-onekiwi-rzv2l.wic"
echo ""
echo "Flash SD:"
echo "  sudo dd if=tmp/deploy/images/onekiwi-rzv2l/onekiwi-hud-image-onekiwi-rzv2l.wic \\"
echo "         of=/dev/sdX bs=4M status=progress conv=fsync"
