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
#include <sys/inotify.h>
#include <sys/select.h>
#include <syslog.h>
#include <signal.h>
#include <errno.h>
#include <stdbool.h>
#include <dirent.h>
#include <limits.h>
#include <sys/resource.h>

#define MAX_PORTS 2
#define MAX_NETDEV_NAME 32
#define LED_OFF 0
#define LED_MAX 255
#define INOTIFY_BUF_LEN (10 * (sizeof(struct inotify_event) + NAME_MAX + 1))

/* Buffer sizes */
#define DEBUGFS_STATE_BUF_SIZE 512
#define BRIGHTNESS_BUF_SIZE 4
#define CARRIER_BUF_SIZE 4

/* Timing values */
#define POLL_INTERVAL_SEC 1
#define POLL_INTERVAL_USEC 0

/* String prefixes and their lengths */
#define MODDEF0_PREFIX "moddef0:"
#define MODDEF0_PREFIX_LEN 8
#define RX_LOS_PREFIX "rx_los:"
#define RX_LOS_PREFIX_LEN 7
#define FMAN_PREFIX "fman@"
#define FMAN_PREFIX_LEN 5
#define ETHERNET_PREFIX "ethernet@"
#define ETHERNET_PREFIX_LEN 9

struct sfp_port {
	char netdev[MAX_NETDEV_NAME];  /* Dynamically discovered */
	const char *link_led;
	const char *activity_led;
	const char *sfp_name;  /* e.g., "sfp-xfi0" */
	int carrier_fd;
	int link_led_fd;
	int activity_led_fd;
	int mod_present_fd;  /* Debugfs state file for module presence detection */
	int inotify_wd;      /* inotify watch descriptor for carrier file */
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
		.inotify_wd = -1,
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
		.inotify_wd = -1,
		.last_carrier_state = false,
		.last_module_present = false,
	},
};

static volatile sig_atomic_t running = 1;
static int inotify_fd = -1;

static void setup_netdev_trigger(struct sfp_port *port);
static void disable_netdev_trigger(struct sfp_port *port);
static void cleanup_port(struct sfp_port *port);
static int find_netdev_for_sfp(const char *sfp_name, char *netdev_out, size_t out_size);

static void signal_handler(int sig)
{
	running = 0;
}

static int set_led_brightness(int fd, int brightness)
{
	char buf[BRIGHTNESS_BUF_SIZE];
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
	char buf[CARRIER_BUF_SIZE];
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
	char buf[DEBUGFS_STATE_BUF_SIZE];
	int ret;
	char *pos, *line_end;
	
	if (fd < 0)
		return 0; /* Assume NOT present if we can't read */
	
	if (lseek(fd, 0, SEEK_SET) < 0)
		return 0;
	
	ret = read(fd, buf, sizeof(buf) - 1);
	if (ret <= 0)
		return 0;
	
	buf[ret] = '\0';
	
	pos = buf;
	while (pos && *pos) {
		line_end = strchr(pos, '\n');
		if (line_end)
			*line_end = '\0';
		
		/* Check if this line contains our prefix */
		if (strncmp(pos, MODDEF0_PREFIX, MODDEF0_PREFIX_LEN) == 0) {
			char *value = pos + MODDEF0_PREFIX_LEN;
			while (*value == ' ') value++;
			return (*value == '1');
		}
		
		if (line_end)
			pos = line_end + 1;
		else
			break;
	}
	
	return 0;
}

static int read_rx_los(int fd)
{
	char buf[DEBUGFS_STATE_BUF_SIZE];
	int ret;
	char *pos, *line_end;
	
	if (fd < 0)
		return 1; /* Assume signal loss if we can't read */
	
	if (lseek(fd, 0, SEEK_SET) < 0)
		return 1;
	
	ret = read(fd, buf, sizeof(buf) - 1);
	if (ret <= 0)
		return 1;
	
	buf[ret] = '\0';
	
	pos = buf;
	while (pos && *pos) {
		line_end = strchr(pos, '\n');
		if (line_end)
			*line_end = '\0';
		
		if (strncmp(pos, RX_LOS_PREFIX, RX_LOS_PREFIX_LEN) == 0) {
			char *value = pos + RX_LOS_PREFIX_LEN;
			while (*value == ' ') value++;
			return (*value == '1');
		}
		
		if (line_end)
			pos = line_end + 1;
		else
			break;
	}
	
	return 1;
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

static int write_sysfs_string(const char *path, const char *value)
{
	int fd, ret, len;
	
	fd = open(path, O_WRONLY);
	if (fd < 0) {
		syslog(LOG_WARNING, "Failed to open %s: %s", path, strerror(errno));
		return -1;
	}
	
	len = strlen(value);
	ret = write(fd, value, len);
	close(fd);
	
	if (ret != len) {
		syslog(LOG_WARNING, "Failed to write to %s: %s", path, strerror(errno));
		return -1;
	}
	
	return 0;
}

static uint32_t read_dt_u32(const char *path)
{
	int fd;
	uint32_t val;
	ssize_t bytes_read;
	
	fd = open(path, O_RDONLY);
	if (fd < 0)
		return 0;
	
	bytes_read = read(fd, &val, sizeof(val));
	close(fd);
	
	if (bytes_read != sizeof(val)) {
		return 0;
	}
	
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
		if (strncmp(entry->d_name, FMAN_PREFIX, FMAN_PREFIX_LEN) != 0)
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
			if (strncmp(eth_entry->d_name, ETHERNET_PREFIX, ETHERNET_PREFIX_LEN) != 0)
				continue;
			
			/* Check if this ethernet node has an sfp property */
			snprintf(path, sizeof(path), "%s/%s/sfp", fman_path, eth_entry->d_name);
			uint32_t eth_sfp_phandle = read_dt_u32(path);
			
			if (eth_sfp_phandle == sfp_phandle) {
				/* Found matching ethernet node! Read its cell-index */
				uint32_t cell_index;
				
				snprintf(path, sizeof(path), "%s/%s/cell-index", fman_path, eth_entry->d_name);
				cell_index = read_dt_u32(path);
				
				/* Validate cell_index to prevent overflow and ensure reasonable value */
				if (cell_index > 1000) {
					syslog(LOG_WARNING, "Invalid cell-index %u for SFP '%s', skipping", 
					       cell_index, sfp_name);
					continue;
				}
				
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
	char path[PATH_MAX];
	int ret;
	
	/* Discover the network device name for this SFP from device tree */
	if (find_netdev_for_sfp(port->sfp_name, port->netdev, sizeof(port->netdev)) < 0) {
		syslog(LOG_ERR, "Failed to find network device for %s", port->sfp_name);
		return -1;
	}
	
	/* Open carrier file */
	ret = snprintf(path, sizeof(path), "/sys/class/net/%s/carrier", port->netdev);
	if (ret >= sizeof(path)) {
		syslog(LOG_ERR, "Path too long for carrier file: %s", port->netdev);
		return -1;
	}
	port->carrier_fd = open_file_ro(path);
	
	/* Setup inotify watch on carrier file */
	if (inotify_fd >= 0 && port->carrier_fd >= 0) {
		/* Watch the parent directory since carrier file gets replaced */
		ret = snprintf(path, sizeof(path), "/sys/class/net/%s", port->netdev);
		if (ret >= sizeof(path)) {
			syslog(LOG_ERR, "Path too long for netdev: %s", port->netdev);
			goto cleanup;
		}
		port->inotify_wd = inotify_add_watch(inotify_fd, path, IN_MODIFY | IN_ATTRIB);
		if (port->inotify_wd < 0) {
			syslog(LOG_WARNING, "Failed to add inotify watch for %s: %s", 
			       path, strerror(errno));
		}
	}
	
	ret = snprintf(path, sizeof(path), "/sys/kernel/debug/%s/state", port->sfp_name);
	if (ret >= sizeof(path)) {
		syslog(LOG_ERR, "Path too long for debugfs: %s", port->sfp_name);
		goto cleanup;
	}
	port->mod_present_fd = open_file_ro(path);
	if (port->mod_present_fd < 0) {
		syslog(LOG_WARNING, "Cannot open %s debugfs, module detection disabled", port->sfp_name);
	}
	
	/* Open link LED brightness file */
	ret = snprintf(path, sizeof(path), "/sys/class/leds/%s/brightness", port->link_led);
	if (ret >= sizeof(path)) {
		syslog(LOG_ERR, "Path too long for link LED: %s", port->link_led);
		goto cleanup;
	}
	port->link_led_fd = open_file_wo(path);
	
	/* Open activity LED brightness file */
	ret = snprintf(path, sizeof(path), "/sys/class/leds/%s/brightness", port->activity_led);
	if (ret >= sizeof(path)) {
		syslog(LOG_ERR, "Path too long for activity LED: %s", port->activity_led);
		goto cleanup;
	}
	port->activity_led_fd = open_file_wo(path);
	
	if (port->carrier_fd < 0 || port->link_led_fd < 0 || port->activity_led_fd < 0) {
		syslog(LOG_ERR, "Failed to setup port %s", port->netdev);
		goto cleanup;
	}
	
	/* Read initial module presence state */
	port->last_module_present = read_module_present(port->mod_present_fd);
	port->last_carrier_state = false;
	
	set_led_brightness(port->link_led_fd, LED_OFF);
	set_led_brightness(port->activity_led_fd, LED_OFF);
	
	syslog(LOG_INFO, "Setup port %s (link=%s, activity=%s, sfp=%s, module_present=%d)", 
	       port->netdev, port->link_led, port->activity_led, port->sfp_name,
	       port->last_module_present);
	
	/* Setup appropriate LED state based on module presence and carrier */
	if (port->last_module_present) {
		bool rx_los = read_rx_los(port->mod_present_fd);
		if (!rx_los) {
			/* Module present with optical signal - setup netdev trigger for activity LED */
			setup_netdev_trigger(port);
			set_led_brightness(port->link_led_fd, LED_MAX);
		} else {
			/* Module present but no optical signal - activity LED solid ON */
			set_led_brightness(port->activity_led_fd, LED_MAX);
		}
	}
	
	return 0;

cleanup:
	cleanup_port(port);
	return -1;
}

static void cleanup_port(struct sfp_port *port)
{
	if (inotify_fd >= 0 && port->inotify_wd >= 0) {
		inotify_rm_watch(inotify_fd, port->inotify_wd);
		port->inotify_wd = -1;
	}
	
	set_led_brightness(port->link_led_fd, LED_OFF);
	set_led_brightness(port->activity_led_fd, LED_OFF);
	
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
	char path[PATH_MAX];
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/trigger", port->activity_led);
	if (write_sysfs_string(path, "netdev") < 0) {
		return;
	}
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/device_name", port->activity_led);
	if (write_sysfs_string(path, port->netdev) < 0) {
		syslog(LOG_WARNING, "Failed to set device_name for %s activity LED", port->netdev);
		return;
	}
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/tx", port->activity_led);
	if (write_sysfs_string(path, "1") < 0) {
		syslog(LOG_WARNING, "Failed to enable tx monitoring for %s activity LED", port->netdev);
	}
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/rx", port->activity_led);
	if (write_sysfs_string(path, "1") < 0) {
		syslog(LOG_WARNING, "Failed to enable rx monitoring for %s activity LED", port->netdev);
	}
	
	syslog(LOG_INFO, "Setup netdev trigger for %s activity LED", port->netdev);
}

static void disable_netdev_trigger(struct sfp_port *port)
{
	char path[PATH_MAX];
	
	snprintf(path, sizeof(path), "/sys/class/leds/%s/trigger", port->activity_led);
	write_sysfs_string(path, "none");
}

static void update_port(struct sfp_port *port)
{
	bool module_present = read_module_present(port->mod_present_fd);
	bool rx_los = read_rx_los(port->mod_present_fd);
	bool has_signal = module_present && !rx_los;
	
	/* Check for module presence change */
	if (module_present != port->last_module_present) {
		if (!module_present) {
			/* Module removed - disable netdev trigger and turn off both LEDs */
			disable_netdev_trigger(port);
			set_led_brightness(port->link_led_fd, LED_OFF);
			set_led_brightness(port->activity_led_fd, LED_OFF);
			syslog(LOG_INFO, "%s: SFP module removed", port->netdev);
		} else {
			/* Module inserted */
			syslog(LOG_INFO, "%s: SFP module inserted", port->netdev);
			
			if (!rx_los) {
				setup_netdev_trigger(port);
				set_led_brightness(port->link_led_fd, LED_MAX);
			} else {
				set_led_brightness(port->activity_led_fd, LED_MAX);
			}
		}
		port->last_module_present = module_present;
		port->last_carrier_state = has_signal;
	}
	
	/* Handle optical signal changes when module is present */
	if (module_present && (has_signal != port->last_carrier_state)) {
		if (has_signal) {
			/* Optical signal detected - enable netdev trigger and turn on link LED */
			set_led_brightness(port->link_led_fd, LED_MAX);
			disable_netdev_trigger(port);
			set_led_brightness(port->activity_led_fd, LED_OFF);
			setup_netdev_trigger(port);
			
			syslog(LOG_INFO, "%s: optical link UP", port->netdev);
		} else {
			/* Optical signal lost - disable netdev trigger, turn off link LED, activity LED solid ON */
			disable_netdev_trigger(port);
			set_led_brightness(port->link_led_fd, LED_OFF);
			set_led_brightness(port->activity_led_fd, LED_MAX);
			syslog(LOG_INFO, "%s: optical link DOWN", port->netdev);
		}
		
		port->last_carrier_state = has_signal;
	}
	
	if (!module_present) {
		set_led_brightness(port->link_led_fd, LED_OFF);
		set_led_brightness(port->activity_led_fd, LED_OFF);
	}
}

static void daemonize(void)
{
	pid_t pid;
	int fd;
	struct rlimit rlim;

	pid = fork();
	if (pid < 0)
		exit(EXIT_FAILURE);
	
	if (pid > 0)
		exit(EXIT_SUCCESS);
	
	if (setsid() < 0)
		exit(EXIT_FAILURE);
	
	pid = fork();
	if (pid < 0)
		exit(EXIT_FAILURE);
	
	if (pid > 0)
		exit(EXIT_SUCCESS);
	
	umask(0);
	
	chdir("/");
	
	if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
		int max_fd = (rlim.rlim_cur != RLIM_INFINITY) ? rlim.rlim_cur : 1024;
		for (fd = 0; fd < max_fd; fd++)
			close(fd);
	} else {
		for (fd = 0; fd < 256; fd++)
			close(fd);
	}
	
	/* Redirect stdin, stdout, stderr to /dev/null */
	fd = open("/dev/null", O_RDWR);
	if (fd >= 0) {
		dup2(fd, STDIN_FILENO);
		dup2(fd, STDOUT_FILENO);
		dup2(fd, STDERR_FILENO);
		if (fd > STDERR_FILENO)
			close(fd);
	}
}

int main(int argc, char *argv[])
{
	int i;
	bool daemon_mode = true;
	fd_set readfds;
	struct timespec timeout;
	char inotify_buf[INOTIFY_BUF_LEN];
	int max_fd;
	sigset_t sigmask, orig_sigmask;
	struct sigaction sa;
	
	/* Check for -f (foreground) flag */
	if (argc > 1 && strcmp(argv[1], "-f") == 0)
		daemon_mode = false;
	
	if (daemon_mode) {
		daemonize();
		openlog("sfp-led-daemon", LOG_PID, LOG_DAEMON);
		syslog(LOG_INFO, "Starting SFP LED daemon");
	} else {
		openlog("sfp-led-daemon", LOG_PID | LOG_PERROR, LOG_DAEMON);
		syslog(LOG_INFO, "Starting SFP LED daemon in foreground mode");
	}
	
	memset(&sa, 0, sizeof(sa));
	sa.sa_handler = signal_handler;
	sigemptyset(&sa.sa_mask);
	sa.sa_flags = 0;
	
	if (sigaction(SIGTERM, &sa, NULL) < 0) {
		syslog(LOG_ERR, "Failed to setup SIGTERM handler: %s", strerror(errno));
		exit(EXIT_FAILURE);
	}
	if (sigaction(SIGINT, &sa, NULL) < 0) {
		syslog(LOG_ERR, "Failed to setup SIGINT handler: %s", strerror(errno));
		exit(EXIT_FAILURE);
	}
	
	/* Block signals during normal operation - pselect will unblock them atomically */
	sigemptyset(&sigmask);
	sigaddset(&sigmask, SIGTERM);
	sigaddset(&sigmask, SIGINT);
	if (sigprocmask(SIG_BLOCK, &sigmask, &orig_sigmask) < 0) {
		syslog(LOG_ERR, "Failed to block signals: %s", strerror(errno));
		exit(EXIT_FAILURE);
	}
	
	inotify_fd = inotify_init1(IN_NONBLOCK);
	if (inotify_fd < 0) {
		syslog(LOG_ERR, "Failed to initialize inotify: %s", strerror(errno));
	}
	
	/* Setup all ports */
	for (i = 0; i < MAX_PORTS; i++) {
		if (setup_port(&ports[i]) < 0) {
			syslog(LOG_WARNING, "Failed to setup port %d, continuing anyway", i);
		}
	}
	
	syslog(LOG_INFO, "SFP LED daemon running");
	
	/* Main event loop */
	while (running) {
		FD_ZERO(&readfds);
		max_fd = -1;
		
		/* Add inotify fd to the set if available */
		if (inotify_fd >= 0) {
			FD_SET(inotify_fd, &readfds);
			max_fd = inotify_fd;
		}
		
		timeout.tv_sec = POLL_INTERVAL_SEC;
		timeout.tv_nsec = POLL_INTERVAL_USEC * 1000;
		
		int ret = pselect(max_fd + 1, &readfds, NULL, NULL, &timeout, &orig_sigmask);
		
		if (ret < 0) {
			if (errno == EINTR)
				continue;
			syslog(LOG_ERR, "pselect() failed: %s", strerror(errno));
			break;
		}
		
		/* Check if inotify has events */
		if (ret > 0 && inotify_fd >= 0 && FD_ISSET(inotify_fd, &readfds)) {
			int len = read(inotify_fd, inotify_buf, sizeof(inotify_buf));
			if (len > 0) {
				for (i = 0; i < MAX_PORTS; i++) {
					update_port(&ports[i]);
				}
			}
		}
		
		/* Periodic check for module presence (debugfs doesn't support inotify) */
		if (ret == 0) {
			for (i = 0; i < MAX_PORTS; i++) {
				update_port(&ports[i]);
			}
		}
	}
	
	syslog(LOG_INFO, "SFP LED daemon shutting down");
	
	for (i = 0; i < MAX_PORTS; i++) {
		cleanup_port(&ports[i]);
	}
	
	if (inotify_fd >= 0) {
		close(inotify_fd);
	}
	
	closelog();
	
	return EXIT_SUCCESS;
}