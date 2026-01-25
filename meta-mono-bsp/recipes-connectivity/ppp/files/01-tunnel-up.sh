#! /bin/sh

ppp_ifname=$1
tunnel_log_file=/tmp/tunnels/tunnel_${ppp_ifname}.log

if [ -f $tunnel_log_file ]; then
	no_of_tunnels=$(cat $tunnel_log_file | wc -l)
#	echo "no_of_tunnels=$no_of_tunnels"
	if [ $no_of_tunnels -gt 1 ]; then
		index=1
		while [ $index -lt $no_of_tunnels ]
		do
			let line=index+1
			tunnel_name=$( head -n $line $tunnel_log_file | tail -n 1 | cut -d' ' -f 1)
			tunnel_device=$( head -n $line $tunnel_log_file | tail -n 1 | cut -d' ' -f 2)
			if [ -n $tunnel_name -a -n $tunnel_device ]; then
				ip tunnel change $tunnel_name dev $tunnel_device
#				echo "ip tunnel change $tunnel_name dev $tunnel_device"
			fi
			let index=index+1
		done
	fi
	rm $tunnel_log_file
fi


