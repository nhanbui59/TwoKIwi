# Add onekiwi-rzv2l support (dunfell syntax: override do_compile)
do_compile() {
    if [ "${MACHINE}" = "onekiwi-rzv2l" ]; then
        BOARD="RZV2L_SMARC"
        PMIC_BOARD="RZV2L_SMARC_PMIC"
    elif [ "${MACHINE}" = "smarc-rzg2l" ]; then
        BOARD="RZG2L_SMARC"
        PMIC_BOARD="RZG2L_SMARC_PMIC"
    elif [ "${MACHINE}" = "rzg2l-dev" ]; then
        BOARD="RZG2L_15MMSQ_DEV"
    elif [ "${MACHINE}" = "smarc-rzg2lc" ]; then
        BOARD="RZG2LC_SMARC"
    elif [ "${MACHINE}" = "rzg2lc-dev" ]; then
        BOARD="RZG2LC_DEV"
    elif [ "${MACHINE}" = "smarc-rzg2ul" ]; then
        BOARD="RZG2UL_SMARC"
    elif [ "${MACHINE}" = "rzg2ul-dev" ]; then
        BOARD="RZG2UL_TYPE1_DEV"
    elif [ "${MACHINE}" = "smarc-rzv2l" ]; then
        BOARD="RZV2L_SMARC"
        PMIC_BOARD="RZV2L_SMARC_PMIC"
    elif [ "${MACHINE}" = "rzv2l-dev" ]; then
        BOARD="RZV2L_15MMSQ_DEV"
    fi
    cd ${S}

    oe_runmake BOARD=${BOARD}

    if [ "${PMIC_SUPPORT}" = "1" ] && [ -n "${PMIC_BOARD}" ]; then
        oe_runmake OUTPUT_DIR=${PMIC_BUILD_DIR} clean
        oe_runmake BOARD=${PMIC_BOARD} OUTPUT_DIR=${PMIC_BUILD_DIR}
    fi
}
