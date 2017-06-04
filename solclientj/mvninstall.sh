#!/bin/bash
plat=`uname`
if [ "Linux" == "$plat" ]; then
	libdir="lnxlib"
elif [ "Darwin" == "$plat" ]; then
	libdir="osxlib"
fi
mvn install:install-file -Dfile=$libdir/solclientj.jar -DpomFile=solclientj-7.2.5.7.pom
