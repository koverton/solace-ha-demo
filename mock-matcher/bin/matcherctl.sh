#!/bin/bash
cd `dirname $0`/..

function now() {
	date -u +"%Y-%m-%dT%H:%M:%SZ"
}

function dispatch {
	ts=$(now)
	echo "$ts topic: $1"
	# <app> / control / <op: kill | start> / <instance-#>
	app=`echo $topic | cut -d'/' -f 2`
	op=`echo $topic | cut -d'/' -f 3`
	inst=`echo $topic | cut -d'/' -f 4`
	if [ "kill" == "$op" ]; then
		killMatcher $inst
	elif [ "start" == "$op" ]; then
		startMatcher $inst
	fi
}

function killMatcher {
	ts=$(now)
	echo "$ts bin/matcher$1.sh stop"
	bin/matcher$1.sh stop
}

function startMatcher {
	ts=$(now)
	echo "$ts bin/matcher$1.sh start"
	bin/matcher$1.sh start
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

