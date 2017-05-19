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
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * RRGuaranteedReplier.java
 * 
 * This sample shows how to implement a Replier for guaranteed Request-Reply
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
public class RRGuaranteedReplier extends AbstractSample {

	private static SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private static MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private static ByteBuffer txContent = ByteBuffer.allocateDirect(200);

	private static ByteBuffer rxContent = ByteBuffer.allocateDirect(200);

	private static boolean quit = false;

	boolean endpointProvisioned = false;
	Endpoint endpoint = null;
	Topic topic = null;

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
		 * Ensure the endpoint provisioning is supported
		 *************************************************************************/
		print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_ENDPOINT_MANAGEMENT...");
		if (!sessionHandle
				.isCapable(SolEnum.CapabilityName.CAPABILITY_ENDPOINT_MANAGEMENT)) {
			throw new RuntimeException(
					"Required Capability is not present: Endpoint management not supported.");
		}

		/*************************************************************************
		 * Provision a durable queue or topic endpoint on Appliance
		 *************************************************************************/
		
		// EndPoint Properties

		int endpointPropsIndex = 0;

		String[] endpointProperties = new String[10];

		endpointProperties[endpointPropsIndex++] = Endpoint.PROPERTIES.PERMISSION;
		endpointProperties[endpointPropsIndex++] = SolEnum.EndpointPermission.MODIFY_TOPIC;

		endpointProperties[endpointPropsIndex++] = Endpoint.PROPERTIES.QUOTA_MB;
		endpointProperties[endpointPropsIndex++] = "100";

		if (queueName != null) {
			print("Will provision a queue " + queueName);
			endpoint = Solclient.Allocator.newQueue(queueName,endpointProperties);
		} else {
			print("Will provision a topic [" + topicName
					+ "] on topicEndpoint [" + SampleUtils.SAMPLE_TOPIC
					+ "]");
			endpoint = Solclient.Allocator
					.newTopicEndpoint(SampleUtils.SAMPLE_TOPIC,endpointProperties);
			topic = Solclient.Allocator.newTopic(topicName);
		}

		print("Provisioning endpoint [" + endpoint.getName() + "]");



		/* Try to provision the endpoint. */
		rc = sessionHandle.provision(endpoint, SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM
						| SolEnum.ProvisionFlags.IGNORE_EXIST_ERRORS, 0);
		assertReturnCode("sessionHandle.provision()", rc, SolEnum.ReturnCode.OK);

		endpointProvisioned = true;

		/*************************************************************************
		 * Preparing flowProperties
		 *************************************************************************/

		// Flow Properties
		int flowProps = 0;
		String[] flowProperties = new String[10];

		flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
		flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

		FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
		FlowMessageReceivedCallback flowMessageReceivedCallback = new FlowMessageReceivedCallback(
				numberOfRequestMessages);

		rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
				endpoint, topic, flowMessageReceivedCallback,
				flowEventCallback);

		assertReturnCode("sessionHandle.createFlowForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Serve requests
		 *************************************************************************/

		// Register a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				quit = true;
			}
		});

		// Do some waiting, the registered callback withh handle messages with
		// replies, it will quit when done
		while (!quit) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		print("Quitting time");

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

		if (endpointProvisioned)
			finish_Deprovision(endpoint, sessionHandle);

		finish_DestroyHandle(txMessageHandle, "messageHandle");

		finish_Disconnect(sessionHandle);

		finish_DestroyHandle(sessionHandle, "sessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();
	}

	public static class FlowMessageReceivedCallback implements MessageCallback {

		private int messageCount = 0;
		int expectedMax;

		public FlowMessageReceivedCallback(int max) {
			expectedMax = max;
		}

		@Override
		public void onMessage(Handle handle) {

			try {

				FlowHandle flowHandle = (FlowHandle) handle;
				MessageHandle rxMessage = flowHandle.getRxMessage();

				/* Get reply queue address. */
				Destination replyTo = rxMessage.getReplyTo();

				rxContent.clear();
				// Get the binary attachment from the received message
				rxMessage.getBinaryAttachment(rxContent);
				rxContent.flip();

				int requestInt = rxContent.getInt();

				int expectedRequestInt = messageCount;

				print("-> RRGuaranteedReplier -> Received request ["
						+ requestInt + "]");

				if (requestInt != expectedRequestInt) {
					throw new IllegalStateException(String.format(
							"[%d] was expected, got this request instead [%d]",
							expectedRequestInt, requestInt));
				}

				// Send a response
				if (!txMessageHandle.isBound()) {
					// Allocate the message
					int rc = Solclient.createMessageForHandle(txMessageHandle);
					assertReturnCode("Solclient.createMessageForHandle()", rc,
							SolEnum.ReturnCode.OK);
				}

				/* Set the message delivery mode. */
				txMessageHandle
						.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

				txMessageHandle.setDestination(replyTo);

				// The Use the tx buffer
				txContent.clear();
				txContent.putInt(requestInt);
				txContent.flip();

				txMessageHandle.setBinaryAttachment(txContent);

				print("Sending response [" + requestInt + "] to ["
						+ replyTo.getName() + "]");

				// Response
				int rc = sessionHandle.send(txMessageHandle);
				assertReturnCode("sessionHandle.send", rc,
						SolEnum.ReturnCode.OK);

				messageCount++;

			} catch (IllegalStateException ise) {
				quit = true;
				throw ise;

			} finally {
				if (!quit && (messageCount >= expectedMax))
					quit = true;
			}
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
		RRGuaranteedReplier sample = new RRGuaranteedReplier();
		sample.run(args);
	}

}
