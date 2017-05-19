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
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.Handle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.MessageSupport;
import com.solacesystems.solclientj.core.handle.MutableLong;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * JMSHeaders.java
 * 
 * This sample demonstrates that JMS related headers are usable when sending and recieving a message.
 * The samples does this by:
 * <ul>
 * <li>Subscribing to a topic for direct messages.
 * <li>Publishing direct messages to a topic.
 * <li>Receiving messages with a message handler.
 * <li>Using various JMS Header message fields, the
 * publisher setting them, the subscriber printing them.
 * <ul>
 * <li>Correlation ID
 * <li>Expiration
 * <li>Application Message Id
 * <li>ReplyTo
 * <li>Sender Timestamp
 * <li>Application Message Type
 * </ul>
 * </ul>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class JMSHeaders extends AbstractSample {

	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private Topic replyTopic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC + "/reply");

	
	private int numOfMessages = 10;
	private ByteBuffer content = ByteBuffer.allocateDirect(200);

	private static String MyApplicationMessageType = "JMSHeaderApplication";

	JMSHeaderCheckingMessageCallbackSample messageCallback = new JMSHeaderCheckingMessageCallbackSample();

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		System.out.println(usage);
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
		int spareRoom = 2;
		String[] sessionProps = getSessionProps(config, spareRoom);
		int sessionPropsIndex = sessionProps.length - spareRoom;

		// Enables generating a sender timestamp
		sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.GENERATE_SEND_TIMESTAMPS;
		sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

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

		for (int i = 0; i < numOfMessages; i++) {

			// create the content (as a binary attachment)
			content.clear();
			content.put(("Hello from JMSHeaders " + i).getBytes(Charset
					.defaultCharset()));
			content.flip();

			// Set content and destination on the message
			txMessageHandle.setBinaryAttachment(content);

			// A made up correlationId
			String correlationId = "CorrelationIdPrefix"	+ i;
			// A made up Application Id
			String applicationMessageId = MyApplicationMessageType	+ i;
			
			txMessageHandle.setCorrelationId(correlationId);
			
			// 2 seconds in the future
			txMessageHandle.setExpiration(System.currentTimeMillis() + 2000);
			
			// Set a ReplyTo
			txMessageHandle.setReplyTo(replyTopic);

			txMessageHandle.setApplicationMessageId(applicationMessageId);

			txMessageHandle.setApplicationMessageType(MyApplicationMessageType);


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
		JMSHeaders sample = new JMSHeaders();
		sample.run(args);
	}

	public static class JMSHeaderCheckingMessageCallbackSample implements
			MessageCallback {

		private int messageCount = 0;
		MutableLong senderTimestamp = new MutableLong();

		@Override
		public void onMessage(Handle handle) {
			messageCount++;
			MessageSupport messageSupport = (MessageSupport) handle;
			MessageHandle rxMessage = messageSupport.getRxMessage();

			// Just print the JMS related message header fields..
			
			System.out.println("CorrelationId\t\t" + rxMessage.getCorrelationId());
			System.out.println("Expiration\t\t" + rxMessage.getExpiration());
			System.out.println("ApplicationMessageId\t" +  rxMessage.getApplicationMessageId());
			System.out.println("ReplyTo\t\t\t" +  rxMessage.getReplyTo().getName());
			rxMessage.getSenderTimestamp(senderTimestamp);
			System.out.println("SenderTimestamp\t\t" +  senderTimestamp.getValue() );
			System.out.println("ApplicationMessageType\t" + rxMessage.getApplicationMessageType());
			
		}

		public int getMessageCount() {
			return messageCount;
		}

		public void setMessageCount(int messageCount) {
			this.messageCount = messageCount;
		}

	}

}
