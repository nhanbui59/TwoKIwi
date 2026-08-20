# Top-level CMake for the OneKiWi LVGL v9 fbdev demo.
#
# This file replaces LVGL's own CMakeLists.txt in the source tree (the original
# is backed up to CMakeLists.txt.lvgl.orig by the recipe). We re-use LVGL's own
# CMake machinery (env_support/cmake/custom.cmake) which defines:
#   - 'lvgl'           static lib (alias lvgl::lvgl)   - all LVGL C sources
#   - 'lvgl_thorvg'    static lib (alias lvgl::thorvg) - bundled ThorVG C++ src
# then add our minimal demo on top.
#
# ThorVG is enabled in lv_conf.h (LV_USE_THORVG_INTERNAL=1). When that is on,
# the LVGL core (lv_draw_sw.c) references tvg_engine_init(), which lives in the
# lvgl_thorvg C++ library, so we must link both lvgl::lvgl and lvgl::thorvg.

cmake_minimum_required(VERSION 3.12)
project(lvgl_v9_demo C CXX)
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 14)

set(LVGL_ROOT_DIR ${CMAKE_CURRENT_LIST_DIR})
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
# custom.cmake gates the example/demo libs on LV_CONF_BUILD_DISABLE_EXAMPLES /
# LV_CONF_BUILD_DISABLE_DEMOS (NOT LVGL_BUILD_*). Disable both so we don't drag
# in the SDL/libdrm-based demos on top of our fbdev target. We deliberately do
# NOT set LV_CONF_BUILD_DISABLE_THORVG_INTERNAL -- we need that library.
set(LV_CONF_BUILD_DISABLE_EXAMPLES ON CACHE BOOL "" FORCE)
set(LV_CONF_BUILD_DISABLE_DEMOS    ON CACHE BOOL "" FORCE)

include(${LVGL_ROOT_DIR}/env_support/cmake/custom.cmake)

add_executable(lvgl_v9_demo ${LVGL_ROOT_DIR}/main.c)

# Make sure lv_conf.h and the fbdev driver header are on the include path even
# if a future LVGL release changes its public include layout.
target_include_directories(lvgl_v9_demo PRIVATE
    ${LVGL_ROOT_DIR}
    ${LVGL_ROOT_DIR}/src
    ${LVGL_ROOT_DIR}/src/drivers/display/fb)

# lvgl::lvgl = core; lvgl::thorvg = bundled ThorVG (vector draw backend).
# pthread needed for LV_USE_OS == LV_OS_PTHREAD (LVGL tick/draw threads).
target_link_libraries(lvgl_v9_demo PRIVATE lvgl::lvgl lvgl::thorvg m pthread)
