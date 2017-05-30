#!/bin/bash

cd `dirname $0`/..

function usage {
	echo "	USAGE: $0 [start | stop]"
	echo ""
	exit 1
}

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
fi

if [ "$#" -ne 1 ]; then
	usage

elif [ "$1" == "start" ]; then
	echo "starting ..."
	classpath=target/classes:`mvn dependency:build-classpath | grep repository`
	java -cp $classpath -Djava.library.path=$solclientlib \
		com.solacesystems.demo.MockMatchingEngine \
		192.168.56.151 app1 2 \
		ha_demo user2 password \
		order/new \
		active_matcher/app1/inst1/\> \
		active_matcher/app1/inst2/new \
		standby_matcher/app1/inst2/new > logs/matcher2.log&
	echo $! > logs/matcher2.pid

elif [ "$1" == "stop" ]; then
	echo "stopping ..."
	kill `cat logs/matcher2.pid`

else
	echo "	Unknown command: $1"
	usage
fi
