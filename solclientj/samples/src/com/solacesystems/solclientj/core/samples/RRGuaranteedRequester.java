/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.nio.ByteBuffer;
import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.FlowEventCallback;
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.Handle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * RRGuaranteedRequester.java
 * 
 * This sample shows how to implement a Requester for guaranteed Request-Reply
 * messaging, where
 * 
 * 
 * <dl>
 * <dt>RRGuaranteedRequester
 * <dd>A message Endpoint that sends a guaranteed request message and waits to
 * receive a reply message as a response.
 * <dt>RRGuaranteedReplier
 * <dd>A message Endpoint that waits to receive a request message and responses
 * to it by sending a guaranteed reply message.
 * </dl>
 * 
 * <pre>
 *  |-----------------------|  -- RequestQueue/RequestTopic --> |----------------------|
 *  | RRGuaranteedRequester |                                   | RRGuaranteedReplier  |
 *  |-----------------------|  <-------- ReplyQueue ----------  |----------------------|
 * </pre>
 * 
 * <b>Notes: the RRGuaranteedReplier supports request queue or topic formats,
 * but not both at the same time.</b>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class RRGuaranteedRequester extends AbstractSample {

	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private ByteBuffer txContent = ByteBuffer.allocateDirect(200);
	private static ByteBuffer rxContent = ByteBuffer.allocateDirect(200);

	static boolean[] requestReceived;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-t topic]\t Topic\n";
		usage += "\t[-q queue]\t Guaranteed Message Queue.\n";
		usage += "\t\t Topic and Queue are mutually exclusive, just pick one\n";
		usage += "\t[-n number]\t Number of request messages to send, default: 5\n";
		System.out.println(usage);
		finish(1);
	}

	public void sendRequests(int maxRequestMessages, Destination destination,
			FlowHandle flowHandle) {

		int rc = 0;

		if (!txMessageHandle.isBound()) {
			// Allocate the message
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);
		}

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

		// Set the destination
		txMessageHandle.setDestination(destination);

		/*
		 * Retrieve the temporary queue name from the Flow.
		 */
		Destination replyToAddress = flowHandle.getDestination();
		/* set the replyTo address. */
		txMessageHandle.setReplyTo(replyToAddress);

		for (int i = 0; i < maxRequestMessages; i++) {

			int waitInSec = 10;

			txContent.clear();
			txContent.putInt(i);
			txContent.flip();

			/* Add some content to the message. */
			txMessageHandle.setBinaryAttachment(txContent);

			/* Send the message. */
			print("Sending Request [" + i + "]");
			rc = sessionHandle.send(txMessageHandle);
			assertReturnCode("aSessionHandle.send()", rc, SolEnum.ReturnCode.OK);

			requestReceived[i] = false;

			/* Wait until a reply message or being interrupted. */
			while ((!requestReceived[i]) && (waitInSec > 0)) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				waitInSec--;
			}

			if (waitInSec == 0) {
				print("Request message timeout.");
				break;
			}

		} // EndFor

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine a destinationName (topic)
		String topicName = config.getArgBag().get("-t");

		// Determine a Queue name
		String queueName = config.getArgBag().get("-q");

		if (topicName == null && queueName == null) {
			printUsage(config instanceof SecureSessionConfiguration);
			return;
		}

		if (topicName != null && queueName != null) {
			printUsage(config instanceof SecureSessionConfiguration);
			return;
		}

		int numberOfRequestMessages = 5;

		String strCount = config.getArgBag().get("-n");
		if (strCount != null) {
			try {
				numberOfRequestMessages = Integer.parseInt(strCount);
			} catch (NumberFormatException e) {
				printUsage(config instanceof SecureSessionConfiguration);
			}
		}

		requestReceived = new boolean[numberOfRequestMessages];

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

		/* Create a Session */
		print(" Create a Session.");

		int spareRoom = 10;
		String[] sessionProps = getSessionProps(config, spareRoom);
		int sessionPropsIndex = sessionProps.length - spareRoom;
		/*
		 * Note: Reapplying subscriptions allows Sessions to reconnect after
		 * failure and have all their subscriptions automatically restored. For
		 * Sessions with many subscriptions this can increase the amount of time
		 * required for a successful reconnect.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.REAPPLY_SUBSCRIPTIONS;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;
		/*
		 * Note: Including meta data fields such as sender timestamp, sender ID,
		 * and sequence number will reduce the maximum attainable throughput as
		 * significant extra encoding/decoding is required. This is true whether
		 * the fields are autogenerated or manually added.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SEND_TIMESTAMPS;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SENDER_ID;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SEQUENCE_NUMBER;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		/*
		 * The certificate validation property is ignored on non-SSL sessions.
		 * For simple demo applications, disable it on SSL sesssions (host
		 * string begins with tcps:) so a local trusted root and certificate
		 * store is not required. See the API users guide for documentation on
		 * how to setup a trusted root so the servers certificate returned on
		 * the secure connection can be verified if this is desired.
		 */
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.SSL_VALIDATE_CERTIFICATE;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.DISABLE;

		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();
		MessageCallbackSample messageCallback = getMessageCallback(false);
		/* Create the Session. */
		rc = contextHandle.createSessionForHandle(sessionHandle, sessionProps,
				messageCallback, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session", rc,
				SolEnum.ReturnCode.OK);

		/* Connect the Session. */
		print(" Connecting session ...");
		rc = sessionHandle.connect();
		assertReturnCode("sessionHandle.connect()", rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Create a Flow and a temporary reply Queue
		 *************************************************************************/

		/*************************************************************************
		 * Preparing flowProperties
		 *************************************************************************/

		// Flow Properties
		int flowProps = 0;
		String[] flowProperties = new String[10];

		flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
		flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

		FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
		FlowMessageReceivedCallback flowMessageReceivedCallback = new FlowMessageReceivedCallback();
		Queue tempQueue = sessionHandle.createTemporaryQueue();

		print("Created tempQueue [" + tempQueue.getName() + "] isTemporary? ["
				+ tempQueue.isTemporary() + "] isDurable? ["
				+ tempQueue.isDurable() + "]");

		rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
				tempQueue, null, flowMessageReceivedCallback,
				flowEventCallback);

		assertReturnCode("sessionHandle.createFlowForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Request Message
		 *************************************************************************/

		// Use Queue or topic ?!
		Destination destination = null;

		if (queueName != null) {
			print("Will send request messages to queue " + queueName);
			destination = Solclient.Allocator.newQueue(queueName, null);
		} else {
			print("Will send request messages to topic " + topicName);
			destination = Solclient.Allocator.newTopic(topicName);
		}

		sendRequests(numberOfRequestMessages, destination, flowHandle);

		print("Run() DONE");
	}

	/**
	 * Invoked when the sample finishes
	 */
	@Override
	protected void finish(int status) {
		/*************************************************************************
		 * Cleanup
		 *************************************************************************/

		finish_DestroyHandle(flowHandle, "flowHandle");

		finish_DestroyHandle(txMessageHandle, "messageHandle");

		finish_Disconnect(sessionHandle);

		finish_DestroyHandle(sessionHandle, "sessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();
	}

	public static class FlowMessageReceivedCallback implements MessageCallback {

		private int messageCount = 0;

		@Override
		public void onMessage(Handle handle) {

			FlowHandle flowHandle = (FlowHandle) handle;
			MessageHandle rxMessage = flowHandle.getRxMessage();

			rxContent.clear();
			// Get the binary attachment from the received message
			rxMessage.getBinaryAttachment(rxContent);
			rxContent.flip();

			int requestInt = rxContent.getInt();

			int expectedRequestInt = messageCount;

			print("-> RRGuaranteedRequester -> Received reponse [" + requestInt
					+ "]");

			if (requestInt != expectedRequestInt) {
				throw new IllegalStateException(String.format(
						"[%d] was expected, got this request instead [%d]",
						expectedRequestInt, requestInt));
			}

			// Mark as received
			requestReceived[requestInt] = true;

			messageCount++;
		}

		public int getMessageCount() {
			return messageCount;
		}

		public void setMessageCount(int messageCount) {
			this.messageCount = messageCount;
		}
	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		RRGuaranteedRequester sample = new RRGuaranteedRequester();
		sample.run(args);
	}

}
