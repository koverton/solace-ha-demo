#!/bin/bash -x
cd `dirname $0`/..

if [ "$#" -ne 10 ]; then
	echo ""
	echo "USAGE: $0 <solace-ip> <vpn> <user> <pass> <appname> <instance#> <input-topic> <state-input-topic> <active-output-topic> <standby-output-topic>"
	echo ""
	exit 1
fi

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

java -cp $classpath -Djava.library.path=$solclientlib com.solacesystems.demo.MockMatchingEngine $*

