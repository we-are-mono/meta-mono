// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * SFP LED Control Platform Driver
 *
 * Controls SFP port LEDs based on module presence and optical signal state.
 * Registers with the SFP bus to receive hotplug and link state events.
 *
 * LED behavior:
 *   - Link LED: ON when optical signal detected (carrier up)
 *   - Activity LED: Blinks on tx/rx traffic when link is up
 *                   Solid ON when module present but no link
 *                   OFF when no module inserted
 *
 * Device tree binding example:
 *
 *   // LED definitions (typically under gpio-leds node)
 *   leds {
 *       compatible = "gpio-leds";
 *
 *       led_sfp0_link: sfp0_link {
 *           label = "sfp0:link";
 *           gpios = <&gpio2 17 GPIO_ACTIVE_HIGH>;
 *           default-state = "off";
 *       };
 *
 *       led_sfp0_activity: sfp0_activity {
 *           label = "sfp0:activity";
 *           gpios = <&gpio2 18 GPIO_ACTIVE_HIGH>;
 *           default-state = "off";
 *       };
 *   };
 *
 *   // SFP cage definition - references LEDs via 'leds' property
 *   // Order: first LED = link, second LED = activity
 *   sfp0: sfp-0 {
 *       compatible = "sff,sfp";
 *       i2c-bus = <&sfp0_i2c>;
 *       mod-def0-gpios = <&gpio2 12 GPIO_ACTIVE_LOW>;
 *       los-gpios = <&gpio2 11 GPIO_ACTIVE_HIGH>;
 *       tx-disable-gpios = <&gpio2 14 GPIO_ACTIVE_HIGH>;
 *       leds = <&led_sfp0_link>, <&led_sfp0_activity>;
 *   };
 *
 *   // SFP LED controller - lists all SFP ports to manage
 *   sfp-led-controller {
 *       compatible = "mono,sfp-led";
 *       sfp-ports = <&sfp0>, <&sfp1>;
 *   };
 *
 *   // MAC node must reference SFP for netdev association
 *   &fman_mac {
 *       sfp = <&sfp0>;
 *       managed = "in-band-status";
 *   };
 *
 * Copyright 2026 Mono Technologies Inc.
 * Author: Tomaz Zaman <tomaz@mono.si>
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/of.h>
#include <linux/platform_device.h>
#include <linux/sfp.h>
#include <linux/leds.h>
#include <linux/workqueue.h>
#include <linux/netdevice.h>
#include <linux/rtnetlink.h>

#define DRIVER_NAME "sfp-led"
#define SFP_LED_NAME_SIZE 64

/* Timing constants */
#define SFP_LED_INIT_DELAY_MS		100
#define SFP_LED_RETRY_DELAY_MS		500
#define SFP_LED_ACTIVITY_INTERVAL_MS	50
#define SFP_LED_MAX_INIT_RETRIES	10

struct sfp_led_port {
	struct sfp_led_priv *priv;        /* Back-pointer to parent */
	struct device_node *sfp_np;       /* SFP device node */
	struct sfp_bus *bus;
	struct net_device *netdev;

	struct led_classdev *link_led;
	struct led_classdev *activity_led;

	char link_led_name[SFP_LED_NAME_SIZE];
	char activity_led_name[SFP_LED_NAME_SIZE];

	bool module_present;
	bool has_signal;
	bool init_failed;              /* Set if netdev discovery exhausted retries */

	struct delayed_work init_work;
	int init_retries;

	struct delayed_work activity_work;
	u64 last_tx_packets;
	u64 last_rx_packets;
	bool activity_polling;
	bool activity_led_on;            /* Current state of activity LED */
};

struct sfp_led_priv {
	struct device *dev;
	int num_ports;		/* Array size (not all may be successfully registered) */
	struct sfp_led_port *ports;
};

/*
 * Find the netdev associated with an SFP by traversing device tree.
 *
 * DPAA device tree structure:
 *   fsldpaa/ethernet@8 (fsl,dpa-ethernet) -> fsl,fman-mac = <&enet6>
 *   fman0/ethernet@f0000 (enet6)          -> sfp = <&sfp_xfi0>
 *
 * Returns: net_device with incremented refcount, or NULL if not found.
 *          Caller must call dev_put() when done.
 *
 * Note: Only searches init_net namespace. Network namespace support is not
 * implemented as SFP hardware is typically not namespace-aware.
 */
static struct net_device *sfp_led_find_netdev(struct device_node *sfp_np)
{
	struct net_device *dev, *found = NULL;

	if (!sfp_np)
		return NULL;

	/*
	 * RTNL protects the netdev list and allows sleeping, which is needed
	 * for device tree operations (of_node_put can sleep if refcount hits
	 * zero during cleanup).
	 *
	 * Callers (workqueue handlers, SFP callbacks) run in process context
	 * without RTNL held.
	 */
	rtnl_lock();
	for_each_netdev(&init_net, dev) {
		struct device *parent = dev->dev.parent;
		struct device_node *dpaa_node, *mac_node, *sfp_ref;

		if (!parent || !parent->of_node)
			continue;

		dpaa_node = parent->of_node;

		mac_node = of_parse_phandle(dpaa_node, "fsl,fman-mac", 0);
		if (!mac_node)
			continue;

		sfp_ref = of_parse_phandle(mac_node, "sfp", 0);
		of_node_put(mac_node);

		if (sfp_ref == sfp_np) {
			of_node_put(sfp_ref);
			found = dev;
			dev_hold(found);
			break;
		}
		of_node_put(sfp_ref);
	}
	rtnl_unlock();

	return found;
}

static void sfp_led_set_link(struct sfp_led_port *port, bool on)
{
	if (!port->link_led)
		return;

	led_set_brightness(port->link_led,
			   on ? port->link_led->max_brightness : LED_OFF);
}

static void sfp_led_set_activity_brightness(struct sfp_led_port *port, bool on)
{
	if (!port->activity_led)
		return;

	led_set_brightness(port->activity_led,
			   on ? port->activity_led->max_brightness : LED_OFF);
}

static void sfp_led_activity_work_handler(struct work_struct *work)
{
	struct sfp_led_port *port = container_of(work, struct sfp_led_port,
						 activity_work.work);
	struct rtnl_link_stats64 stats;
	struct net_device *netdev;
	u64 new_tx, new_rx;
	bool had_activity;

	if (!READ_ONCE(port->activity_polling))
		return;

	netdev = READ_ONCE(port->netdev);
	if (!netdev)
		goto reschedule;

	/*
	 * Check if netdev is operational. We hold a reference (dev_hold),
	 * so the device won't be freed, but it may be transitioning state.
	 *
	 * Note: There's an inherent TOCTOU race between this check and
	 * dev_get_stats() - the device state could change. This is acceptable
	 * because:
	 *   1. dev_get_stats() is safe to call regardless of device state
	 *   2. Worst case: one poll cycle (50ms) with stale/zero stats
	 *   3. For LED indication, this transient inaccuracy is invisible
	 */
	if (!netif_running(netdev) || !netif_carrier_ok(netdev))
		goto reschedule;

	dev_get_stats(netdev, &stats);
	new_tx = stats.tx_packets;
	new_rx = stats.rx_packets;

	had_activity = (new_tx != port->last_tx_packets ||
			new_rx != port->last_rx_packets);

	if (had_activity) {
		/*
		 * Toggle LED state on activity. This creates a visible
		 * blink pattern when there's continuous traffic.
		 */
		port->activity_led_on = !port->activity_led_on;
		led_set_brightness(port->activity_led,
				   port->activity_led_on ?
				   port->activity_led->max_brightness : LED_OFF);

		port->last_tx_packets = new_tx;
		port->last_rx_packets = new_rx;
	} else if (port->activity_led_on) {
		/* No activity - ensure LED is off */
		port->activity_led_on = false;
		led_set_brightness(port->activity_led, LED_OFF);
	}

reschedule:
	schedule_delayed_work(&port->activity_work,
			      msecs_to_jiffies(SFP_LED_ACTIVITY_INTERVAL_MS));
}

static void sfp_led_start_activity_polling(struct sfp_led_port *port)
{
	if (!port->activity_led || !READ_ONCE(port->netdev))
		return;

	/*
	 * If user has configured an LED trigger (e.g., netdev via DT or sysfs),
	 * defer to that trigger for activity indication. Our polling would
	 * conflict with the trigger's control of the LED.
	 *
	 * User can configure the netdev trigger via sysfs:
	 *   echo "eth0" > /sys/class/leds/<led>/device_name
	 *   echo 1 > /sys/class/leds/<led>/tx
	 *   echo 1 > /sys/class/leds/<led>/rx
	 */
	if (port->activity_led->trigger) {
		dev_dbg(port->priv->dev,
			"%s: trigger '%s' active, skipping polling\n",
			port->activity_led_name,
			port->activity_led->trigger->name);
		return;
	}

	port->last_tx_packets = 0;
	port->last_rx_packets = 0;
	port->activity_led_on = false;
	WRITE_ONCE(port->activity_polling, true);
	schedule_delayed_work(&port->activity_work, 0);

	dev_dbg(port->priv->dev, "%s: activity polling started\n",
		port->link_led_name);
}

static void sfp_led_stop_activity_polling(struct sfp_led_port *port)
{
	WRITE_ONCE(port->activity_polling, false);
	cancel_delayed_work_sync(&port->activity_work);

	dev_dbg(port->priv->dev, "%s: activity polling stopped\n",
		port->link_led_name);
}

static void sfp_led_init_work_handler(struct work_struct *work)
{
	struct sfp_led_port *port = container_of(work, struct sfp_led_port,
						 init_work.work);
	struct net_device *netdev;
	bool carrier;

	if (!READ_ONCE(port->module_present))
		return;

	netdev = READ_ONCE(port->netdev);
	if (!netdev) {
		netdev = sfp_led_find_netdev(port->sfp_np);
		if (netdev) {
			if (cmpxchg(&port->netdev, NULL, netdev) != NULL)
				dev_put(netdev);  /* Someone else won the race */
			else
				dev_dbg(port->priv->dev, "%s: found netdev %s\n",
					port->link_led_name, netdev->name);
		}
		netdev = READ_ONCE(port->netdev);
	}

	if (!netdev) {
		/*
		 * Safe to use plain increment: this work handler is the only
		 * writer, and any reset (in module_insert) happens only after
		 * cancel_delayed_work_sync() has ensured we're not running.
		 */
		int retries = ++port->init_retries;

		if (retries >= SFP_LED_MAX_INIT_RETRIES) {
			dev_err(port->priv->dev,
				"%s: netdev not found after %d retries, port non-functional\n",
				port->link_led_name, retries);
			WRITE_ONCE(port->init_failed, true);
			/*
			 * Blink both LEDs to indicate error state.
			 * User should check dmesg for details.
			 */
			sfp_led_set_link(port, true);
			sfp_led_set_activity_brightness(port, true);
			return;
		}
		dev_dbg(port->priv->dev, "%s: netdev not found, retry %d/%d\n",
			port->link_led_name, retries, SFP_LED_MAX_INIT_RETRIES);
		schedule_delayed_work(&port->init_work,
				      msecs_to_jiffies(SFP_LED_RETRY_DELAY_MS));
		return;
	}

	carrier = netif_carrier_ok(netdev);

	if (carrier) {
		WRITE_ONCE(port->has_signal, true);
		dev_dbg(port->priv->dev, "%s: %s has carrier (link up)\n",
			port->link_led_name, netdev->name);

		sfp_led_set_link(port, true);
		sfp_led_start_activity_polling(port);
	} else {
		WRITE_ONCE(port->has_signal, false);
		dev_dbg(port->priv->dev, "%s: %s has no carrier\n",
			port->link_led_name, netdev->name);

		sfp_led_set_link(port, false);

		/* Only set activity LED if no trigger is active */
		if (port->activity_led && !port->activity_led->trigger)
			sfp_led_set_activity_brightness(port, true);
	}
}

/* SFP upstream callbacks - called from SFP state machine workqueue */

static void sfp_led_attach(void *priv, struct sfp_bus *bus) { }
static void sfp_led_detach(void *priv, struct sfp_bus *bus) { }

static int sfp_led_module_insert(void *priv, const struct sfp_eeprom_id *id)
{
	struct sfp_led_port *port = priv;

	WRITE_ONCE(port->module_present, true);
	WRITE_ONCE(port->has_signal, false);
	WRITE_ONCE(port->init_failed, false);
	port->init_retries = 0;  /* Safe: init_work was sync-cancelled in module_remove */

	schedule_delayed_work(&port->init_work,
			      msecs_to_jiffies(SFP_LED_INIT_DELAY_MS));

	dev_dbg(port->priv->dev, "%s: SFP module inserted: %.16s\n",
		port->link_led_name, id->base.vendor_name);

	return 0;
}

static void sfp_led_module_remove(void *priv)
{
	struct sfp_led_port *port = priv;

	might_sleep();

	WRITE_ONCE(port->module_present, false);
	WRITE_ONCE(port->has_signal, false);

	cancel_delayed_work_sync(&port->init_work);
	sfp_led_stop_activity_polling(port);

	sfp_led_set_link(port, false);
	sfp_led_set_activity_brightness(port, false);

	dev_dbg(port->priv->dev, "%s: SFP module removed\n", port->link_led_name);
}

static void sfp_led_link_up(void *priv)
{
	struct sfp_led_port *port = priv;
	struct net_device *netdev;

	might_sleep();

	WRITE_ONCE(port->has_signal, true);

	cancel_delayed_work_sync(&port->init_work);

	/* Ensure netdev is discovered - link_up may arrive before init_work completes */
	netdev = READ_ONCE(port->netdev);
	if (!netdev) {
		netdev = sfp_led_find_netdev(port->sfp_np);
		if (netdev) {
			if (cmpxchg(&port->netdev, NULL, netdev) != NULL)
				dev_put(netdev);  /* Someone else won the race */
			else
				dev_dbg(port->priv->dev, "%s: found netdev %s\n",
					port->link_led_name, netdev->name);
		}
		netdev = READ_ONCE(port->netdev);
	}

	sfp_led_set_link(port, true);
	sfp_led_start_activity_polling(port);

	dev_dbg(port->priv->dev, "%s: SFP link up\n", port->link_led_name);
}

static void sfp_led_link_down(void *priv)
{
	struct sfp_led_port *port = priv;

	might_sleep();

	WRITE_ONCE(port->has_signal, false);

	cancel_delayed_work_sync(&port->init_work);
	sfp_led_stop_activity_polling(port);

	sfp_led_set_link(port, false);

	/*
	 * Only set activity LED state if no trigger is active.
	 * If user has configured a trigger (e.g., netdev), let it handle
	 * the LED state - our direct control would conflict.
	 */
	if (port->activity_led && !port->activity_led->trigger) {
		if (READ_ONCE(port->module_present))
			sfp_led_set_activity_brightness(port, true);
		else
			sfp_led_set_activity_brightness(port, false);
	}

	dev_dbg(port->priv->dev, "%s: SFP link down\n", port->link_led_name);
}

static const struct sfp_upstream_ops sfp_led_upstream_ops = {
	.attach = sfp_led_attach,
	.detach = sfp_led_detach,
	.module_insert = sfp_led_module_insert,
	.module_remove = sfp_led_module_remove,
	.link_up = sfp_led_link_up,
	.link_down = sfp_led_link_down,
};

/*
 * Find the MAC node that references an SFP.
 */
static struct device_node *sfp_led_find_mac_for_sfp(struct device_node *sfp_np)
{
	struct device_node *node;

	for_each_node_with_property(node, "sfp") {
		struct device_node *sfp_ref = of_parse_phandle(node, "sfp", 0);
		bool matches = (sfp_ref == sfp_np);

		of_node_put(sfp_ref);

		if (matches)
			return node;
	}

	return NULL;
}

static int sfp_led_register_port(struct sfp_led_priv *priv,
				 struct device_node *sfp_np, int index)
{
	struct sfp_led_port *port = &priv->ports[index];
	struct device_node *mac_np;
	struct sfp_bus *bus;
	int ret;

	port->priv = priv;

	mac_np = sfp_led_find_mac_for_sfp(sfp_np);
	if (!mac_np) {
		dev_warn(priv->dev, "port %d: no MAC references SFP %pOFn\n",
			 index, sfp_np);
		return -ENODEV;
	}

	INIT_DELAYED_WORK(&port->init_work, sfp_led_init_work_handler);
	INIT_DELAYED_WORK(&port->activity_work, sfp_led_activity_work_handler);

	port->sfp_np = sfp_np;
	of_node_get(sfp_np);

	port->link_led = of_led_get(sfp_np, 0);
	if (IS_ERR(port->link_led)) {
		dev_dbg(priv->dev, "port %d: link LED not in DT: %ld\n",
			index, PTR_ERR(port->link_led));
		port->link_led = NULL;
	}

	port->activity_led = of_led_get(sfp_np, 1);
	if (IS_ERR(port->activity_led)) {
		dev_dbg(priv->dev, "port %d: activity LED not in DT: %ld\n",
			index, PTR_ERR(port->activity_led));
		port->activity_led = NULL;
	}

	if (port->link_led && port->link_led->name)
		strscpy(port->link_led_name, port->link_led->name,
			sizeof(port->link_led_name));
	else
		snprintf(port->link_led_name, sizeof(port->link_led_name),
			 "sfp%d:link", index);

	if (port->activity_led && port->activity_led->name)
		strscpy(port->activity_led_name, port->activity_led->name,
			sizeof(port->activity_led_name));
	else
		snprintf(port->activity_led_name, sizeof(port->activity_led_name),
			 "sfp%d:activity", index);

	bus = sfp_bus_find_fwnode(of_fwnode_handle(mac_np));
	of_node_put(mac_np);

	if (IS_ERR_OR_NULL(bus)) {
		ret = bus ? PTR_ERR(bus) : -ENODEV;
		dev_err(priv->dev, "%s: failed to find SFP bus: %d\n",
			port->link_led_name, ret);
		goto err_put_leds;
	}

	ret = sfp_bus_add_upstream(bus, port, &sfp_led_upstream_ops);
	if (ret) {
		sfp_bus_put(bus);
		dev_err(priv->dev, "%s: failed to register with SFP bus: %d\n",
			port->link_led_name, ret);
		goto err_put_leds;
	}

	port->bus = bus;

	dev_info(priv->dev, "registered port %d: %pOFn (link=%s, activity=%s)\n",
		 index, sfp_np, port->link_led_name, port->activity_led_name);

	return 0;

err_put_leds:
	if (port->activity_led) {
		led_put(port->activity_led);
		port->activity_led = NULL;
	}
	if (port->link_led) {
		led_put(port->link_led);
		port->link_led = NULL;
	}
	of_node_put(sfp_np);
	port->sfp_np = NULL;
	return ret;
}

static void sfp_led_cleanup_port(struct sfp_led_port *port)
{
	/* Skip ports that failed registration (sfp_np is NULL for uninitialized slots) */
	if (!port->sfp_np)
		return;

	/* Unregister first - no more callbacks after this returns */
	if (port->bus) {
		sfp_bus_del_upstream(port->bus);
		sfp_bus_put(port->bus);
		port->bus = NULL;
	}

	/* Flush any work that was already scheduled by callbacks */
	cancel_delayed_work_sync(&port->init_work);
	cancel_delayed_work_sync(&port->activity_work);

	sfp_led_set_link(port, false);
	sfp_led_set_activity_brightness(port, false);

	if (port->activity_led) {
		led_put(port->activity_led);
		port->activity_led = NULL;
	}

	if (port->link_led) {
		led_put(port->link_led);
		port->link_led = NULL;
	}

	if (port->netdev) {
		dev_put(port->netdev);
		port->netdev = NULL;
	}

	of_node_put(port->sfp_np);
	port->sfp_np = NULL;
}

static int sfp_led_probe(struct platform_device *pdev)
{
	struct sfp_led_priv *priv;
	struct device_node *np;
	int count, i, ret, registered = 0;

	priv = devm_kzalloc(&pdev->dev, sizeof(*priv), GFP_KERNEL);
	if (!priv)
		return -ENOMEM;

	priv->dev = &pdev->dev;
	platform_set_drvdata(pdev, priv);

	count = of_count_phandle_with_args(pdev->dev.of_node, "sfp-ports", NULL);
	if (count <= 0) {
		dev_err(&pdev->dev, "no sfp-ports specified\n");
		return -ENODEV;
	}

	priv->ports = devm_kcalloc(&pdev->dev, count,
				   sizeof(*priv->ports), GFP_KERNEL);
	if (!priv->ports)
		return -ENOMEM;

	for (i = 0; i < count; i++) {
		np = of_parse_phandle(pdev->dev.of_node, "sfp-ports", i);
		if (!np) {
			dev_warn(&pdev->dev, "failed to parse sfp-ports[%d]\n", i);
			continue;
		}

		ret = sfp_led_register_port(priv, np, i);
		of_node_put(np);
		if (ret == 0)
			registered++;
	}

	if (registered == 0) {
		dev_err(&pdev->dev, "no SFP ports registered\n");
		return -ENODEV;
	}

	priv->num_ports = count;
	dev_info(&pdev->dev, "loaded (%d ports)\n", registered);
	return 0;
}

static void sfp_led_remove(struct platform_device *pdev)
{
	struct sfp_led_priv *priv = platform_get_drvdata(pdev);
	int i;

	for (i = 0; i < priv->num_ports; i++)
		sfp_led_cleanup_port(&priv->ports[i]);

	dev_info(&pdev->dev, "unloaded\n");
}

static const struct of_device_id sfp_led_of_match[] = {
	{ .compatible = "mono,sfp-led" },
	{ }
};
MODULE_DEVICE_TABLE(of, sfp_led_of_match);

static struct platform_driver sfp_led_driver = {
	.probe = sfp_led_probe,
	.remove = sfp_led_remove,
	.driver = {
		.name = DRIVER_NAME,
		.of_match_table = sfp_led_of_match,
	},
};
module_platform_driver(sfp_led_driver);

MODULE_AUTHOR("Tomaz Zaman <tomaz@mono.si>");
MODULE_DESCRIPTION("SFP LED Control Platform Driver");
MODULE_LICENSE("GPL");
