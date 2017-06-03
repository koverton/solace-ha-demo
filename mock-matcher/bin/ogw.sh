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
	echo ""
	echo "	Unknown platform $plat; exitting"
	usage
	exit 1
fi

if [ "$#" -ne 1 ]; then
	usage
elif [ "$1" == "start" ]; then
	echo "starting ..."
	classpath=target/classes:`mvn dependency:build-classpath | grep repository`

	java -cp $classpath -Djava.library.path=$solclientlib com.solacesystems.demo.MockOrderGateway \
		192.168.56.151 ha_demo pub pub order/new 1 AAPL > logs/ogw.log &
	echo $! > logs/ogw.pid
elif [ "$1" == "stop" ]; then
	echo "stopping ..."
	kill `cat logs/ogw.pid`
else
	echo "	Unknown command: $1"
	usage
fi
