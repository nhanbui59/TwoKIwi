cmake_minimum_required(VERSION 3.10)
project(hud-app C)

set(CMAKE_C_STANDARD 11)

find_package(PkgConfig REQUIRED)
pkg_check_modules(LVGL REQUIRED lvgl)
pkg_check_modules(DRM  REQUIRED libdrm)

add_execable(hud-app hud-app.c)

target_include_directories(hud-app PRIVATE
    ${LVGL_INCLUDE_DIRS}
    ${DRM_INCLUDE_DIRS}
)

target_link_libraries(hud-app PRIVATE
    ${LVGL_LIBRARIES}
    ${DRM_LIBRARIES}
    EGL
    GLESv2
)
