#!/bin/bash
cd `dirname $0`/..

function usage {
	echo "	USAGE: $0 [start | stop]"
	echo ""
	exit 1
}
op=$1

plat=`uname`
if [ "Linux" == "$plat" ]; then
	solclientlib="../solclientj/lnxlib"
	export LD_LIBRARY_PATH=$solclientlib:$LD_LIBRARY_PATH
elif [ "Darwin" == "$plat" ]; then
	solclientlib="../solclientj/osxlib"
	export DYLD_LIBRARY_PATH=$solclientlib:$DYLD_LIBRARY_PATH
else
	echo "	Unknown platform $plat; exitting"
	usage
	exit 1
fi

# source in environment settings
. bin/env.sh

if [ "$#" -ne 1 ]; then
	usage

elif [ "$op" == "start" ]; then
	echo "starting ..."
	classpath=target/classes:`mvn dependency:build-classpath | grep repository`
	java -cp $classpath -Djava.library.path=$solclientlib \
		com.solacesystems.demo.MockMatchingEngine \
		$host app1 1 \
		$vpn m1user pass \
		order/new \
		active_matcher/app1/inst2/\> \
		active_matcher/app1/inst1/new \
		standby_matcher/app1/inst1/new > logs/matcher1.log&
	echo $! > logs/matcher1.pid

elif [ "$op" == "stop" ]; then
	echo "stopping ..."
	kill `cat logs/matcher1.pid`

else
	echo "	Unknown command: $op"
	usage
fi
