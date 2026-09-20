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
    echo "16 500 128 500" > $LED_RED/pattern
    echo -1 > $LED_RED/repeat
else
    # Dim amber breathing in recovery.
    # Red:green ratio remains 255:48.
    echo pattern > $LED_RED/trigger
    echo "16 1000 128 1000" > $LED_RED/pattern
    echo -1 > $LED_RED/repeat

    echo pattern > $LED_GREEN/trigger
    echo "3 1000 24 1000" > $LED_GREEN/pattern
    echo -1 > $LED_GREEN/repeat
fi