ATF_BRANCH = "mono-development"
ATF_SRC = "git://github.com/we-are-mono/atf.git;protocol=https"
SRC_URI = "${ATF_SRC};branch=${ATF_BRANCH}"
SRCREV = "5e09df26d071f3139a09a378bae26fba473a9d4f"

# To get rid of UEFI requirement that comes with QorIQ meta layer
DEPENDS += "rcw u-boot"
EXTRA_OEMAKE += "LOG_LEVEL=20"
do_compile[depends] = "u-boot:do_deploy rcw:do_deploy"

BOOTTYPE = "qspi emmc"
RCW_FOLDER = "gateway_dk"
PLATFORM:gateway = "gateway_dk"
UBOOT_BINARY = "${DEPLOY_DIR_IMAGE}/u-boot.bin"
