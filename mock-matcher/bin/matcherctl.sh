#!/bin/bash -x
cd `dirname $0`/..

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

function now() {
	date -u +"%Y-%m-%dT%H:%M:%SZ"
}

function dispatch {
    ts=$(now)
    echo "$ts topic: $1"
    # <app> / control / <component> / <op: kill | start> / <instance-#>
    srv=`echo $topic | cut -d'/' -f 3`
    op=`echo $topic | cut -d'/' -f 4`
    inst=`echo $topic | cut -d'/' -f 5`

    if [ "ogw" == "$srv" ]; then
            ogwctl $op
    elif [ "matcher" == "$srv" ]; then
            matcherctl $inst $op
    else
            echo "Unknown service $srv; ignored"
    fi
}

function ogwctl {
    op=$1
    ts=$(now)
    echo "$ts bin/ogw.sh $op"
    bin/ogw.sh $op
}

function matcherctl {
    inst=$1
    op=$2
    ts=$(now)
    echo "$ts bin/matcher${inst}.sh $op"
    bin/matcher${inst}.sh $op
}

# source in environment settings
. bin/env.sh

if [ "Darwin" == `uname` ]; then
	tdump_exe=bin/topic_dump.osx
else
	tdump_exe=bin/topic_dump.lnx
fi

$tdump_exe $host $vpn huntsman x $matchapp/control/\> | \
	while read -r topic; do 
		dispatch $topic
	done

