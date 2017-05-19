                   Java RTO Messaging API Sample Applications

INTRODUCTION

   These samples provide a basic introduction to using the Java RTO Messaging 
   API (solclientj) in messaging applications. Common uses, such as sending a 
   message, receiving a message, asynchronous messaging, and subscription 
   management, are described in detail in these samples.

   Before working with these samples, ensure that you have read and understood
   the basic concepts found in the Messaging APIs Developer Guide. 

SOFTWARE REQUIREMENTS

   The following third-party software tools are required for building  and  run-
   ning the samples:

     o Apache Ant version 1.9.x or above

     o Java SDK 7.0 or above

   The  following libraries  are  required for building and running the 
   solclientj samples:

   Solace Corporation Libraries:
  
   Under Linux:

     o lib/solclientj.jar
     o lib/libsolclient.so
     o lib/libsolclient_jni.so
     o lib/libcrypto.so
     o lib/libssl.so


   Under Windows:

     o lib\solclientj.jar

     o bin\Win32\libsolclient.dll
     o bin\Win32\libsolclient_d.dll
     o bin\Win32\solclient_jni.dll
     o bin\Win32\solclient_jni_d.dll

     o bin\Win32\third-party\libeay32.dll
     o bin\Win32\third-party\ssleay32.dll

     o bin\Win64\libsolclient.dll
     o bin\Win64\libsolclient_d.dll
     o bin\Win64\solclient_jni.dll
     o bin\Win64\solclient_jni_d.dll

     o bin\Win64\third-party\libeay32.dll
     o bin\Win64\third-party\ssleay32.dll

INTRODUCTORY SAMPLES LIST

   The following introductory samples are included:
   
     ActiveFlowIndication
     	Provision Queues and request active flow indication when creating flows.

     AdPubAck
     	The publishing of Guaranteed messages and how message acknowledgments 
	can be handled on callback.

     ASyncCacheRequest
        Performs an asynchronous cache request.
        
     CutThroughFlowToQueue
	How to create a cut-through message Flow to a queue.

     DirectPubSub
        Publish/Subscribe with Direct messages.

     DTOPubSub
        Publish/Subscribe with Deliver-To-One features.

     JMSHeaders
        Publish/Subscribe and uses the JMS Headers.

     MessageSelectorsOnQueue
        Creating a message flow to a queue using a message selector to select 
        which messages should be delivered.

     MessageTTLAndDeadMessageQueue
        Provision endpoints which support message TTL and message expiry.

     NoLocalPubSub
        Demonstrates the use of the NO_LOCAL session  and flow property.

     PerfADPubSub
        A GC-free performance test using Guaranteed messages.

     PerfPubSub
        A GC-free performance test for subscribing and publishing direct 
        messages.

     PerfCutThroughFlowToQueue
        A GC-free version of CutThroughFlowToQueue.

     QueueProvision
        Provisioning a Queue, binding a flow to the Queue, publishing and 
        receiving messages from it.
        
     Replication
     	Show how publishing of Guaranteed messages works and how message
        acknowledgements are handled for the Replication feature.

     RRDirectReplier
        Demonstrates how to implement a replier that accepts requests and reply 
        to them using direct messaging.  

     RRDirectRequester
        Demonstrates how to implement a requester that sends a request to a 
        replier using direct messaging. Requires RRDirectReplier to be running. 
        
     RRGuaranteedReplier
        Demonstrates how to implement a replier that accepts requests and reply 
        to them using guaranteed messaging.  

     RRGuaranteedRequester
        Demonstrates how to implement a requestor that sends a request to a 
        replier using guaranteed messaging.  

     SecureSession
        Demonstrates setting up a secure connection to the appliance.
        
     SimpleFlowToQueue
        Demonstrates creating a flow to a durable or temporary queue, and client
        acknowledgement of messages.
        
     SimpleFlowToTopic
        Demonstrates creating a flow to a  durable  or  non-durable  topic
        endpoint, and auto-acknowledgement of messages.

     SubscribeOnBehalfOfClient
        Shows how to subscribe on behalf of another client.

     SyncCacheRequest
        Performs a synchronous cache request

     TopicDispatch
	Demonstrates using local topic dispatch to direct received messages 
        into specialized received data paths.

     TopicToQueueMapping
        Shows how to add topic subscriptions to  Queue  endpoints.

     Transactions
        Shows transacted session usage using a request/reply scenario.
 
   The source for these samples is in:
   samples/src/com/solacesystems/solclientj/core/samples

HOW TO BUILD THE SAMPLES

   To  build  the  samples,  go to the samples directory and invoke "ant build".
   Note that this command performs clean before starting the build process.

CONFIGURING THE SOLACE Appliance

   Some samples rely on the presence of a sample durable Queue  and  Topic  End-
   point.   In  addition  to  configuring  the appliance to authenticate the 
   sample applications successfully, you must do the following:

     o Create a queue named my_sample_queue, ensure that quota is >0

     o Create a durable Topic Endpoint named
       my_sample_topicendpoint, ensure the quota is >0

     o Ensure the message-vpn  you  use  is  marked  as  the
       Appliance's management message-vpn and maximum spool usage is >0

     o For  SolCache samples, ensure a cache is setup on the
       Solace Appliance

     o For the SubscribeOnBehalfOfClient sample, ensure Subscription Manager is 
       enabled in the Client Username

     o For the Replication sample, ensure Reject Message to Sender on No 
       Subscription match is disabled in the Client Profile (this is the
       default setting)


HOW TO RUN THE SAMPLES

   A startup script is provided to set up the Java CLASSPATH and start any  pro-
   vided  sample.  To run the startup script, go to the bin directory, and enter
   the following command:

   On LINUX:
     run.sh samples.SAMPLECLASSNAME -h Applianceip[:port] \
     -u username[@vpn] [-w password]

   On Windows:
     run.bat samples.SAMPLECLASSNAME -h Applianceip[:port] \
     -u username[@vpn] [-w password]

   If you are running the samples on UNIX/LINUX, ensure that execute permissions
   are enabled for run.sh.
