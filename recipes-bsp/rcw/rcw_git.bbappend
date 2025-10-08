# meta-freescale layer already provides RCW that is good enough,
# we only have to point to our custom repository and branch
SRC_URI = "git://github.com/we-are-mono/rcw.git;protocol=https;branch=mono-development"
SRCREV = "749b14e9e720e558d8011728f13037d5796159e2"
BOARD_TARGETS:gateway = "gateway_dk"
