/*
 * SFP LED Control VPP Plugin
 * 
 * Monitors VPP/DPDK interface link state and controls SFP+ port LEDs accordingly.
 * 
 * Copyright 2025 Mono Technologies Inc.
 * Author: Tomaz Zaman <tomaz@mono.si>
 */

#include <vnet/vnet.h>
#include <vnet/plugin/plugin.h>
#include <vnet/interface.h>
#include <vppinfra/error.h>
#include <vppinfra/hash.h>

#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <signal.h>

#define LED_OFF 0
#define LED_MAX 255
#define POLL_INTERVAL_SEC 0.05

typedef struct {
    u8 *vpp_interface_name;
    u8 *linux_interface_name;
    u8 *link_led_path;
    u8 *activity_led_path;
    u8 *sfp_debug_path;
    
    u32 sw_if_index;
    int link_led_fd;
    int activity_led_fd;
    int sfp_debug_fd;
    u8 last_link_state;
    u8 last_module_present;
    u64 last_rx_packets;
    u64 last_tx_packets;
    u8 activity_led_state;
    u8 activity_blink_countdown;
    u8 skip_activity_monitoring;
} sfp_led_port_t;

typedef struct {
    sfp_led_port_t *ports;
    vnet_main_t *vnet_main;
    u32 process_node_index;
    u8 initialized;
} sfp_led_main_t;

sfp_led_main_t sfp_led_main;

/* Cleanup function to turn off all LEDs */
static void
sfp_led_cleanup(void)
{
    sfp_led_main_t *slm = &sfp_led_main;
    
    sfp_led_port_t *port;
    vec_foreach(port, slm->ports) {
        if (port->link_led_fd >= 0) {
            int fd = port->link_led_fd;
            char buf[8];
            int len = snprintf(buf, sizeof(buf), "%d\n", LED_OFF);
            if (lseek(fd, 0, SEEK_SET) >= 0) {
                write(fd, buf, len);
            }
            close(fd);
        }
        if (port->activity_led_fd >= 0) {
            int fd = port->activity_led_fd;
            char buf[8];
            int len = snprintf(buf, sizeof(buf), "%d\n", LED_OFF);
            if (lseek(fd, 0, SEEK_SET) >= 0) {
                write(fd, buf, len);
            }
            close(fd);
        }
        if (port->sfp_debug_fd >= 0) {
            close(port->sfp_debug_fd);
        }
    }
}

/* Signal handler to cleanup on termination */
static void
sfp_led_signal_handler(int signum)
{
    sfp_led_cleanup();
    signal(signum, SIG_DFL);
    raise(signum);
}

static void setup_netdev_trigger(sfp_led_port_t *port);
static clib_error_t *setup_sfp_port(sfp_led_port_t *port, vnet_main_t *vnm);

static int
set_led_brightness(int fd, int brightness)
{
    char buf[8];
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

static int
read_module_present(int fd)
{
    char buf[512];
    int ret;
    char *line, *saveptr;
    
    if (fd < 0)
        return 0;
    
    if (lseek(fd, 0, SEEK_SET) < 0)
        return 0;
    
    ret = read(fd, buf, sizeof(buf) - 1);
    if (ret <= 0)
        return 0;
    
    buf[ret] = '\0';
    
    line = strtok_r(buf, "\n", &saveptr);
    while (line != NULL) {
        if (strncmp(line, "moddef0:", 8) == 0) {
            char *value = line + 8;
            while (*value == ' ') value++;
            return (*value == '1');
        }
        line = strtok_r(NULL, "\n", &saveptr);
    }
    
    return 0;
}

static void
setup_netdev_trigger(sfp_led_port_t *port)
{
    char path[256];
    int fd;
    int len;
    
    if (!port->linux_interface_name || !port->activity_led_path)
        return;
    
    char *led_name = strrchr((char *)port->activity_led_path, '/');
    if (!led_name || strncmp(led_name, "/brightness", 11) != 0)
        return;
    
    len = led_name - (char *)port->activity_led_path;
    char led_base[256];
    snprintf(led_base, sizeof(led_base), "%.*s", len, (char *)port->activity_led_path);
    
    snprintf(path, sizeof(path), "%s/trigger", led_base);
    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        write(fd, "netdev", 6);
        close(fd);
    }
    
    snprintf(path, sizeof(path), "%s/device_name", led_base);
    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        len = vec_len(port->linux_interface_name);
        write(fd, port->linux_interface_name, len);
        close(fd);
    }
    
    snprintf(path, sizeof(path), "%s/tx", led_base);
    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        write(fd, "1", 1);
        close(fd);
    }
    
    snprintf(path, sizeof(path), "%s/rx", led_base);
    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        write(fd, "1", 1);
        close(fd);
    }
    
    clib_warning("Setup netdev trigger for %v activity LED on %v",
                 port->vpp_interface_name, port->linux_interface_name);
}

static void
disable_netdev_trigger(sfp_led_port_t *port)
{
    char path[256];
    int fd;
    
    if (!port->activity_led_path)
        return;
    
    char *led_name = strrchr((char *)port->activity_led_path, '/');
    if (!led_name || strncmp(led_name, "/brightness", 11) != 0)
        return;
    
    int len = led_name - (char *)port->activity_led_path;
    snprintf(path, sizeof(path), "%.*s/trigger", len, (char *)port->activity_led_path);
    
    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        write(fd, "none", 4);
        close(fd);
    }
}

static clib_error_t *
sfp_led_link_change(vnet_main_t *vnm, u32 hw_if_index, u32 flags)
{
    sfp_led_main_t *slm = &sfp_led_main;
    vnet_hw_interface_t *hi = vnet_get_hw_interface(vnm, hw_if_index);
    u32 sw_if_index = hi->sw_if_index;
    
    if (!slm->initialized && vec_len(slm->ports) > 0) {
        slm->initialized = 1;
        
        sfp_led_port_t *p;
        vec_foreach(p, slm->ports) {
            clib_error_t *error = setup_sfp_port(p, vnm);
            if (error) {
                clib_warning("Failed to setup SFP LED port: %s", error->what);
                clib_error_free(error);
            }
        }
    }
    
    sfp_led_port_t *port;
    vec_foreach(port, slm->ports) {
        if (port->sw_if_index == sw_if_index) {
            u8 link_up = (flags & VNET_HW_INTERFACE_FLAG_LINK_UP) != 0;
            u8 module_present = read_module_present(port->sfp_debug_fd);
            
            vnet_sw_interface_t *si = vnet_get_sw_interface(vnm, sw_if_index);
            u8 admin_up = (si->flags & VNET_SW_INTERFACE_FLAG_ADMIN_UP) != 0;
            
            if (module_present && admin_up && link_up) {
                set_led_brightness(port->link_led_fd, LED_MAX);
                clib_warning("%v: link up", port->vpp_interface_name);
            } else {
                set_led_brightness(port->link_led_fd, LED_OFF);
                if (module_present) {
                    clib_warning("%v: link down", port->vpp_interface_name);
                }
            }
            
            port->last_link_state = link_up;
            break;
        }
    }
    
    return 0;
}

VNET_HW_INTERFACE_LINK_UP_DOWN_FUNCTION(sfp_led_link_change);

static clib_error_t *
sfp_led_admin_change(vnet_main_t *vnm, u32 sw_if_index, u32 flags)
{
    sfp_led_main_t *slm = &sfp_led_main;
    
    if (!slm->initialized) {
        return 0;
    }
    
    sfp_led_port_t *port;
    vec_foreach(port, slm->ports) {
        if (port->sw_if_index == sw_if_index) {
            u8 module_present = read_module_present(port->sfp_debug_fd);
            u8 admin_up = (flags & VNET_SW_INTERFACE_FLAG_ADMIN_UP) != 0;
            
            vnet_hw_interface_t *hi = vnet_get_sup_hw_interface(vnm, sw_if_index);
            u8 link_up = (hi->flags & VNET_HW_INTERFACE_FLAG_LINK_UP) != 0;
            
            if (module_present && admin_up && link_up) {
                set_led_brightness(port->link_led_fd, LED_MAX);
            } else {
                set_led_brightness(port->link_led_fd, LED_OFF);
            }
            
            break;
        }
    }
    
    return 0;
}

VNET_SW_INTERFACE_ADMIN_UP_DOWN_FUNCTION(sfp_led_admin_change);

static uword
sfp_led_process(vlib_main_t *vm, vlib_node_runtime_t *rt, vlib_frame_t *f)
{
    sfp_led_main_t *slm = &sfp_led_main;
    u32 poll_count = 0;
    
    while (1) {
        vlib_process_wait_for_event_or_clock(vm, POLL_INTERVAL_SEC);
        poll_count++;
        
        sfp_led_port_t *port;
        vec_foreach(port, slm->ports) {
            u8 module_present = read_module_present(port->sfp_debug_fd);
            
            if (poll_count % 20 == 0) {
                if (module_present != port->last_module_present) {
                    if (!module_present) {
                        disable_netdev_trigger(port);
                        set_led_brightness(port->link_led_fd, LED_OFF);
                        set_led_brightness(port->activity_led_fd, LED_OFF);
                        port->activity_led_state = 0;
                        clib_warning("%v: SFP module removed", port->vpp_interface_name);
                    } else {
                        clib_warning("%v: SFP module inserted", port->vpp_interface_name);
                        
                        port->last_rx_packets = 0;
                        port->last_tx_packets = 0;
                        port->activity_led_state = 0;
                    }
                    
                    port->last_module_present = module_present;
                }
            }
            
            if (module_present && port->sw_if_index != ~0) {
                vnet_sw_interface_t *si = 
                    vnet_get_sw_interface(slm->vnet_main, port->sw_if_index);
                vnet_hw_interface_t *hi = 
                    vnet_get_sup_hw_interface(slm->vnet_main, port->sw_if_index);
                
                u8 admin_up = (si->flags & VNET_SW_INTERFACE_FLAG_ADMIN_UP) != 0;
                u8 link_up = (hi->flags & VNET_HW_INTERFACE_FLAG_LINK_UP) != 0;
                
                if (admin_up && link_up) {
                    vlib_combined_counter_main_t *rxc = 
                        &slm->vnet_main->interface_main.combined_sw_if_counters[VNET_INTERFACE_COUNTER_RX];
                    vlib_combined_counter_main_t *txc = 
                        &slm->vnet_main->interface_main.combined_sw_if_counters[VNET_INTERFACE_COUNTER_TX];
                    
                    vlib_counter_t rx, tx;
                    vlib_get_combined_counter(rxc, port->sw_if_index, &rx);
                    vlib_get_combined_counter(txc, port->sw_if_index, &tx);
                    
                    u64 rx_packets = rx.packets;
                    u64 tx_packets = tx.packets;
                    
                    if (rx_packets != port->last_rx_packets || tx_packets != port->last_tx_packets) {
                        if (port->activity_led_state) {
                            set_led_brightness(port->activity_led_fd, LED_OFF);
                            port->activity_led_state = 0;
                        } else {
                            set_led_brightness(port->activity_led_fd, LED_MAX);
                            port->activity_led_state = 1;
                        }
                    } else {
                        if (port->activity_led_state) {
                            set_led_brightness(port->activity_led_fd, LED_OFF);
                            port->activity_led_state = 0;
                        }
                    }
                    
                    port->last_rx_packets = rx_packets;
                    port->last_tx_packets = tx_packets;
                } else {
                    set_led_brightness(port->activity_led_fd, LED_MAX);
                    port->activity_led_state = 1;
                    port->last_rx_packets = 0;
                    port->last_tx_packets = 0;
                }
            } else {
                if (port->activity_led_state) {
                    set_led_brightness(port->activity_led_fd, LED_OFF);
                    port->activity_led_state = 0;
                }
            }
        }
    }
    
    return 0;
}

VLIB_REGISTER_NODE(sfp_led_process_node) = {
    .function = sfp_led_process,
    .type = VLIB_NODE_TYPE_PROCESS,
    .name = "sfp-led-process",
};

static clib_error_t *
sfp_led_config(vlib_main_t *vm, unformat_input_t *input)
{
    sfp_led_main_t *slm = &sfp_led_main;
    sfp_led_port_t *port = NULL;
    u8 *interface_name = NULL;
    
    while (unformat_check_input(input) != UNFORMAT_END_OF_INPUT) {
        if (unformat(input, "interface %v", &interface_name)) {
            vec_add2(slm->ports, port, 1);
            memset(port, 0, sizeof(*port));
            port->vpp_interface_name = interface_name;
            port->link_led_fd = -1;
            port->activity_led_fd = -1;
            port->sfp_debug_fd = -1;
            port->sw_if_index = ~0;
            interface_name = NULL;
        }
        else if (port && unformat(input, "linux-interface %v", &port->linux_interface_name))
            ;
        else if (port && unformat(input, "link-led %v", &port->link_led_path))
            ;
        else if (port && unformat(input, "activity-led %v", &port->activity_led_path))
            ;
        else if (port && unformat(input, "sfp-debug %v", &port->sfp_debug_path))
            ;
        else {
            return clib_error_return(0, "unknown input `%U'",
                                   format_unformat_error, input);
        }
    }
    
    return 0;
}

VLIB_CONFIG_FUNCTION(sfp_led_config, "sfp-led");

static clib_error_t *
setup_sfp_port(sfp_led_port_t *port, vnet_main_t *vnm)
{
    char *ifname_cstr = (char *)format(0, "%v%c", port->vpp_interface_name, 0);
    
    u32 sw_if_index = ~0;
    
    unformat_input_t input;
    unformat_init_string(&input, ifname_cstr, strlen(ifname_cstr));
    
    if (!unformat_user(&input, unformat_vnet_sw_interface, vnm, &sw_if_index)) {
        unformat_free(&input);
        vec_free(ifname_cstr);
        return clib_error_return(0, "Interface %v not found",
                                port->vpp_interface_name);
    }
    unformat_free(&input);
    vec_free(ifname_cstr);
    
    port->sw_if_index = sw_if_index;
    
    if (port->link_led_path) {
        char *path = (char *)format(0, "%v%c", port->link_led_path, 0);
        port->link_led_fd = open(path, O_WRONLY);
        vec_free(path);
        if (port->link_led_fd < 0) {
            return clib_error_return(0, "Failed to open %v: %s",
                                   port->link_led_path, strerror(errno));
        }
    }
    
    if (port->activity_led_path) {
        char *path = (char *)format(0, "%v%c", port->activity_led_path, 0);
        port->activity_led_fd = open(path, O_WRONLY);
        vec_free(path);
        if (port->activity_led_fd < 0) {
            return clib_error_return(0, "Failed to open %v: %s",
                                   port->activity_led_path, strerror(errno));
        }
    }
    
    if (port->sfp_debug_path) {
        char *path = (char *)format(0, "%v%c", port->sfp_debug_path, 0);
        port->sfp_debug_fd = open(path, O_RDONLY);
        vec_free(path);
        if (port->sfp_debug_fd < 0) {
            clib_warning("Failed to open %v: %s (module detection disabled)",
                       port->sfp_debug_path, strerror(errno));
        }
    }
    
    port->last_module_present = read_module_present(port->sfp_debug_fd);
    port->last_link_state = 0;
    port->last_rx_packets = 0;
    port->last_tx_packets = 0;
    port->activity_led_state = 0;
    port->activity_blink_countdown = 0;
    port->skip_activity_monitoring = 0;
    
    set_led_brightness(port->link_led_fd, LED_OFF);
    set_led_brightness(port->activity_led_fd, LED_OFF);
    
    clib_warning("Initialized SFP LED control for %v (sw_if_index=%d, module_present=%d)",
                port->vpp_interface_name, port->sw_if_index, port->last_module_present);
    
    return 0;
}

static clib_error_t *
sfp_led_init(vlib_main_t *vm)
{
    sfp_led_main_t *slm = &sfp_led_main;
    vnet_main_t *vnm = vnet_get_main();
    
    slm->vnet_main = vnm;
    slm->process_node_index = sfp_led_process_node.index;
    
    atexit(sfp_led_cleanup);
    signal(SIGTERM, sfp_led_signal_handler);
    signal(SIGINT, sfp_led_signal_handler);
    
    return 0;
}

VLIB_INIT_FUNCTION(sfp_led_init) = {
    .runs_after = VLIB_INITS("dpdk_init"),
};

static clib_error_t *
sfp_led_exit(vlib_main_t *vm)
{
    sfp_led_cleanup();
    return 0;
}

VLIB_MAIN_LOOP_EXIT_FUNCTION(sfp_led_exit);

VLIB_PLUGIN_REGISTER() = {
    .version = "1.2",
    .description = "SFP LED Control for DPDK Interfaces",
};