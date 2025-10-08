# Mono Yocto meta layer

This layer includes everything you need to build a firmware image for Gateway. On top of that, you can also build an _SDK_ image that can be flashed onto eMMC to explore all the device's capabilities and potential.

## Getting started

The recommended way to building images for Gateway (development kit) is by using [kas](https://github.com/siemens/kas).

First, clone the repository, then:
```
$ git clone https://github.com/we-are-mono/meta-mono.git
$ cd meta-mono/kas
$ cp site.example.conf site.conf # Don't forget to configure it

# if you want SDK: 
$ kas shell distro/mono-sdk.yaml
$ bitbake mono-sdk-image

# or, if you want firmware:
$ kas shell distro/recovery.yaml
$ bitbake firmware
```

