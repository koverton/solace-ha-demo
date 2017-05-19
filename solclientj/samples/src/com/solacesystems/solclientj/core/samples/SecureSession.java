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
 * SecureSession.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Subscribing to a Topic for Direct messages.
 * <li>Publishing Direct messages to a Topic.
 * <li>Receiving messages through a callback function.
 * <li>Explicitly configure session encryption properties
 * <li>Connecting a session to the Appliance using SSL over TCP
 * </ul>
 * 
 * This sample shows the basics of creating a Context, creating a Secure
 * Session, connecting a Session using SSL over TCP, subscribing to a Topic, and
 * publishing Direct messages to a Topic. It uses a message callback that simply
 * prints any received message to the screen.
 * <p>
 * A server certificate needs to be installed on the Appliance and SSL must be
 * enabled on the Appliance for this sample to work. Also, in order to connect
 * to the Appliance with Certificate Validation enabled (which is enabled by
 * default), the Appliance's certificate chain must be signed by one of the root
 * CAs in the trust store used by the sample.
 * <p>
 * For this sample to use CLIENT CERTIFICATE authentication, a trust store has
 * to be set up on the Appliance and it must contain the root CA that signed the
 * client certificate. The VPN must also have client-certificate authentication
 * enabled.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class SecureSession extends AbstractSample {

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

	private MessageCallbackSample messageCallback;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getSecureArgUsage();
		System.out.println(usage);
	}

	@Override
	public int parse(String[] args, ArgumentsParser parser) {
		return parser.parseSecureSampleArgs(args);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config,
			Level logLevel) throws SolclientException {

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
		// Get session configuration with spare room
		int spareRoom = 6;
		String[] sessionProps = getSessionProps(config, spareRoom);
		int sessionBPropsIndex = sessionProps.length - spareRoom;

		sessionProps[sessionBPropsIndex++] = SessionHandle.PROPERTIES.RECONNECT_RETRIES;
		sessionProps[sessionBPropsIndex++] = "3";

		sessionProps[sessionBPropsIndex++] = SessionHandle.PROPERTIES.CONNECT_RETRIES_PER_HOST;
		sessionProps[sessionBPropsIndex++] = "3";

		/*
		 * Note: Reapplying subscriptions allows Sessions to reconnect after
		 * failure and have all their subscriptions automatically restored. For
		 * Sessions with many subscriptions this can increase the amount of time
		 * required for a successful reconnect.
		 */

		sessionProps[sessionBPropsIndex++] = SessionHandle.PROPERTIES.REAPPLY_SUBSCRIPTIONS;
		sessionProps[sessionBPropsIndex++] = SolEnum.BooleanValue.ENABLE;

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
			content.rewind();
			content.put(("Hello from SecureSession " + i).getBytes(Charset
					.defaultCharset()));
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

		/*************************************************************************
		 * Unsubscribe
		 *************************************************************************/
		print(" Unsubscribing ...");
		rc = sessionHandle.unsubscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("sessionHandle.subscribe()", rc, SolEnum.ReturnCode.OK);

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
		SecureSession sample = new SecureSession();
		sample.run(args);
	}

}
