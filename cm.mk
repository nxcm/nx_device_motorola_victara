$(call inherit-product, device/motorola/victara/full_victara.mk)

# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_full_phone.mk)

# Enhanced NFC
$(call inherit-product, vendor/cm/config/nfc_enhanced.mk)

PRODUCT_RELEASE_NAME := MOTO X (2014)
PRODUCT_NAME := nx_victara
NX_MODEL_NAME := Moto X (2014)

PRODUCT_GMS_CLIENTID_BASE := android-motorola

PRODUCT_BUILD_PROP_OVERRIDES += \
    PRODUCT_NAME=victara_retbr \
    BUILD_FINGERPRINT=motorola/victara_retbr/victara:5.1/LPE23.32-14/13:user/release-keys \
    PRIVATE_BUILD_DESC="victara_retbr-user 5.1 LPE23.32-14 13 release-keys"
