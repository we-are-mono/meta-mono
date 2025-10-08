/*
 * SFP LED Control Daemon
 * 
 * Monitors network interface carrier state and controls LEDs accordingly.
 * 
 * Copyright 2025 Mono Technologies Inc.
 * Author: Tomaz Zaman <tomaz@mono.si>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <syslog.h>
#include <signal.h>
#include <errno.h>
#include <stdbool.h>
#include <dirent.h>
#include <limits.h>
#include <stdint.h>

#define MAX_PORTS 2
#define POLL_INTERVAL_MS 100
#define MAX_NETDEV_NAME 32

struct sfp_port {
	char netdev[MAX_NETDEV_NAME];  /* Dynamically discovered */
	const char *link_led;
	const char *activity_led;
	const char *sfp_name;  /* e.g., "sfp-xfi0" */
	int carrier_fd;
	int link_led_fd;
	int activity_led_fd;
	int mod_present_fd;  /* GPIO for module presence detection */
	bool last_carrier_state;
	bool last_module_present;
};

static struct sfp_port ports[MAX_PORTS] = {
	{
		.netdev = "",
		.link_led = "sfp0:link",
		.activity_led = "sfp0:activity",
		.sfp_name = "sfp-xfi0",
		.carrier_fd = -1,
		.link_led_fd = -1,
		.activity_led_fd = -1,
		.mod_present_fd = -1,
		.last_carrier_state = false,
		.last_module_present = false,
	},
	{
		.netdev = "",
		.link_led = "sfp1:link",
		.activity_led = "sfp1:activity",
		.sfp_name = "sfp-xfi1",
		.carrier_fd = -1,
		.link_led_fd = -1,
		.activity_led_fd = -1,
		.mod_present_fd = -1,
		.last_carrier_state = false,
		.last_module_present = false,
	},
};

static volatile sig_atomic_t running = 1;

/* Forward declarations */
static void setup_netdev_trigger(struct sfp_port *port);
static int find_netdev_for_sfp(const char *sfp_name, char *netdev_out, size_t out_size);

static void signal_handler(int sig)
{
	running = 0;
}

static int set_led_brightness(int fd, int brightness)
{
	char buf[4];
	int len;
	
	if (fd < 0)
		return -1;
	
	len = snprintf(buf, sizeof(buf), "%d\n", brightness);
	if (lseek(fd, 0, SEEK_SET) < 0)
		return -1;
	
	if (write(fd, buf, len) != len)
		return -1;
	
	return 0;
}

static int read_carrier_state(int fd)
{
	char buf[4];
	int ret;
	
	if (fd < 0)
		return 0;
	
	if (lseek(fd, 0, SEEK_SET) < 0)
		return 0;
	
	ret = read(fd, buf, sizeof(buf) - 1);
	if (ret <= 0)
		return 0;
	
	buf[ret] = '\0';
	return (buf[0] == '1');
}

static int read_module_present(int fd)
{
	char buf[512];
	int ret;
	char *line, *saveptr;
	
	if (fd < 0)
		return 0; /* Assume NOT present if we can't read */
	
	if (lseek(fd, 0, SEEK_SET) < 0)
		return 0;
	
	ret = read(fd, buf, sizeof(buf) - 1);
	if (ret <= 0)
		return 0;
	
	buf[ret] = '\0';
	
	/* Parse the state file looking for "moddef0: X" */
	line = strtok_r(buf, "\n", &saveptr);
	while (line != NULL) {
		if (strncmp(line, "moddef0:", 8) == 0) {
			/* Extract the value after "moddef0: " */
			char *value = line + 8;
			while (*value == ' ') value++;
			/* In debugfs: 1 = present, 0 = absent */
			return (*value == '1');
		}
		line = strtok_r(NULL, "\n", &saveptr);
	}
	
	return 0; /* Default to NOT present if we can't parse */
}

static int open_file_ro(const char *path)
{
	int fd = open(path, O_RDONLY);
	if (fd < 0) {
		syslog(LOG_WARNING, "Failed to open %s: %s", path, strerror(errno));
	}
	return fd;
}

static int open_file_wo(const char *path)
{
	int fd = open(path, O_WRONLY);
	if (fd < 0) {
		syslog(LOG_WARNING, "Failed to open %s: %s", path, strerror(errno));
	}
	return fd;
}

/*
 * Read a 32-bit big-endian value from a device tree property file
 */
static uint32_t read_dt_u32(const char *path)
{
	int fd;
	uint32_t val;
	
	fd = open(path, O_RDONLY);
	if (fd < 0)
		return 0;
	
	if (read(fd, &val, sizeof(val)) != sizeof(val)) {
		close(fd);
		return 0;
	}
	
	close(fd);
	return __builtin_bswap32(val);  /* Convert from big-endian */
}

/*
 * Find the network device associated with an SFP port by reading device tree
 * 
 * Process:
 * 1. Read the phandle of the SFP device from /sys/firmware/devicetree/base/sfp-xfiX/phandle
 * 2. Search through /sys/firmware/devicetree/base/soc/fman nodes for matching sfp property
 * 3. Read the cell-index from the matching ethernet node
 * 4. The netdev is fm1-mac with cell-index plus 1
 */
static int find_netdev_for_sfp(const char *sfp_name, char *netdev_out, size_t out_size)
{
	char path[PATH_MAX];
	uint32_t sfp_phandle;
	DIR *dir;
	struct dirent *entry;
	
	/* Read the phandle of our SFP device */
	snprintf(path, sizeof(path), "/sys/firmware/devicetree/base/%s/phandle", sfp_name);
	sfp_phandle = read_dt_u32(path);
	if (sfp_phandle == 0) {
		syslog(LOG_ERR, "Failed to read phandle for %s", sfp_name);
		return -1;
	}
	
	syslog(LOG_DEBUG, "%s phandle: 0x%x", sfp_name, sfp_phandle);
	
	/* Search through fman ethernet nodes */
	dir = opendir("/sys/firmware/devicetree/base/soc");
	if (!dir) {
		syslog(LOG_ERR, "Failed to open device tree soc directory");
		return -1;
	}
	
	while ((entry = readdir(dir)) != NULL) {
		if (strncmp(entry->d_name, "fman@", 5) != 0)
			continue;
		
		/* Found an fman node, search its ethernet children */
		DIR *fman_dir;
		struct dirent *eth_entry;
		char fman_path[PATH_MAX];
		
		snprintf(fman_path, sizeof(fman_path), "/sys/firmware/devicetree/base/soc/%s", entry->d_name);
		fman_dir = opendir(fman_path);
		if (!fman_dir)
			continue;
		
		while ((eth_entry = readdir(fman_dir)) != NULL) {
			if (strncmp(eth_entry->d_name, "ethernet@", 9) != 0)
				continue;
			
			/* Check if this ethernet node has an sfp property */
			snprintf(path, sizeof(path), "%s/%s/sfp", fman_path, eth_entry->d_name);
			uint32_t eth_sfp_phandle = read_dt_u32(path);
			
			if (eth_sfp_phandle == sfp_phandle) {
				/* Found matching ethernet node! Read its cell-index */
				uint32_t cell_index;
				
				snprintf(path, sizeof(path), "%s/%s/cell-index", fman_path, eth_entry->d_name);
				cell_index = read_dt_u32(path);
				
				/* The netdev name is fm1-mac<cell-index+1> */
				snprintf(netdev_out, out_size, "fm1-mac%u", cell_index + 1);
				
				closedir(fman_dir);
				closedir(dir);
				
				syslog(LOG_INFO, "Found netdev '%s' for SFP '%s' (cell-index=%u)", 
				       netdev_out, sfp_name, cell_index);
				return 0;
			}
		}
		
		closedir(fman_dir);
	}
	
	closedir(dir);
	syslog(LOG_ERR, "Could not find ethernet node for SFP '%s' (phandle 0x%x)", sfp_name, sfp_phandle);
	return -1;
}

static int setup_port(struct sfp_port *port)
{
	char path[256];
	
	/* Discover the network device name for this SFP from device tree */
	if (find_netdev_for_sfp(port->sfp_name, port->netdev, sizeof(port->netdev)) < 0) {
		syslog(LOG_ERR, "Failed to find network device for %s", port->sfp_name);
		return -1;
	}
	
	/* Open carrier file */
	snprintf(path, sizeof(path), "/sys/class/net/%s/carrier", port->netdev);
	port->carrier_fd = open_file_ro(path);
	
	/* Open SFP debugfs state file for module presence detection */
	snprintf(path, sizeof(path), "/sys/kernel/debug/%s/state", port->sfp_name);
	port->mod_present_fd = open_file_ro(path);
	if (port->mod_present_fd < 0) {
		syslog(LOG_WARNING, "Cannot open %s debugfs, module detection disabled", port->sfp_name);
	}
	
	/* Open link LED brightness file */
	snprintf(path, sizeof(path), "/sys/class/leds/%s/brightness", port->link_led);
	port->link_led_fd = open_file_wo(path);
	
	/* Open activity LED brightness file */
	snprintf(path, sizeof(path), "/sys/class/leds/%s/brightness", port->activity_led);
	port->activity_led_fd = open_file_wo(path);
	
	if (port->carrier_fd < 0 || port->link_led_fd < 0 || port->activity_led_fd < 0) {
		syslog(LOG_ERR, "Failed to setup port %s", port->netdev);
		return -1;
	}
	
	/* Read initial module presence state */
	port->last_module_present = read_module_present(port->mod_present_fd);
	port->last_carrier_state = false;
	
	/* Turn off LEDs initially */
	set_led_brightness(port->link_led_fd, 0);
	set_led_brightness(port->activity_led_fd, 0);
	
	syslog(LOG_INFO, "Setup port %s (link=%s, activity=%s, sfp=%s, module_present=%d)", 
	       port->netdev, port->link_led, port->activity_led, port->sfp_name,
	       port->last_module_present);
	
	/* Only setup netdev trigger if module is present */
	if (port->last_module_present) {
		setup_netdev_trigger(port);
	}
	
	return 0;
}

static void cleanup_port(struct sfp_port *port)
{
	/* Turn off LEDs */
	set_led_brightness(port->link_led_fd, 0);
	set_led_brightness(port->activity_led_fd, 0);
	
	if (port->carrier_fd >= 0) {
		close(port->carrier_fd);
		port->carrier_fd = -1;
	}
	if (port->link_led_fd >= 0) {
		close(port->link_led_fd);
		port->link_led_fd = -1;
	}
	if (port->activity_led_fd >= 0) {
		close(port->activity_led_fd);
		port->activity_led_fd = -1;
	}
	if (port->mod_present_fd >= 0) {
		close(port->mod_present_fd);
		port->mod_present_fd = -1;
	}
}

static void setup_netdev_trigger(struct sfp_port *port)
{
	char path[256];
	int fd;
	char trigger_config[256];
	int len;
	
	/* Setup netdev trigger for activity LED */
	snprintf(path, sizeof(path), "/sys/class/leds/%s/trigger", port->activity_led);
	fd = open(path, O_WRONLY);
	if (fd < 0) {
		syslog(LOG_WARNING, "Failed to open %s: %s", path, strerror(errno));
		return;
	}
	
	write(fd, "netdev", 6);
	close(fd);
	
	/* Set the device name */
	snprintf(path, sizeof(path), "/sys/class/leds/%s/device_name", port->activity_led);
	fd = open(path, O_WRONLY);
	if (fd >= 0) {
		len = strlen(port->netdev);
		write(fd, port->netdev, len);
		close(fd);
	}
	
	/* Enable tx and rx monitoring (not link, so LED is off when idle) */
	snprintf(path, sizeof(path), "/sys/class/leds/%s/tx", port->activity_led);
	fd = open(path, O_WRONLY);
	if (fd >= 0) {
		write(fd, "1", 1);
		close(fd);
	}
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/rx", port->activity_led);
	fd = open(path, O_WRONLY);
	if (fd >= 0) {
		write(fd, "1", 1);
		close(fd);
	}
	
	syslog(LOG_INFO, "Setup netdev trigger for %s activity LED", port->netdev);
}

static void update_port(struct sfp_port *port)
{
	bool carrier_state = read_carrier_state(port->carrier_fd);
	bool module_present = read_module_present(port->mod_present_fd);
	
	/* Check for module presence change */
	if (module_present != port->last_module_present) {
		if (!module_present) {
			/* Module removed - disable netdev trigger and turn off both LEDs */
			char path[256];
			int fd;
			
			/* Disable netdev trigger by setting to "none" */
			snprintf(path, sizeof(path), "/sys/class/leds/%s/trigger", port->activity_led);
			fd = open(path, O_WRONLY);
			if (fd >= 0) {
				write(fd, "none", 4);
				close(fd);
			}
			
			/* Turn off both LEDs */
			set_led_brightness(port->link_led_fd, 0);
			set_led_brightness(port->activity_led_fd, 0);
			syslog(LOG_INFO, "%s: SFP module removed", port->netdev);
		} else {
			/* Module inserted - re-setup netdev trigger for activity LED */
			syslog(LOG_INFO, "%s: SFP module inserted", port->netdev);
			setup_netdev_trigger(port);
		}
		port->last_module_present = module_present;
		port->last_carrier_state = false; /* Reset carrier state */
	}
	
	/* Only update carrier-based LEDs if module is present */
	if (module_present && (carrier_state != port->last_carrier_state)) {
		set_led_brightness(port->link_led_fd, carrier_state ? 255 : 0);
		
		syslog(LOG_INFO, "%s: carrier %s", port->netdev, 
		       carrier_state ? "UP" : "DOWN");
		
		port->last_carrier_state = carrier_state;
	}
	
	/* Keep LEDs off if module is not present */
	if (!module_present) {
		set_led_brightness(port->link_led_fd, 0);
		set_led_brightness(port->activity_led_fd, 0);
	}
}

static void daemonize(void)
{
	pid_t pid;
	
	/* Fork off the parent process */
	pid = fork();
	if (pid < 0)
		exit(EXIT_FAILURE);
	
	/* Exit parent process */
	if (pid > 0)
		exit(EXIT_SUCCESS);
	
	/* On success: The child process becomes session leader */
	if (setsid() < 0)
		exit(EXIT_FAILURE);
	
	/* Fork off for the second time */
	pid = fork();
	if (pid < 0)
		exit(EXIT_FAILURE);
	
	/* Exit parent process */
	if (pid > 0)
		exit(EXIT_SUCCESS);
	
	/* Set new file permissions */
	umask(0);
	
	/* Change the working directory to root */
	chdir("/");
	
	/* Close all open file descriptors */
	for (int fd = sysconf(_SC_OPEN_MAX); fd >= 0; fd--)
		close(fd);
}

int main(int argc, char *argv[])
{
	int i;
	bool daemon_mode = true;
	
	/* Check for -f (foreground) flag */
	if (argc > 1 && strcmp(argv[1], "-f") == 0)
		daemon_mode = false;
	
	/* Setup syslog */
	openlog("sfp-led-daemon", LOG_PID | (daemon_mode ? 0 : LOG_PERROR), LOG_DAEMON);
	
	if (daemon_mode) {
		syslog(LOG_INFO, "Starting SFP LED daemon");
		daemonize();
	} else {
		syslog(LOG_INFO, "Starting SFP LED daemon in foreground mode");
	}
	
	/* Setup signal handlers */
	signal(SIGTERM, signal_handler);
	signal(SIGINT, signal_handler);
	
	/* Setup all ports */
	for (i = 0; i < MAX_PORTS; i++) {
		if (setup_port(&ports[i]) < 0) {
			syslog(LOG_WARNING, "Failed to setup port %d, continuing anyway", i);
		} else {
			setup_netdev_trigger(&ports[i]);
		}
	}
	
	syslog(LOG_INFO, "SFP LED daemon running");
	
	/* Main loop */
	while (running) {
		for (i = 0; i < MAX_PORTS; i++) {
			update_port(&ports[i]);
		}
		
		usleep(POLL_INTERVAL_MS * 1000);
	}
	
	syslog(LOG_INFO, "SFP LED daemon shutting down");
	
	/* Cleanup */
	for (i = 0; i < MAX_PORTS; i++) {
		cleanup_port(&ports[i]);
	}
	
	closelog();
	
	return EXIT_SUCCESS;
}