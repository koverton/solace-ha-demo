#!/bin/bash
cd `dirname $0`/..

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

if [ "Darwin" == `uname` ]; then
	tdump_exe=bin/topic_dump.osx
else
	tdump_exe=bin/topic_dump.lnx
fi

$tdump_exe 192.168.56.151 ha_demo huntsman x app1/control/\> | \
	while read -r topic; do 
		dispatch $topic
	done

