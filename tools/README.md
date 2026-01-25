# Gateway DK Deploy Tool

Automated build and flash tool for the LS1046A Gateway development kit.

## Prerequisites

```bash
sudo apt install python3-serial python3-yaml python3-rich arping
```

## Configuration

Copy the example config and customize:

```bash
cp config.yaml.example config.yaml
```

Edit `config.yaml`:

```yaml
network:
  host_interface: "eth0"    # Interface connected to target
  target_interface: "eth3"  # Interface on target device
  http_port: 8000

serial:
  device: "/dev/ttyUSB0"
  baudrate: 115200
```

## Usage

```bash
# Full workflow: build images + flash to target
./deploy

# Build all images (no flash)
./deploy build

# Build firmware only
./deploy build --firmware-only

# Build rootfs only
./deploy build --rootfs-only

# Flash only (use existing images)
./deploy flash

# Flash firmware only (skip rootfs - faster iteration)
./deploy flash --firmware-only

# Flash rootfs only (skip firmware)
./deploy flash --rootfs-only

# Flash QSPI NOR only
./deploy flash --qspi-only

# Flash eMMC boot area only
./deploy flash --emmc-only

# Check target state via serial
./deploy status

# Boot target into recovery mode
./deploy recovery
```

## Options

| Flag | Description |
|------|-------------|
| `--no-build` | Skip build step in deploy command |
| `--no-reboot` | Don't reboot target after flashing |
| `--config FILE` | Use alternative config file |
| `-v, --verbose` | Enable debug output |

## How It Works

1. Builds firmware and SDK images via kas/bitbake
2. Detects host IP and finds unused IP for target
3. Starts HTTP server to serve images
4. Connects to target via serial (UART)
5. Boots target into recovery mode (initramfs)
6. Configures target network
7. Downloads and verifies images (SHA256)
8. Flashes to NOR (`/dev/mtd0`) and eMMC (`/dev/mmcblk0`)
9. Reboots target
