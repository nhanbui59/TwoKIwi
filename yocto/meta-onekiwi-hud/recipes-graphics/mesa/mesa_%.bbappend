# Enable the open-source Mali-G31 (Bifrost) Gallium driver for the RZ/V2L.
#
# Background / why this is non-trivial:
#  - Mesa 20.0.x exposes panfrost as a PACKAGECONFIG that appends 'panfrost' to
#    GALLIUMDRIVERS (see poky meta/recipes-graphics/mesa/mesa.inc,
#    PACKAGECONFIG[panfrost] and PACKAGECONFIG[kmsro]).
#  - The Renesas meta-rzg2l mesa bbappend REMOVES 'egl gles' from mesa whenever
#    EXT_GFX_BACKEND=1. EXT_GFX_BACKEND is derived from the 'opengles' machine
#    feature (rzg2-common.inc), which the RZ/V2L sets -> so by default mesa is
#    built WITHOUT EGL/GLES (the proprietary-GPU path).
#  - The RZ/V2L's Mali-G31 has NO proprietary driver, so for Panfrost we want
#    mesa ITSELF to provide the EGL/GLES2/GBM stack. We therefore force
#    EXT_GFX_BACKEND=0 for this recipe (our layer has higher priority than
#    meta-rzg2l, so this assignment wins and the remove becomes a no-op), keep
#    the default 'egl gles gbm dri gallium', and add the panfrost + kmsro
#    gallium drivers.
#
# Note: this override is recipe-scoped to mesa. The weston-init bbappend also
# reads EXT_GFX_BACKEND, but under =1 it only adds '--idle-time=0' to the weston
# service, which is harmless for the Panfrost path.

EXT_GFX_BACKEND = "0"
PACKAGECONFIG_append = " panfrost kmsro"
