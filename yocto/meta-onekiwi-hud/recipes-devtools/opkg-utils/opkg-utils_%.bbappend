# Fix: git.yoctoproject.org cgit tarball URL broken (404). Use git fetch instead.
SRC_URI = "git://git.yoctoproject.org/opkg-utils;protocol=https \
           file://fix-reproducibility.patch \
"
SRCREV = "f64b7616284d55cfbb6a52e1e3ed170627ef5968"
S = "${WORKDIR}/git"
