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
 * RRDirectRequester.java
 * 
 * This sample shows how to implement a Requester for direct Request-Reply
 * messaging, where
 * 
 * <dl>
 * <dt>RRDirectRequester
 * <dd>A message Endpoint that sends a request message and waits to receive a
 * reply message as a response.
 * <dt>RRDirectReplier
 * <dd>A message Endpoint that waits to receive a request message and responses
 * to it by sending a reply message.
 * </dl>
 * 
 * <pre>
 *  |-------------------|  ---RequestTopic --> |------------------|
 *  | RRDirectRequester |                      | RRDirectReplier  |
 *  |-------------------|  <--ReplyToTopic---- |------------------|
 * </pre>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class RRDirectRequester extends AbstractSample {

	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private MessageHandle rxMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private ByteBuffer txContent = ByteBuffer.allocateDirect(200);
	private ByteBuffer rxContent = ByteBuffer.allocateDirect(200);

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-t topic]\t Topic, default:" + SampleUtils.SAMPLE_TOPIC
				+ "\n";
		usage += "\t[-n number]\t Number of request messages to send, default: 5\n";
		System.out.println(usage);
		finish(1);
	}

	public void sendRequests(int maxRequestMessages, String aDestinationName) {

		Topic topic = Solclient.Allocator.newTopic(aDestinationName);

		int rc = 0;

		if (!txMessageHandle.isBound()) {
			// Allocate the message
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);
		}

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.DIRECT);

		// Set the destination/topic
		txMessageHandle.setDestination(topic);

		for (int i = 0; i < maxRequestMessages; i++) {

			txContent.clear();
			txContent.putInt(i);
			txContent.flip();

			/* Add some content to the message. */
			txMessageHandle.setBinaryAttachment(txContent);

			/* Send the message. */
			print("Sending Request [" + i + "] and blocking for a response");
			rc = sessionHandle.sendRequest(txMessageHandle, rxMessageHandle,
					5000);
			assertReturnCode("aSessionHandle.sendRequest()", rc,
					SolEnum.ReturnCode.OK);

			// Clear the buffer and copy message content into it.
			rxContent.clear();
			rxMessageHandle.getBinaryAttachment(rxContent);
			rxContent.flip();

			int response = rxContent.getInt();
			print("Received response [" + response + "]");

			// Do some assertion for the expected response
			if (response != i) {
				throw new IllegalStateException(String.format(
						"[%d] was expected, got this response instead [%d]", i,
						response));
			}

			if (rxMessageHandle.isBound()) {
				// Clean up time
				print("rxMessageHandle.destroy()");
				rxMessageHandle.destroy();
			}

		} // EndFor

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine a destinationName (topic), default to
		// SampleUtils.SAMPLE_TOPIC
		String destinationName = config.getArgBag().get("-t");
		if (destinationName == null) {
			destinationName = SampleUtils.SAMPLE_TOPIC;
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

		/* Send the requests and wait for the responses. */
		sendRequests(numberOfRequestMessages, destinationName);

		// ///////////////////////////////////////////// SHUTDOWN
		// ///////////////////////////////////

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

		finish_DestroyHandle(txMessageHandle, "messageHandle");

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
		RRDirectRequester sample = new RRDirectRequester();
		sample.run(args);
	}

}
