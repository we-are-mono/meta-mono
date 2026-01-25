#! /bin/sh

ppp_ifname=$1
ppp_ifindex=$7

mkdir -p /tmp/tunnels

tunnel_information=$(ip tunnel show 2> /dev/null | grep dev)
iface_list=$(ls /sys/class/net)
no_of_tunnels=$(echo $tunnel_information | wc -l)
#echo "no_of_tunnels=$no_of_tunnels"

tunnel_log_file=/tmp/tunnels/tunnel_${ppp_ifname}.log
echo "" > $tunnel_log_file

index=1
while [[ $index -le $no_of_tunnels ]]
do
	tunnel_info=$(echo $tunnel_information | head -n $index | tail -n 1)
	tunnel_name=$(echo $tunnel_info | cut -d':' -f 1)
	tunnel_device=$(echo $tunnel_info | egrep -o ' dev (.*)' | cut -d' ' -f 3)
	iface_match=0
	for iface in $iface_list;
	do
		#echo "iface=$iface tunnel_device=$tunnel_device" >>/log1.txt
		if [ -n "$iface" -a -n "$tunnel_device" -a $iface = $tunnel_device ]; then
			iface_match=1
			break
		fi
	done
	if [ $iface_match -eq 0 ]; then
		if [ "$tunnel_device" = "if$ppp_ifindex" ]; then
			echo "$tunnel_name $ppp_ifname" >>$tunnel_log_file
		fi
	else
		echo "$tunnel_name $tunnel_device" >>$tunnel_log_file
	fi
	let index=index+1
done

