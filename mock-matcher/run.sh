#!/bin/bash

libdir=../solclientj/osxlib
basedir=../../mock-matcher

cd ../solclientj/osxlib

classpath=$basedir/target/classes:/Users/koverton/.m2/repository/com/solacesystems/solace-ha/1.0-SNAPSHOT/solace-ha-1.0-SNAPSHOT.jar:/Users/koverton/.m2/repository/com/solacesystems/solclientj/7.11.0.8/solclientj-7.11.0.8.jar:/Users/koverton/.m2/repository/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar:/Users/koverton/.m2/repository/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar:/Users/koverton/.m2/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar:/Users/koverton/.m2/repository/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar:/Users/koverton/.m2/repository/junit/junit/4.10/junit-4.10.jar:/Users/koverton/.m2/repository/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar


host=localhost
vpn=default
user=matcher1
pass=m
app=aaplmatcher
inst=1
intopic=order/new/AAPL
peertopic="active_matcher/$app/inst2/>"
activetopic="active_matcher/$app/inst$inst/new
standbytopic="standby_matcher/$app/inst$inst/new

# PATH=../solclientj/osxlib:$PATH
# DYLD_LIBRARY_PATH=../solclientj/osxlib:$DYLD_LIBRARY_PATH

# localhost default matcher1 pass aaplmatcher 1 order/new/AAPL 'active_matcher/aaplmatcher/inst2/>' active_matcher/aaplmatcher/inst1/new standby_matcher/aaplmatcher/inst1/new
# -Djava.library.path=../solclientj/osxlib 
java -cp $classpath \
     -Djava.library.path=. \
     com.solacesystems.demo.MockMatchingEngine \
     $host $vpn $user $pass $app $inst $intopic $peertopic $activetopic $standbytopic
