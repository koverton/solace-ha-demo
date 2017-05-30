#!/bin/bash
cd `dirname $0`/..

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
	echo ""
	exit 1
fi

classpath=target/classes:`mvn dependency:build-classpath | grep repository`

java -cp $classpath -Djava.library.path=$solclientlib com.solacesystems.demo.MockOrderGateway \
	msgvpn-3419.messaging.datago.io:20128 msgvpn-3419 datago-client-username 72f9nie8jpfdaj8gjse23vr21t \
	order/new 1 AAPL