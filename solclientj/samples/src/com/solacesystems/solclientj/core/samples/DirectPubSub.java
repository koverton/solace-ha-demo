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
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * DirectPubSub.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Subscribing to a topic for direct messages.
 * <li>Publishing direct messages to a topic.
 * <li>Receiving messages with a message handler.
 * </ul>
 * 
 * <p>
 * This sample shows the basics of creating a context, creating a session,
 * connecting a session, subscribing to a topic, and publishing direct messages
 * to a topic. This is meant to be a very basic example, so there are minimal
 * session properties and a message handler that simply prints any received
 * message to the screen.
 * 
 * <p>
 * Although other samples make use of common code to perform some of the most
 * common actions, many of those common methods are explicitly included in this
 * sample to emphasize the most basic building blocks of any application.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class DirectPubSub extends AbstractSample {

	private boolean keepRxMsgs = false;
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private int numOfMessages = 10;
	private ByteBuffer content = ByteBuffer.allocateDirect(200);

	MessageCallbackSample messageCallback;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		System.out.println(usage);
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

		// Session
		print(" Creating a session ...");
		String[] sessionProps = getSessionProps(config, 0);
		messageCallback = getMessageCallback(keepRxMsgs);
		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();
		rc = contextHandle.createSessionForHandle(sessionHandle, sessionProps,
				messageCallback, sessionEventCallback);
		assertReturnCode("contextHandle.createSession()", rc,
				SolEnum.ReturnCode.OK);

		// Connect
		print(" Connecting session ...");
		rc = sessionHandle.connect();
		assertReturnCode("sessionHandle.connect()", rc, SolEnum.ReturnCode.OK);

		// Subscribe
		print(" Adding subscription ...");
		rc = sessionHandle.subscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("sessionHandle.subscribe()", rc, SolEnum.ReturnCode.OK);

		// Allocate the message
		rc = Solclient.createMessageForHandle(txMessageHandle);
		assertReturnCode("Solclient.createMessage()", rc, SolEnum.ReturnCode.OK);
		txMessageHandle.setDestination(topic);

		// Send
		for (int i = 0; i < numOfMessages; i++) {

			// create the content (as a binary attachment)
			content.clear();
			content.put(("Hello from DirectPubSub " + i).getBytes(Charset.defaultCharset()));
			content.flip();

			// Set content and destination on the message
			txMessageHandle.setBinaryAttachment(content);

			// Send it
			rc = sessionHandle.send(txMessageHandle);
			assertReturnCode("sessionHandle.send()", rc, SolEnum.ReturnCode.OK,
					SolEnum.ReturnCode.IN_PROGRESS);

			// Wait
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertExpectedCount("Received messages count", numOfMessages,
				messageCallback.getMessageCount());
		print("Test Passed");

	}

	/**
	 * Invoked when the sample finishes
	 */
	@Override
	protected void finish(int status) {

		/*************************************************************************
		 * Cleanup
		 *************************************************************************/

		if (messageCallback != null) {
			try {
				messageCallback.destroy();
			} catch (Throwable t) {
				error("Unable to call destroy on messageCallback ", t);
			}
		}

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
		DirectPubSub sample = new DirectPubSub();
		sample.run(args);
	}

}
