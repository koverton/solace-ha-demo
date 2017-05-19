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
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.FlowEventCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * NoLocalPubSub.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Subscribing to a Topic for Direct messages on a Session with No Local
 * delivery enabled.
 * <li>Creating a Flow to a Queue with no Local Delivery enabled on the Flow,
 * but not on the Session.
 * <li>Publish a message to the Direct message on each Session, and verify that
 * it is not delivered locally.
 * <li>Publish a message to the Queue on each Session, and verify that it is not
 * delivered locally.
 * </ul>
 * 
 * This sample demonstrates the use of the NO_LOCAL Session and flow property.
 * With this property enabled, messages are not received on the publishing
 * Session, even with a Topic or Flow match.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class NoLocalPubSub extends AbstractSample {

	private SessionHandle sessionHandleA = Solclient.Allocator
			.newSessionHandle();

	private SessionHandle sessionHandleB = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private Queue queue;

	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private ByteBuffer content = ByteBuffer.allocateDirect(200);

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		System.out.println(usage);
		finish(1);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

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

		/*************************************************************************
		 * Create and connect Session 'A'. Session 'A' allows local delivery of
		 * Direct messages.
		 *************************************************************************/
		print(" Creating solClient session A.");

		// Using a message counting callback
		MessageCallbackSample countingMessageReceiveCallbackA = new MessageCallbackSample(
				"A");

		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();

		String[] sessionPropsA = getSessionProps(config, 0);
		rc = contextHandle.createSessionForHandle(sessionHandleA,
				sessionPropsA, countingMessageReceiveCallbackA,
				sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session A", rc,
				SolEnum.ReturnCode.OK);

		// Connect Session A
		print(" Connecting session A ...");
		rc = sessionHandleA.connect();
		assertReturnCode("sessionHandleA.connect()", rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Ensure that the Appliance supports No Local delivery
		 *************************************************************************/
		print("Checking for capability SolEnum.CapabilityType.CAPABILITY_NO_LOCAL");
		if (!sessionHandleA
				.isCapable(SolEnum.CapabilityName.CAPABILITY_NO_LOCAL)) {
			throw new RuntimeException(
					"Required Capability is not present: SolEnum.CapabilityType.CAPABILITY_NO_LOCAL No Local delivery mode. ");
		}

		/*************************************************************************
		 * Create and connect Session 'B'. Session 'B' disallows local delivery
		 * of Direct messages. The common function cannot be used to create this
		 * Session because it has non-standard properties.
		 *************************************************************************/
		print(" Creating solClient session B.");

		// Get session configuration with spare room
		int spareRoom = 2;
		String[] sessionPropsB = getSessionProps(config, spareRoom);
		int sessionBPropsIndex = sessionPropsB.length - spareRoom;

		/*
		 * Prevent local delivery by enabling SessionHandle.PROPERTIES.NO_LOCAL
		 * property.
		 */
		sessionPropsB[sessionBPropsIndex++] = SessionHandle.PROPERTIES.NO_LOCAL;
		sessionPropsB[sessionBPropsIndex++] = SolEnum.BooleanValue.ENABLE;

		// Using a message counting callback
		MessageCallbackSample countingMessageReceiveCallbackB = new MessageCallbackSample(
				"B");

		/* Create the Session. */
		rc = contextHandle.createSessionForHandle(sessionHandleB,
				sessionPropsB, countingMessageReceiveCallbackB,
				sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session B", rc,
				SolEnum.ReturnCode.OK);

		/* Connect the B Session. */
		print(" Connecting session B ...");
		rc = sessionHandleB.connect();
		assertReturnCode("sessionHandleB.connect()", rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Subscribe to the common Topic on sessionB
		 *************************************************************************/
		print(" Adding subscription for Topic on sessionB ...");
		rc = sessionHandleB.subscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("sessionHandleB.subscribe() ", rc,
				SolEnum.ReturnCode.OK);

		/************************************************************************
		 * Provision a Queue on the Appliance
		 ***********************************************************************/
		queue = common_createQueue(sessionHandleA, SampleUtils.SAMPLE_QUEUE);

		/*************************************************************************
		 * Create a Flow to the Queue on sessionA. (Local Delivery is allowed on
		 * the Session but not on the Flow.)
		 *************************************************************************/

		/*************************************************************************
		 * Preparing flowProperties
		 *************************************************************************/

		// Flow Properties
		int flowProps = 0;
		String[] flowProperties = new String[6];

		flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
		flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

		/*
		 * Send an ack when the message has been received. The default value is
		 * to automatically acknowledge on return from the message receive
		 * callback but it is recommended to use client acknowledgement when
		 * using flows.
		 */
		flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
		flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

		/*
		 * Enable SOLCLIENT_FLOW_PROP_NO_LOCAL property to prevent local
		 * delivery on a Queue.
		 */
		flowProperties[flowProps++] = FlowHandle.PROPERTIES.NO_LOCAL;
		flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

		FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();

		/*************************************************************************
		 * Creating flow
		 *************************************************************************/
		print("Creating flow ...");

		rc = sessionHandleA.createFlowForHandle(flowHandle, flowProperties,
				queue, null, countingMessageReceiveCallbackA,
				flowEventCallback);

		assertReturnCode("Creating flow", rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Publish a message on sessionA that will be received on SessionB.
		 *************************************************************************/

		print("Publishing messages.");
		// Allocate the message
		rc = Solclient.createMessageForHandle(txMessageHandle);
		assertReturnCode("Solclient.createMessageForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.DIRECT);

		// Set the destination/topic
		txMessageHandle.setDestination(topic);

		content.clear();
		content.put("Hello World".getBytes(Charset.defaultCharset()));
		content.flip();

		/* Add some content to the message. */
		txMessageHandle.setBinaryAttachment(content);

		/* Send the message. */
		rc = sessionHandleA.send(txMessageHandle);
		assertReturnCode("sessionHandleA.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);

		/* Pause to let the callback receive messages. */
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		/* Should be seen only on Session B. */
		if (countingMessageReceiveCallbackA.getMessageCount() != 0
				|| countingMessageReceiveCallbackB.getMessageCount() != 1) {
			throw new IllegalStateException(
					"Published direct message seen on session A or not seen on session B");
		}

		print("countingMessageReceiveCallbackA["
				+ countingMessageReceiveCallbackA.getMessageCount()
				+ "] countingMessageReceiveCallbackB["
				+ countingMessageReceiveCallbackB.getMessageCount() + "]");

		/* Reset msgCounterB. */
		countingMessageReceiveCallbackB.setMessageCount(0);

		/*************************************************************************
		 * Publish a message on SessionB that will be not be received at all.
		 *************************************************************************/

		print("Publishing message on Session B.");

		/* Send the message. */
		rc = sessionHandleB.send(txMessageHandle);
		assertReturnCode("sessionHandleB.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);

		/* Pause to let the callback receive messages. */
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		print("countingMessageReceiveCallbackA["
				+ countingMessageReceiveCallbackA.getMessageCount()
				+ "] countingMessageReceiveCallbackB["
				+ countingMessageReceiveCallbackB.getMessageCount() + "]");
		/* Should not be seen. */
		if (countingMessageReceiveCallbackA.getMessageCount() != 0
				|| countingMessageReceiveCallbackB.getMessageCount() != 0) {
			throw new IllegalStateException(
					"Published direct message seen on session A or on session B");
		}

		/*************************************************************************
		 * Publish a message on SessionA to COMMON_TESTQ that will be not be
		 * received at all. NOTE: It is expected that the Appliance will reject
		 * this message because it is published to the Queue name and cannot be
		 * accepted. The test should report a "No Local Discard" message
		 * rejection received from the Appliance.
		 *************************************************************************/

		print("NoLocalPubSub: Publishing a message that will be rejected by Appliance due to No Local Discard");

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

		/* Set the destination. */
		txMessageHandle.setDestination(queue);

		/* Send the message. */
		rc = sessionHandleA.send(txMessageHandle);
		assertReturnCode("sessionHandleA.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);

		/* Pause to let callback receive messages. */
		try {
			print("Waiting for Event ... ");
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		print("countingMessageReceiveCallbackA["
				+ countingMessageReceiveCallbackA.getMessageCount()
				+ "] countingMessageReceiveCallbackB["
				+ countingMessageReceiveCallbackB.getMessageCount() + "]");

		/* The message should not be seen. */
		if (countingMessageReceiveCallbackA.getMessageCount() != 0
				|| countingMessageReceiveCallbackB.getMessageCount() != 0) {
			throw new IllegalStateException(
					"Published persistent message seen on session A or on session B");
		}

		/*************************************************************************
		 * Publish a message to a Queue on SessionB; the message will be be
		 * received on the Flow on sessionA.
		 *************************************************************************/

		print("Publishing message on Session B.");

		/* Send the message. */
		rc = sessionHandleB.send(txMessageHandle);
		assertReturnCode("sessionHandleA.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);

		/* Pause to let callback receive messages. */
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		print("countingMessageReceiveCallbackA["
				+ countingMessageReceiveCallbackA.getMessageCount()
				+ "] countingMessageReceiveCallbackB["
				+ countingMessageReceiveCallbackB.getMessageCount() + "]");

		if (countingMessageReceiveCallbackA.getMessageCount() != 1
				|| countingMessageReceiveCallbackB.getMessageCount() != 0) {
			throw new IllegalStateException(
					"Published persistent message not seen on session A or seen on session B");
		}

		print("Test Passed");

		print("Run() DONE");
	}

	/**
	 * Invoked when the sample finishes
	 */
	@Override
	protected void finish(int status) {

		/*************************************************************************
		 * CLEANUP
		 *************************************************************************/

		try {
			/*************************************************************************
			 * Unsubscribe
			 *************************************************************************/
			print("Unsubscribe");

			int rc = sessionHandleB.unsubscribe(topic,
					SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandleB.unsubscribe()", rc,
					SolEnum.ReturnCode.OK, SolEnum.ReturnCode.OK);
		} catch (Throwable t) {
			error("Unable to deprovision a queue ", t);
		}

		/*
		 * Destroy the Flow before deleting the Queue or else the API will log
		 * at NOTICE level for the unsolicited unbind.
		 */
		finish_DestroyHandle(flowHandle, "flowHandle");

		finish_Deprovision(queue, sessionHandleA);

		finish_DestroyHandle(txMessageHandle, "messageHandle");

		finish_Disconnect(sessionHandleA);

		finish_DestroyHandle(sessionHandleA, "sessionHandleA");

		finish_Disconnect(sessionHandleB);

		finish_DestroyHandle(sessionHandleB, "sessionHandleB");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		NoLocalPubSub sample = new NoLocalPubSub();
		sample.run(args);
	}

}
