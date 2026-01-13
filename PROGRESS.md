# ASK Kernel 6.12 Port - Fixed Issues

This document tracks all issues that have been resolved during the ASK (Acceleration Support Kit) port from kernel 5.4 to 6.12.

---

## Issue #1: Missing module_init/module_exit

**Status**: FIXED

**File**: `sources/linux/net/netfilter/comcerto_fp_netfilter.c`

**Problem**: The `module_init(fp_netfilter_init)` and `module_exit(fp_netfilter_exit)` lines were missing from the file, preventing the netfilter hooks from registering.

**Symptom**: `dmesg | grep fp_netfilter` showed no output, netfilter hooks never registered.

**Fix**: Added the missing lines at the end of `comcerto_fp_netfilter.c`:
```c
module_init(fp_netfilter_init);
module_exit(fp_netfilter_exit);
```

---

## Issue #2: Excessive debug printks

**Status**: FIXED

**Files**:
- `sources/linux/net/netfilter/comcerto_fp_netfilter.c`
- `sources/linux/net/core/dev.c`

**Problem**: Debug `printk` statements firing on every packet caused severe performance degradation and dmesg flooding.

**Fix**: Removed/commented out per-packet debug prints.

---

## Issue #3: libnetfilter-conntrack byte order bug

**Status**: FIXED

**File**: `meta-mono-bsp/recipes-filter/libnetfilter/files/01-nxp-ask-comcerto-fp-extensions.patch`

**Problem**: Custom libnetfilter-conntrack patch incorrectly added `ntohl()`/`ntohs()` when parsing fp_info attributes.

**Symptom**: CMM logs showed "No such device" error. On little-endian ARM, `ntohl(6)` = 100663296 (garbage ifindex).

**Fix**: Removed byte swapping from all fp_info attribute parsing:
```c
// Before (broken):
ct->fp_info[dir].iif = ntohl(mnl_attr_get_u32(tb[CTA_COMCERTO_FP_IIF]));

// After (fixed):
ct->fp_info[dir].iif = mnl_attr_get_u32(tb[CTA_COMCERTO_FP_IIF]);
```

---

## Issue #4: Conntrack not active by default

**Status**: FIXED (workaround)

**Problem**: Conntrack module loaded but doesn't track connections until iptables rules activate it.

**Symptom**: `cat /proc/net/nf_conntrack` was empty, `ct` pointer NULL in netfilter hooks.

**Fix**: Added iptables rules to activate conntrack:
```bash
iptables -A INPUT -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
iptables -A OUTPUT -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
iptables -A FORWARD -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
```

---

## Issue #5: CMM conntrack API compatibility (nfct_clear)

**Status**: FIXED

**File**: `sources/ASK/cmm-17.03.1/src/conntrack.c`

**Problem**: `nfct_clear()` function doesn't exist in newer libnetfilter-conntrack versions.

**Fix**: Replaced with `nfct_destroy()` + `nfct_new()` pattern, or used `memset()` for clearing conntrack structures.

---

## Issue #6: CMM RTM_GET4RD undefined

**Status**: FIXED

**File**: `sources/ASK/cmm-17.03.1/src/module_4rd.c`

**Problem**: `RTM_GET4RD` netlink message type doesn't exist in kernel 6.12.

**Fix**: Added conditional compilation or stubbed out the 4RD (4over6) functionality.

---

## Issue #7: RX Checksum Offload Breaking TCP

**Status**: FIXED

**Files**:
- `sources/linux/drivers/net/ethernet/freescale/sdk_dpaa/dpaa_eth_common.c`
- `sources/linux/drivers/net/ethernet/freescale/sdk_dpaa/dpaa_eth_common.h`
- `sources/linux/drivers/net/ethernet/freescale/sdk_dpaa/dpaa_eth.c`

**Symptom**: TCP connections hang or are extremely slow (90+ seconds for simple requests). ICMP ping works fine. This is a telltale sign of RX checksum offload issues.

**Quick diagnostic**: If `ethtool -K eth3 rx off` immediately fixes TCP performance, this is the issue.

**Root Cause**: The initial 6.12 kernel port was based on an incomplete/outdated patch that was missing the `dpa_fix_features()` function. This function contains a critical line that disables hardware RX checksum offload:

```c
unsupported_features |= NETIF_F_RXCSUM;
```

Without this line, the kernel advertises RX checksum offload as supported. The FMan hardware attempts L4 checksum validation but produces unreliable results, causing TCP packets to be dropped or marked as corrupted.

**NXP's original implementation** (in `001-layerscape-lsdk-kernel_linux_5_4_3_00_0.patch`):
- Line 54814: `unsupported_features |= NETIF_F_RXCSUM;`
- Line 50715: `.ndo_fix_features = dpa_fix_features,`

NXP knew this feature was unreliable and explicitly disabled it in their SDK.

**Fix**: Added the missing `dpa_fix_features()` function and registered it:

1. Added to `dpaa_eth_common.c`:
```c
netdev_features_t dpa_fix_features(struct net_device *dev,
                                   netdev_features_t features)
{
    netdev_features_t unsupported_features = 0;

    /* We don't support enabling Rx csum through ethtool yet */
    unsupported_features |= NETIF_F_RXCSUM;

    features &= ~unsupported_features;

    return features;
}
EXPORT_SYMBOL(dpa_fix_features);
```

2. Added declaration to `dpaa_eth_common.h`

3. Registered callback in `dpaa_eth.c`:
```c
.ndo_fix_features = dpa_fix_features,
```

**Verification**: After fix, `ethtool -k eth3 | grep rx-checksum` shows `off`, and TCP traffic works at full speed.

**Lesson learned**: When porting kernel patches between versions, ensure ALL functions are included - a missing callback like this can cause subtle but severe networking issues.

---

## Issue #8: Missing qosconnmark Functions in libnetfilter-conntrack

**Status**: FIXED

**File**: `meta-mono-bsp/recipes-filter/libnetfilter/files/01-nxp-ask-comcerto-fp-extensions.patch`

**Problem**: CMM is built with `USE_QOSCONNMARK` enabled and calls `nfct_set_attr_u64(ct, ATTR_QOSCONNMARK, ...)`. The libnetfilter-conntrack patch needed all required callback functions (setter, build, copy, compare, snprintf) for CMM to properly manage QoS marks on connections.

**Required Functions** (all implemented):
- ✅ getter function (`get_attr_qosconnmark`)
- ✅ parse function
- ✅ setter function (`set_attr_qosconnmark`) - line 619
- ✅ build function (`nfct_build_qosconnmark`) - line 248
- ✅ copy function (`copy_attr_qosconnmark`) - line 319
- ✅ compare function (`cmp_qosconnmark`) - line 289
- ✅ snprintf function (`__snprintf_qosconnmark`) - line 644

**Evidence**: CMM uses `cmmQosmarkSet()` in multiple places:
- `third_part.c:206` - `cmmQosmarkSet(ct, msg.qosmark)`
- `forward_engine.c:1475` - `cmmQosmarkSet(ctTemp, ctCmd->qosconnmark)`
- `conntrack.c:3826` - `cmmQosmarkSet(ctEntry->ct, qosmark)`

**Fix**: All qosconnmark functions are implemented in the libnetfilter-conntrack patch:

1. **setter.c** - `set_attr_qosconnmark()`:
```c
static void set_attr_qosconnmark(struct nf_conntrack *ct, const void *value, size_t len)
{
    ct->qosconnmark = *((u_int64_t *) value);
}
```

2. **build_mnl.c** - `nfct_build_qosconnmark()` for sending qosconnmark to kernel via netlink

3. **copy.c** - `copy_attr_qosconnmark()` for cloning conntrack objects

4. **compare.c** - `cmp_qosconnmark()` for comparing conntrack objects

5. **snprintf_default.c** - `__snprintf_qosconnmark()` for CLI output

---

## Issue #9: FM ioctl error spam from isatty() probes

**Status**: FIXED

**File**: `sources/linux/drivers/net/ethernet/freescale/sdk_fman/src/wrapper/lnxwrp_ioctls_fm.c`

**Problem**: Error messages in dmesg during boot:
```
cpu 1: ! MINOR FM Error [...]: Invalid Selection;
cpu 1: invalid ioctl: cmd:0x00005401(type:0x54, nr: 1.
```

**Root Cause**: The ioctl `0x5401` (type `0x54` = 'T', nr 1) is TCGETS - a standard TTY ioctl sent by `isatty()`. When userspace opens the FM device and checks if it's a terminal, the kernel sends TCGETS. The FM driver's default ioctl handler logged an error for unrecognized ioctls.

**Affected functions**:
- `LnxwrpFmPcdIOCTL()` - line 3404
- `LnxwrpFmPortIOCTL()` - line 4664

**Fix**: Added a check for TTY ioctls (type 'T') before the RETURN_ERROR macro to silently return `E_NOT_SUPPORTED`:
```c
default:
    /* Silently reject TTY ioctls (e.g., TCGETS from isatty()) */
    if (_IOC_TYPE(cmd) == 'T')
        return E_NOT_SUPPORTED;
    RETURN_ERROR(MINOR, E_INVALID_SELECTION,
        ("invalid ioctl: cmd:0x%08x(type:0x%02x, nr: %d.\n",
        cmd, _IOC_TYPE(cmd), _IOC_NR(cmd)));
```

**Verification**: `dmesg | grep -i "invalid ioctl"` shows no output after boot.

---

## Summary

| Issue | Description | Status |
|-------|-------------|--------|
| #1 | Missing module_init/module_exit | FIXED |
| #2 | Excessive debug printks | FIXED |
| #3 | libnetfilter-conntrack byte order bug | FIXED |
| #4 | Conntrack not active by default | FIXED |
| #5 | CMM nfct_clear() compatibility | FIXED |
| #6 | CMM RTM_GET4RD undefined | FIXED |
| #7 | RX Checksum Offload Breaking TCP | FIXED |
| #8 | Missing qosconnmark functions | FIXED |
| #9 | FM ioctl error spam from isatty() | FIXED |

All critical issues for basic ASK functionality have been resolved. The SDK image now supports hardware-accelerated networking with working TCP/UDP traffic and QoS marking.
