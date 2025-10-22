#!/bin/sh

LED_WHITE="/sys/class/leds/status:white"
LED_RED="/sys/class/leds/status:red"
LED_GREEN="/sys/class/leds/status:green"
LED_BLUE="/sys/class/leds/status:blue"

# Reset all LEDs
for led in $LED_WHITE $LED_RED $LED_GREEN $LED_BLUE; do
    echo none > $led/trigger
    echo 0 > $led/brightness
done

# Check if U-Boot reported a test failure
if grep -q "hwtest_status=fail" /proc/cmdline; then
    echo pattern > $LED_RED/trigger
    echo "255 500 0 500" > $LED_RED/pattern
    echo -1 > $LED_RED/repeat
else
    echo pattern > $LED_WHITE/trigger
    echo "0 1000 255 1000" > $LED_WHITE/pattern
    echo -1 > $LED_WHITE/repeat
fi