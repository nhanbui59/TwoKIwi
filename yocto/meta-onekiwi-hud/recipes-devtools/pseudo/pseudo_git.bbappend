# Fix pseudo "unknown base path for fd" trên Ubuntu 22.04+ glibc 2.35+
# Fix openat2 wrapper chỉ có trên master branch, oe-core branch bị stuck 2023-01.
SRCREV = "823895ba708c63f6ae4dcbfc266210f26c02c698"
PV = "1.9.0+git${SRCPV}"

# Use master branch (có fix openat2)
SRC_URI = "git://git.yoctoproject.org/pseudo;branch=master \
           file://fallback-passwd \
           file://fallback-group \
"

# Bỏ prebuilt tarball + patch old-glibc
SRC_URI_remove_class-native = " \
    http://downloads.yoctoproject.org/mirror/sources/pseudo-prebuilt-2.33.tar.xz;subdir=git/prebuilt;name=prebuilt \
    file://older-glibc-symbols.patch \
"
SRC_URI_remove_class-nativesdk = " \
    http://downloads.yoctoproject.org/mirror/sources/pseudo-prebuilt-2.33.tar.xz;subdir=git/prebuilt;name=prebuilt \
    file://older-glibc-symbols.patch \
"
