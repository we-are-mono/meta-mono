# Mono Yocto meta layer

This layer includes everything you need to build a firmware image for Gateway. On top of that, you can also build an _SDK_ image that can be flashed onto eMMC to explore all the device's capabilities and potential.

## Getting started

The recommended way to building images for Gateway (development kit) is by using [kas](https://github.com/siemens/kas).

Before you start, install the dependencies:

```
$ sudo apt-get install build-essential chrpath cpio debianutils diffstat file gawk gcc git iputils-ping libacl1 liblz4-tool locales python3 python3-git python3-jinja2 python3-pexpect python3-pip python3-subunit socat texinfo unzip wget xz-utils zstd pipx

$ pipx install kas
$ pipx ensurepath
$ source ~/.bashrc
```

First, clone the repository, then:
```

$ git clone https://github.com/we-are-mono/meta-mono.git
$ cd meta-mono/kas

# Make new site.conf from example and edit/configure it
# Note that the DL_DIR and SSTATE_DIR need to exist and have proper permissions for your current user
$ cp site.example.conf site.conf 

# if you want SDK: 
$ kas shell distro/mono-sdk.yaml
$ bitbake mono-sdk-image

# or, if you want firmware:
$ kas shell distro/recovery.yaml
$ bitbake firmware
```

