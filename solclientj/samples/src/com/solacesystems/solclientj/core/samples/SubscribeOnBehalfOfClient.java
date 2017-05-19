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
import com.solacesystems.solclientj.core.resource.ClientName;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * SubscribeOnBehalfOfClient.java
 * 
 * This sample shows how to subscribe on behalf of another client. Doing so
 * requires knowledge of the target client name, as well as possession of the
 * subscription-manager permission.
 * 
 * Two Sessions are connected to the Appliance, their ClientNames are extracted,
 * and Session #1 adds a Topic subscription on behalf of Session #2. A message
 * is then published on that Topic, which will be received by Session #2.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class SubscribeOnBehalfOfClient extends AbstractSample {

	private SessionHandle managerSessionHandle = Solclient.Allocator
			.newSessionHandle();

	private SessionHandle clientSessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

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
		 * Create and connection a Session
		 *************************************************************************/
		print(" Creating solClient sessions.");

		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();
		MessageCallbackSample messageCallback = getMessageCallback(false);

		String[] commonSessionProps = getSessionProps(config, 0);
		rc = contextHandle.createSessionForHandle(managerSessionHandle,
				commonSessionProps, messageCallback, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - manager session", rc,
				SolEnum.ReturnCode.OK);

		/* Connect the "manager" Session. */
		rc = managerSessionHandle.connect();
		assertReturnCode("managerSessionHandle.connect()", rc,
				SolEnum.ReturnCode.OK);

		MessageCallbackSample countingMessageReceiveCallbackForClientSession = new MessageCallbackSample(
				"clientSession");

		/* Create the "client" Session. */
		rc = contextHandle.createSessionForHandle(clientSessionHandle,
				commonSessionProps,
				countingMessageReceiveCallbackForClientSession,
				sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - client session", rc,
				SolEnum.ReturnCode.OK);

		/* Connect the client Session. */
		rc = clientSessionHandle.connect();
		assertReturnCode("clientSessionHandle.connect()", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Checking on capability for manager session
		 *************************************************************************/
		print("Checking for capability SolEnum.CapabilityType.CAPABILITY_SUBSCRIPTION_MANAGER...");
		if (!managerSessionHandle
				.isCapable(SolEnum.CapabilityName.CAPABILITY_SUBSCRIPTION_MANAGER)) {
			throw new RuntimeException(
					"Required Capability is not present: Subscription Manager Not Supported. Exiting.");
		}

		/************************************************************************
		 * Get ClientName for the "client" Session
		 ************************************************************************/
		// This is the ClientName as a usable endpoint for subscription
		ClientName clientSessionClientName = clientSessionHandle
				.getClientName();
		if (clientSessionClientName == null
				|| clientSessionClientName.getName() == null)
			throw new IllegalStateException("Unable to determine ["
					+ SessionHandle.PROPERTIES.CLIENT_NAME + "]");

		/************************************************************************
		 * Get ClientName for the "manager" Session, using the property just to
		 * show it..
		 ************************************************************************/
		String managerSessionCLIENT_NAMEStr = managerSessionHandle
				.getProperty(SessionHandle.PROPERTIES.CLIENT_NAME);
		if (managerSessionCLIENT_NAMEStr == null)
			throw new IllegalStateException("Unable to determine ["
					+ SessionHandle.PROPERTIES.CLIENT_NAME + "]");

		/*************************************************************************
		 * Subscribe through the Session
		 *************************************************************************/

		print("Adding subscription [" + SampleUtils.SAMPLE_TOPIC
				+ "] by Subscription Manager [" + managerSessionCLIENT_NAMEStr
				+ "] on behalf Subscription Client ["
				+ clientSessionClientName.getName() + "]");

		rc = managerSessionHandle.subscribe(clientSessionClientName, topic,
				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
		assertReturnCode("managerSessionHandle.subscribe ", rc,
				SolEnum.ReturnCode.OK);

		/************************************************************************
		 * Send a message on the manager session and see it received on the
		 * client only
		 ***********************************************************************/
		common_publishMessage(managerSessionHandle, txMessageHandle, topic,
				SolEnum.MessageDeliveryMode.DIRECT);

		/* Wait a little */
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (countingMessageReceiveCallbackForClientSession.getMessageCount() != 1) {
			throw new IllegalStateException(
					"Messages not received by client session?");
		}

		print("Test Passed - Client Session Received ["
				+ countingMessageReceiveCallbackForClientSession
						.getMessageCount() + "] message(s)");

//		/*************************************************************************
//		 * Shows that client can unsubscribe
//		 *************************************************************************/
//		print("clientSessionHandle.unsubscribe");
//
//		rc = clientSessionHandle.unsubscribe(topic,
//				SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
//		assertReturnCode("clientSessionHandle.unsubscribe()", rc,
//				SolEnum.ReturnCode.OK, SolEnum.ReturnCode.OK);
//
//		/************************************************************************
//		 * Send another message on the manager session, it should not be
//		 * received on the client, as the client has unsubscribed..
//		 ***********************************************************************/
//		common_publishMessage(managerSessionHandle, txMessageHandle, topic,
//				SolEnum.MessageDeliveryMode.DIRECT);
//
//		/* Wait a little */
//		try {
//			Thread.sleep(1000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//
//		if (countingMessageReceiveCallbackForClientSession.getMessageCount() > 1) {
//			throw new IllegalStateException(
//					"Whoa! more messages received than expected !");
//		}
//
//		print("Test Passed - Client Session Received ["
//				+ countingMessageReceiveCallbackForClientSession
//						.getMessageCount() + "] message(s)");

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

		finish_Disconnect(managerSessionHandle);

		finish_DestroyHandle(managerSessionHandle, "managerSessionHandle");

		finish_Disconnect(clientSessionHandle);

		finish_DestroyHandle(clientSessionHandle, "clientSessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		SubscribeOnBehalfOfClient sample = new SubscribeOnBehalfOfClient();
		sample.run(args);
	}

}
