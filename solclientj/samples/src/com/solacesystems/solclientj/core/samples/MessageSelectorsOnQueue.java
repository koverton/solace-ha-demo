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
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * MessageSelectorsOnQueue.java
 * 
 * This sample demonstrates how to:
 * <ul>
 * <li>Create and bind a Flow to a temporary Queue with a message selector on a
 * Solace API Selector Identifier.
 * <li>Publish a number of Guaranteed messages with the given Selector
 * Identifier property to the temporary Queue.
 * <li>Show that, messages matching the registered message selector are
 * delivered to the temporary Queue Flow.
 * </ul>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong> *
 */
public class MessageSelectorsOnQueue extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private ByteBuffer txContentBuffer = ByteBuffer.allocateDirect(512);

	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		System.out.println(usage);
		finish(1);
	}

	/*
	 * Publishes a PERSISTENT message to a destination having this
	 * applicationMessageId
	 */
	private void pubMsg(Destination destination, String applicationMessageId) {

		print("About to publish");

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

		// Set the destination/topic
		txMessageHandle.setDestination(destination);

		txMessageHandle.setApplicationMessageId(applicationMessageId);

		/* Set the binary attachment. */
		txMessageHandle.setBinaryAttachment(txContentBuffer);

		/*
		 * After building a message, use sendMsg() to publish it. Malformed
		 * messages will result in a session event callback call with the error.
		 */
		rc = sessionHandle.send(txMessageHandle);
		assertReturnCode("SessionHandle.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		Queue tempQueue = null;

		try {

			// Init
			print(" Initializing the Java RTO Messaging API...");
			int rc = Solclient.init(new String[0]);
			assertReturnCode("Solclient.init()", rc, SolEnum.ReturnCode.OK);

			// Set a log level (not necessary as there is a default)
			Solclient.setLogLevel(logLevel);
			
			// Context
			print(" Creating a context ...");
			rc = Solclient.createContextForHandle(contextHandle, new String[0]);
			assertReturnCode("Solclient.createContext()", rc,
					SolEnum.ReturnCode.OK);

			// Session
			print(" Creating a session ...");
			String[] sessionProps = getSessionProps(config, 0);

			SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();
			MessageCallbackSample messageCallback = getMessageCallback(false);

			rc = contextHandle.createSessionForHandle(sessionHandle,
					sessionProps, messageCallback, sessionEventCallback);
			assertReturnCode("contextHandle.createSession()", rc,
					SolEnum.ReturnCode.OK);

			// Connect
			print(" Connecting session ...");
			rc = sessionHandle.connect();
			assertReturnCode("sessionHandle.connect()", rc,
					SolEnum.ReturnCode.OK);

			/************************************************************************
			 * Make a Temp Queue
			 ***********************************************************************/
			tempQueue = sessionHandle.createTemporaryQueue();

			/*************************************************************************
			 * Create a Flow
			 *************************************************************************/

			print("Bind to queue [" + tempQueue.getName() + "]");
			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[8];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			/* Set Acknowledge mode to CLIENT_ACK */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
			flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

			/* In a started state: for the tempQueue to be used */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.START_STATE;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			/* The selector. */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.SELECTOR;
			flowProperties[flowProps++] = "ApplicationMessageId='rotini' OR ApplicationMessageId='farfalle'";

			FlowEventCallback defaultFlowEventCallback = getDefaultFlowEventCallback();
			MessageCallbackSample tempQueueFlowMessageCallback = new MessageCallbackSample(
					tempQueue.getName());

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					tempQueue, null, tempQueueFlowMessageCallback,
					defaultFlowEventCallback);
			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			// Get the flow Destination (this should be the tempQueue)
			Destination flowDestination = flowHandle.getDestination();

			/*************************************************************************
			 * Wait for messages
			 *************************************************************************/

			print("Waiting for messages.....Expecting two messages to match the selector");

			/* Send message */
			pubMsg(flowDestination, "macaroni");
			pubMsg(flowDestination, "fettuccini");
			pubMsg(flowDestination, "farfalle"); /* Should match */
			pubMsg(flowDestination, "fiori");
			pubMsg(flowDestination, "rotini"); /* Should match */
			pubMsg(flowDestination, "penne");

			// wait for 5 seconds and exit
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			int expectedMatchingMessages = 2;

			if (tempQueueFlowMessageCallback.getMessageCount() != expectedMatchingMessages) {
				throw new IllegalStateException("Was expecting ["
						+ expectedMatchingMessages + "] messages, but I got ["
						+ tempQueueFlowMessageCallback.getMessageCount()
						+ "] instead!");
			}

			print("Test Passed I got the expected [" + expectedMatchingMessages
					+ "] message(s) based on the current selector!");

		} catch (Throwable t) {
			error("An error has occurred ", t);
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

		finish_DestroyHandle(flowHandle, "flowHandle");

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
		MessageSelectorsOnQueue sample = new MessageSelectorsOnQueue();
		sample.run(args);
	}

}
