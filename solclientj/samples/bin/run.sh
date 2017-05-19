#!/bin/sh
#######################################################################
#   Run SOLCLIENTJ sample applications 
#
#   Copyright 2004-2017 Solace Corporation. All rights reserved.
#######################################################################

SOLCLIENTJ_HOME=`dirname $0`
SOLCLIENTJ_HOME=$SOLCLIENTJ_HOME/..

#classpath function
lcp() {
  # if the directory is empty, then it will return the input string
  if [ -f "$1" ] ; then
    if [ -z "$LOCALCLASSPATH" ] ; then
      LOCALCLASSPATH="$1"
    else
      LOCALCLASSPATH="$1":"$LOCALCLASSPATH"
    fi
  fi
}

# First check the arguments
sampleList=" samples.DirectPubSub samples.PerfPubSub samples.ActiveFlowIndication samples.AdPubAck samples.NoLocalPubSub samples.TopicDispatch samples.RRDirectRequester samples.RRDirectReplier samples.RRGuaranteedRequester samples.RRGuaranteedReplier samples.Transactions samples.SyncCacheRequest samples.ASyncCacheRequest samples.TopicToQueueMapping  samples.SimpleFlowToTopic samples.SimpleFlowToQueue samples.SubscribeOnBehalfOfClient samples.MessageTTLAndDeadMessageQueue samples.MessageSelectorsOnQueue samples.Replication samples.DTOPubSub samples.QueueProvision samples.SecureSession samples.CutThroughFlowToQueue samples.PerfCutThroughFlowToQueue samples.PerfADPubSub samples.JMSHeaders samples.Logging "
# check if user gave no arguments
if [ $# -lt 1 ] ; then
 echo "Expecting one of the following as the first argument:"
 echo "$sampleList"
exit
fi
#
found=0
for i in $sampleList
do
  matchCount=`expr "$i" : ".*$1"`
  if [ $matchCount -ne 0 ]; then
    found=1
    javaApp=$i
    shift
    break
  fi
done
if [ $found -eq 0 ] ; then 
 echo "Expecting one of the following as the first argument:"
 echo "$sampleList"
 exit
fi


if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=`which java 2> /dev/null `
    if [ -z "$JAVACMD" ] ; then
        JAVACMD=java
    fi
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi

if [ -n "$CLASSPATH" ] ; then
  LOCALCLASSPATH="$CLASSPATH"
fi

# add in the required dependency .jar files
for i in $SOLCLIENTJ_HOME/../lib/*.jar
do
  lcp $i
done

# 
LOCALCLASSPATH=$SOLCLIENTJ_HOME/config:"$LOCALCLASSPATH"

export SOLCLIENT_JNI_LIB_PATH=${SOLCLIENT_JNI_LIB_PATH:-$SOLCLIENTJ_HOME/../lib}
export DYLD_LIBRARY_PATH=$SOLCLIENT_JNI_LIB_PATH:$DYLD_LIBRARY_PATH
export PATH=$SOLCLIENT_JNI_LIB_PATH:$PATH

LOCALCLASSPATH=$SOLCLIENTJ_HOME/classes:"$LOCALCLASSPATH"
exec "$JAVACMD" -classpath "$LOCALCLASSPATH" -Djava.util.logging.config.file=$SOLCLIENTJ_HOME/config/sample_logging_config.properties -Djava.library.path=$SOLCLIENT_JNI_LIB_PATH com.solacesystems.solclientj.core.$javaApp "$@"
