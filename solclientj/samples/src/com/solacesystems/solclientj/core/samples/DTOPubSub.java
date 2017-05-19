/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

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
 * DTOPubSub.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Publishing a message using Deliver-To-One (DTO).
 * <li>Subscribing to a Topic using DTO override to receive all messages.
 * </ul>
 * 
 * Sample Requirements:
 * <ul>
 * <li>A Solace Appliance running SolOS-TR
 * </ul>
 * 
 * In this sample, three Sessions to a SolOS-TR Appliance are created:
 * <ul>
 * 
 * <li>Session 1
 * <ul>
 * <li>Publish messages to a Topic with the DTO flag set.
 * <li>Subscribe to the Topic with DTO override set.
 * </ul>
 * <li>Session 2
 * <ul>
 * <li>Subscribe to the Topic.
 * </ul>
 * <li>Session 3
 * <ul>
 * <li>Subscribe to the Topic.
 * </ul>
 * </ul>
 * <p>
 * All Sessions subscribe to the same Topic. Therefore, with the DTO flag set on
 * messages being published, the Appliance delivers messages to Session 2 and
 * Session 3 in a round robin manner. In addition to delivering the message to
 * either Session 2 or Session 3, the Appliance delivers all messages to Session
 * 1.
 * 
 * <strong>Note: Session 1 is not part of the round robin to receive DTO
 * messages because its subscription uses DTO-override.</strong>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class DTOPubSub extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private SessionHandle sessionHandle1 = Solclient.Allocator
			.newSessionHandle();

	private SessionHandle sessionHandle2 = Solclient.Allocator
			.newSessionHandle();

	private SessionHandle sessionHandle3 = Solclient.Allocator
			.newSessionHandle();

	private static Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-n number]\t Number of messages to publish, default: 1\n";
		System.out.println(usage);
		finish(1);
	}

	/*****************************************************************************
	 * Publishes an empty message to the topic MY_SAMPLE_TOPIC. An empty message
	 * is used in this case because we only care about where it gets delivered,
	 * not what the contents are. The Deliver-To-One flag is set on the message
	 * before it is sent.
	 *****************************************************************************/
	public void publisheDtoMessage(SessionHandle sessionHandle) {
		int rc = 0;

		if (!txMessageHandle.isBound()) {
			// Allocate the message
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);
		}

		/* Set the destination. */
		txMessageHandle.setDestination(topic);

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.DIRECT);

		/*************************************************************************
		 * Enable Deliver-To-One (DTO)
		 ************************************************************************/
		txMessageHandle.setDeliverToOne(true);

		/* Send the message. */
		rc = sessionHandle.send(txMessageHandle);
		assertReturnCode("sessionHandle.send()", rc, SolEnum.ReturnCode.OK);

		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine the numberOfMessageToPublish
		int numberOfMessageToPublish = 10;

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

		/*************************************************************************
		 * Create and connect three Sessions
		 *************************************************************************/

		String[] sessionProps = getSessionProps(config, 0);
		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();

		MessageCallbackSample countingMessageReceiveCallback1 = new MessageCallbackSample(
				"DTO Override Session");

		print(" Creating solClient session 1.");
		rc = contextHandle.createSessionForHandle(sessionHandle1, sessionProps,
				countingMessageReceiveCallback1, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session 1", rc,
				SolEnum.ReturnCode.OK);

		// Connect Session 1
		print(" Connecting session 1 ...");
		rc = sessionHandle1.connect();
		assertReturnCode("sessionHandle1.connect()", rc, SolEnum.ReturnCode.OK);

		MessageCallbackSample countingMessageReceiveCallback2 = new MessageCallbackSample(
				"DTO Session 1");
		print(" Creating solClient session 2.");
		rc = contextHandle.createSessionForHandle(sessionHandle2, sessionProps,
				countingMessageReceiveCallback2, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session 2", rc,
				SolEnum.ReturnCode.OK);

		// Connect Session 2
		print(" Connecting session 2 ...");
		rc = sessionHandle2.connect();
		assertReturnCode("sessionHandle2.connect()", rc, SolEnum.ReturnCode.OK);

		MessageCallbackSample countingMessageReceiveCallback3 = new MessageCallbackSample(
				"DTO Session 2");
		print(" Creating solClient session 3.");
		rc = contextHandle.createSessionForHandle(sessionHandle3, sessionProps,
				countingMessageReceiveCallback3, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session 3", rc,
				SolEnum.ReturnCode.OK);

		// Connect Session 3
		print(" Connecting session 3 ...");
		rc = sessionHandle3.connect();
		assertReturnCode("sessionHandle3.connect()", rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Subscribe
		 *************************************************************************/
		/* Session 1: Subscription with DTO override enabled. */
		rc = sessionHandle1.subscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM
						| SolEnum.SubscribeFlags.RX_ALL_DELIVER_TO_ONE, 0);
		assertReturnCode("sessionHandle1.subscribe() ", rc,
				SolEnum.ReturnCode.OK);

		/* Session 2: Regular subscription. */
		rc = sessionHandle2.subscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("sessionHandle2.subscribe() ", rc,
				SolEnum.ReturnCode.OK);

		/* Session 3: Regular subscription. */
		rc = sessionHandle3.subscribe(topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("sessionHandle3.subscribe() ", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Publish on session 1
		 *************************************************************************/

		print("Publishing [" + numberOfMessageToPublish + "] messages");

		for (int i = 0; i < numberOfMessageToPublish; i++) {
			publisheDtoMessage(sessionHandle1);
		}

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Do some checks
		int actual = countingMessageReceiveCallback1.getMessageCount();
		int expected = numberOfMessageToPublish;
		assertExpectedCount("Session 1 message count", expected, actual);

		// Session 2 and 3
		actual = countingMessageReceiveCallback2.getMessageCount()
				+ countingMessageReceiveCallback3.getMessageCount();
		expected = numberOfMessageToPublish;
		assertExpectedCount("Session 2 and 3 message count", expected, actual);

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

		finish_Disconnect(sessionHandle1);

		finish_DestroyHandle(sessionHandle1, "sessionHandle1");

		finish_Disconnect(sessionHandle2);

		finish_DestroyHandle(sessionHandle2, "sessionHandle2");

		finish_Disconnect(sessionHandle3);

		finish_DestroyHandle(sessionHandle3, "sessionHandle3");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

/**
	 * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		DTOPubSub sample = new DTOPubSub();
		sample.run(args);
	}

}
