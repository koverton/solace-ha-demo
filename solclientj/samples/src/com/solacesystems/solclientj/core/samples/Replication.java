/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientErrorInfo;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.SessionEvent;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * Replication.java
 * 
 * This sample shows the publishing of Guaranteed messages through
 * a host list reconnect
 *
 * In the event callback, the publisher recognizes the displays
 * the content of events that may be seen when the session to
 * the original message-router fails and reconnects to the next
 * message-router in the host list.
 *
 * Prior running this sample, two Solace message-routers must be
 * configured with the same VPN and client configuration. These message
 * routers must not be an HA pair, as the purpose is to demonstrate
 * reconnect via a host list.
 *
 * The easiest way to force a reconnect from one host to the next is
 * to shutdown the client-username in the first message-router after
 * connecting with this application.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class Replication extends AbstractSample {

	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private ReplicationEventCallback replicationEventCallback = new ReplicationEventCallback();

	private MessageCallbackSample countingMessageReceiveCallback = new MessageCallbackSample(
			"Replication");

	private ByteBuffer content = ByteBuffer.allocateDirect(400);


	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-n number]\t Number of messages to publish, default: 1\n";
		System.out.println(usage);
		finish(1);
	}

	/**
	 * A SessionEventCallback implementation which holds and uses a
	 * CorrelationArray to match session events containing a correlationKey to a
	 * correlated object.
	 * 
	 */
	public static class ReplicationEventCallback implements
			SessionEventCallback {
	
		private int acknowledgementRx_m;
		private int rejectedMsgRx_m;
	
		public int getAcknowledgeMents() { return acknowledgementRx_m; }
		public int getRejectedMsgs() { return rejectedMsgRx_m; }
		
		@Override
		public void onEvent(SessionHandle sessionHandle) {

			SessionEvent se = sessionHandle.getSessionEvent();

			int sessionEventCode = se.getSessionEventCode();

			SolclientErrorInfo solclientErrorInfo = Solclient
					.getLastErrorInfo();
			
			switch (sessionEventCode) {

			case SolEnum.SessionEventCode.ACKNOWLEDGEMENT:
			    
				print("ReplicationEventCallback - Received ACKNOWLEDGEMENT");
    			acknowledgementRx_m ++;
				break;
				
			case SolEnum.SessionEventCode.REJECTED_MSG_ERROR:
                
                print("ReplicationEventCallback - Received REJECTED_MSG_ERROR; subCode ["
                		+ solclientErrorInfo.toString() + "]");
                rejectedMsgRx_m ++;
				break;

			case SolEnum.SessionEventCode.UP_NOTICE:
			case SolEnum.SessionEventCode.TE_UNSUBSCRIBE_OK:
			case SolEnum.SessionEventCode.CAN_SEND:
			case SolEnum.SessionEventCode.RECONNECTING_NOTICE:
			case SolEnum.SessionEventCode.RECONNECTED_NOTICE:
			case SolEnum.SessionEventCode.PROVISION_OK:
			case SolEnum.SessionEventCode.SUBSCRIPTION_OK:
			case SolEnum.SessionEventCode.VIRTUAL_ROUTER_NAME_CHANGED:
			case SolEnum.SessionEventCode.REPUBLISH_UNACKED_MESSAGES:
				print("ReplicationEventCallback - Received eventCode ["
						+ sessionEventCode + "], info '"
						+ se.getInfo() +"'");
				break;
				
			case SolEnum.SessionEventCode.DOWN_ERROR:
			case SolEnum.SessionEventCode.CONNECT_FAILED_ERROR:
			case SolEnum.SessionEventCode.SUBSCRIPTION_ERROR:
			case SolEnum.SessionEventCode.TE_UNSUBSCRIBE_ERROR:
			case SolEnum.SessionEventCode.PROVISION_ERROR:
				print("ReplicationEventCallback - Error Received eventCode ["
						+ sessionEventCode + "] " + solclientErrorInfo);
				break;

			default:
				print("ReplicationEventCallback - Received Unrecognized or deprecated event, eventCode ["
						+ sessionEventCode + "]");

				break;
			}
		}

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine the numberOfMessageToPublish

		int numberOfMessageToPublish = 1;

		String strCount = config.getArgBag().get("-n");
		if (strCount != null) {
			try {
				numberOfMessageToPublish = Integer.parseInt(strCount);
			} catch (NumberFormatException e) {
				printUsage(config instanceof SecureSessionConfiguration);
			}
		}

		// Init
		print(" Initializing the Java RTO Messaging API...");
		int rc = Solclient.init(new String[0]);
		assertReturnCode("Solclient.init()", rc, SolEnum.ReturnCode.OK);
		
		// Set a log level (not necessary as there is a default)
		Solclient.setLogLevel(logLevel);

		// Context
		print(" Creating a context ...");
		rc = Solclient.createContextForHandle(contextHandle, new String[0]);
		assertReturnCode("Solclient.createContext()", rc, SolEnum.ReturnCode.OK);

		// Session - Notice the registered replicationEventCallback with the
		// session
		print(" Creating a session ...");

		// Get session configuration with spare room
		int spareRoom = 14;
		String[] sessionProps = getSessionProps(config, spareRoom);
		int sessionPropsIndex = sessionProps.length - spareRoom;

		/*
		 * Times to try to connect to the host Appliance (or list of Appliances)
		 * during connection setup. -1 means try to connect forever.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.CONNECT_RETRIES;
		sessionProps[sessionPropsIndex++] = "-1";

		/*
		 * Times to retry to reconnect to the host Appliance (or list of
		 * Appliances) after a connected Session goes down. -1 means try to
		 * connect forever.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.RECONNECT_RETRIES;
		sessionProps[sessionPropsIndex++] = "-1";

		/*
		 * Note: Reapplying subscriptions allows Sessions to reconnect after
		 * failure and have all their subscriptions automatically restored. For
		 * Sessions with many subscriptions, this can increase the amount of
		 * time required for a successful reconnect.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.REAPPLY_SUBSCRIPTIONS;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		/*
		 * Note: Including meta data fields such as sender timestamp, sender ID,
		 * and sequence number can reduce the maximum attainable throughput as
		 * significant extra encoding/ decodings required. This is true whether
		 * the fields are autogenerated or manually added.
		 */

		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SEND_TIMESTAMPS;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SENDER_ID;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SEQUENCE_NUMBER;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		rc = contextHandle.createSessionForHandle(sessionHandle, sessionProps,
				countingMessageReceiveCallback, replicationEventCallback);
		assertReturnCode("contextHandle.createSession()", rc,
				SolEnum.ReturnCode.OK);

		// Connect

		print(" Connecting session ...");
		rc = sessionHandle.connect();
		assertReturnCode("sessionHandle.connect()", rc, SolEnum.ReturnCode.OK);

		print(" Will send [" + numberOfMessageToPublish + "] messages");


		for (int loop = 1; loop <= numberOfMessageToPublish; loop++) {

			/*************************************************************************
			 * MSG building
			 *************************************************************************/

			/* Allocate a message. */
			MessageHandle txMessageHandle = Solclient.Allocator
					.newMessageHandle();

			print(" Created a Message Handle ...");
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode("Solclient.createMessage()", rc,
					SolEnum.ReturnCode.OK);

			/* Set the delivery mode for the message. */
			txMessageHandle
					.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

			/* Set the destination */
			txMessageHandle.setDestination(topic);

			// Make a message
			String msg = "Message [" + loop + "]";

			// Rewind and reuse the content buffer with new content
			content.clear();
			content.put(msg.getBytes(Charset.defaultCharset()));
			content.flip();

			// This will copy the binary content into the message
			txMessageHandle.setBinaryAttachment(content);


			/*************************************************************************
			 * MSG sending
			 *************************************************************************/

			print("Sending message  ["
					+ loop + "]");

			// Send it
			rc = sessionHandle.send(txMessageHandle);
			assertReturnCode("sessionHandle.send()", rc, SolEnum.ReturnCode.OK,
					SolEnum.ReturnCode.IN_PROGRESS);
			txMessageHandle.destroy();
			// Wait
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (replicationEventCallback.getRejectedMsgs() != 0) {
			print ("Test saw '" 
					+ replicationEventCallback.getRejectedMsgs()
		            +"' SOLCLIENT_SESSION_EVENT_REJECTED_MSG_ERROR. None expected");
		}
		if ((replicationEventCallback.getRejectedMsgs() 
				+ replicationEventCallback.getAcknowledgeMents())!= numberOfMessageToPublish) {
			/*
	         * We expect to see one response for every message sent.
	         */
	        print ( "Test saw '"
	        		+ replicationEventCallback.getRejectedMsgs() 
	        		+ "/"
	        		+ replicationEventCallback.getAcknowledgeMents()
	        		+"' responses (acknowlegement/rejected). Expected '"
	        		+numberOfMessageToPublish
	        		+"'");


		}
		if (countingMessageReceiveCallback.getMessageCount() != 
				numberOfMessageToPublish) {
			print ( "Test published '" +numberOfMessageToPublish 
					+"' and receieved '" 
					+countingMessageReceiveCallback.getMessageCount()
					+"'");
		}
	}

	/**
	 * Invoked when the sample finishes
	 */
	@Override
	protected void finish(int status) {

		/*************************************************************************
		 * Cleanup
		 *************************************************************************/

		// Forced Cleanup

		finish_Disconnect(sessionHandle);

		finish_DestroyHandle(sessionHandle, "sessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		Replication sample = new Replication();
		sample.run(args);
	}

}
