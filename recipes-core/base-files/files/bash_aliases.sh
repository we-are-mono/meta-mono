# This is purely for convenience. Since recovery mode is often used behind a
# DHCP router, typing these by hand becomes annoying really fast. Now you just
# press the interface number (0 for eth0) and Recovery Linux does the rest!
0() { udhcpc -i eth0; }
1() { udhcpc -i eth1; }
2() { udhcpc -i eth2; }
3() { udhcpc -i eth3; }
4() { udhcpc -i eth4; }
