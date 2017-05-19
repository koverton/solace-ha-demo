/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

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
import com.solacesystems.solclientj.core.handle.TransactedSessionHandle;
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * Transactions.java
 * 
 * This sample uses a simple request/reply scenario to show the use of
 * transactions.
 * 
 * 
 * <dl>
 * <dt>TransactedRequestor
 * <dd>A transacted session and a transacted consumer flow are created for the
 * TransactedRequestor. The TransactedRequestor sends a request message and
 * commits the transaction. It then waits up to 10s for a reply message. It
 * commits the transaction again after it receives a reply message.
 * <dt>TransactedReplier
 * <dd>A transacted session and a transacted consumer flow are created for the
 * TransactedReplier. When the TransactedReplier receives a request message, it
 * sends a reply message and then commits the transaction.
 * </dl>
 * 
 * <pre>
 *  |---------------------|  -------- RequestTopic ---------> |--------------------|
 *  | TransactedRequestor |                                   | TransactedReplier  |
 *  |---------------------|  <-------- ReplyQueue ----------  |--------------------|
 * </pre>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class Transactions extends AbstractSample {

	private static SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private TransactedSessionHandle replierTransactedSessionHandle = Solclient.Allocator
			.newTransactedSessionHandle();

	private TransactedSessionHandle requestorTransactedSessionHandle = Solclient.Allocator
			.newTransactedSessionHandle();

	private FlowHandle replierFlowHandle = Solclient.Allocator.newFlowHandle();
	private FlowHandle requestorFlowHandle = Solclient.Allocator
			.newFlowHandle();

	private static MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private static MessageHandle rxMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private Endpoint endpoint;

	boolean endpointProvisioned = false;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-t topic]\t Topic, default:" + SampleUtils.SAMPLE_TOPIC
				+ "\n";
		System.out.println(usage);
		finish(1);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config,
			Level logLevel) throws SolclientException {

		String topicName = config.getArgBag().get("-t");
		if (topicName == null) {
			print(" No topic was provided, using default...");
			topicName = SampleUtils.SAMPLE_TOPIC;
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
		String[] sessionProps = getSessionProps(config, 0);
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
		 * Ensure Transacted Session is enabled on Appliance
		 *************************************************************************/

		print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_TRANSACTED_SESSION...");
		if (!sessionHandle
				.isCapable(SolEnum.CapabilityName.CAPABILITY_TRANSACTED_SESSION)) {
			throw new RuntimeException(
					"Required Capability is not present: Transacted session not supported.");
		}

		/*************************************************************************
		 * Ensure the endpoint provisioning is supported
		 *************************************************************************/
		print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_ENDPOINT_MANAGEMENT...");
		if (!sessionHandle
				.isCapable(SolEnum.CapabilityName.CAPABILITY_ENDPOINT_MANAGEMENT)) {
			throw new RuntimeException(
					"Required Capability is not present: Endpoint management not supported.");
		}

		/****************************************************************
		 * Create a Transacted Session for TransactedReplier
		 ***************************************************************/
		String[] replierTransactedSessionProperties = null;
		rc = sessionHandle.createTransactedSessionForHandle(
				replierTransactedSessionHandle,
				replierTransactedSessionProperties);
		assertReturnCode("sessionHandle.createTransactedSessionForHandle()",
				rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Provision a Topic Endpoint for the request topic on Appliance
		 *************************************************************************/

		print("Will be using topic [" + topicName + "]");
		Topic topic = Solclient.Allocator.newTopic(topicName);

		int endpointPropsIndex = 0;

		String[] endpointProperties = new String[4];

		endpointProperties[endpointPropsIndex++] = Endpoint.PROPERTIES.PERMISSION;
		endpointProperties[endpointPropsIndex++] = SolEnum.EndpointPermission.MODIFY_TOPIC;

		endpointProperties[endpointPropsIndex++] = Endpoint.PROPERTIES.QUOTA_MB;
		endpointProperties[endpointPropsIndex++] = "100";

		print("Will provision a topicEndpoint ["
				+ SampleUtils.SAMPLE_TOPIC + "]");
		endpoint = Solclient.Allocator.newTopicEndpoint(
				SampleUtils.SAMPLE_TOPIC, endpointProperties);

		print("Provisioning endpoint [" + endpoint.getName() + "]");

		// EndPoint Properties

		/* Try to provision the endpoint. */
		rc = sessionHandle.provision(endpoint,
				SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM
						| SolEnum.ProvisionFlags.IGNORE_EXIST_ERRORS, 0);
		assertReturnCode("sessionHandle.provision()", rc, SolEnum.ReturnCode.OK);

		endpointProvisioned = true;

		/*************************************************************************
		 * Create a Transacted Consumer Flow with a Rx message callback for
		 * TransactedReplier
		 *************************************************************************/

		// Flow Properties
		int replierFlowProps = 0;
		String[] replierFlowProperties = new String[2];

		replierFlowProperties[replierFlowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
		replierFlowProperties[replierFlowProps++] = SolEnum.BooleanValue.ENABLE;

		FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
		ReplierFlowRxMsgCallback replierFlowRxMsgCallback = new ReplierFlowRxMsgCallback();

		rc = replierTransactedSessionHandle.createFlowForHandle(
				replierFlowHandle, replierFlowProperties, endpoint, topic,
				replierFlowRxMsgCallback, flowEventCallback);
		assertReturnCode("transactedSessionHandle.createFlowForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Create a Transacted Session for TransactedRequestor
		 ***********************************************************************/

		String[] requestorTransactedSessionProperties = null;
		rc = sessionHandle.createTransactedSessionForHandle(
				requestorTransactedSessionHandle,
				requestorTransactedSessionProperties);
		assertReturnCode("sessionHandle.createTransactedSessionForHandle()",
				rc, SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Create a Temporary Queue and a Transacted Consumer Flow without
		 * specifying a Rx message callback for the TransactedRequestor
		 *************************************************************************/

		Queue tempQueue = sessionHandle.createTemporaryQueue();

		// Flow Properties
		int requestorFlowProps = 0;
		String[] requestorFlowProperties = new String[10];

		requestorFlowProperties[requestorFlowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
		requestorFlowProperties[requestorFlowProps++] = SolEnum.BooleanValue.ENABLE;

		/* No Rx message callback */
		rc = requestorTransactedSessionHandle.createFlowForHandle(
				requestorFlowHandle, requestorFlowProperties, tempQueue, null,
				null, flowEventCallback);

		assertReturnCode("transactedSessionHandle.createFlowForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/* Allocate a request Message */
		rc = Solclient.createMessageForHandle(txMessageHandle);
		assertReturnCode("Solclient.createMessageForHandle()", rc,
				SolEnum.ReturnCode.OK);

		/* Set the request message Delivery Mode */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

		/* Set the request message SenderId */
		String senderId = "Requestor";
		txMessageHandle.setSenderId(senderId);

		/* Set the request message Destination */
		txMessageHandle.setDestination(topic);

		/* Use the tempQueue as replyTo */
		Destination replyTo = tempQueue; // requestorFlowHandle.getDestination();

		/* set request message ReplyTo address */
		txMessageHandle.setReplyTo(replyTo);

		/* Send the request message */
		rc = requestorTransactedSessionHandle.send(txMessageHandle);
		assertReturnCode("TransactedSessionHandle.send()", rc,
				SolEnum.ReturnCode.OK);

		print("Requestor sends a request message on topic [" + topicName
				+ "] and then commits the transaction");

		/* Commit the Transaction for the request message */
		rc = requestorTransactedSessionHandle.commit();
		assertReturnCode("TransactedSessionHandle.commit()", rc,
				SolEnum.ReturnCode.OK);

		/*
		 * Wait up to 10s for a reply message.<br> 
		 * As indicated in FlowHandle:
		 * The receive operation is only supported when a FlowHandle is obtained
		 * from a TransactedSession
		 */
		rc = requestorFlowHandle.receive(rxMessageHandle, 10000);
		assertReturnCode("FlowHandle.receive()", rc, SolEnum.ReturnCode.OK);

		print("Requestor receives a reply message and commits the transaction.");

		/* Free the received message */
		rxMessageHandle.destroy();
		print("Free the received message");

		/* Commit the transaction for the reply message */
		rc = requestorTransactedSessionHandle.commit();
		assertReturnCode("TransactedSessionHandle.commit()", rc,
				SolEnum.ReturnCode.OK);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

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

		finish_DestroyHandle(requestorFlowHandle, "requestorFlowHandle");

		finish_DestroyHandle(replierFlowHandle, "replierFlowHandle");

		finish_DestroyHandle(requestorTransactedSessionHandle,
				"requestorTransactedSessionHandle");

		finish_DestroyHandle(replierTransactedSessionHandle,
				"replierTransactedSessionHandle");

		if (endpointProvisioned)
			finish_Deprovision(endpoint, sessionHandle);

		finish_DestroyHandle(txMessageHandle, "messageHandle");

		finish_Disconnect(sessionHandle);

		finish_DestroyHandle(sessionHandle, "sessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

	public static class ReplierFlowRxMsgCallback implements MessageCallback {

		private int messageCount = 0;
		private TransactedSessionHandle transactedSessionHandle = Solclient.Allocator
				.newTransactedSessionHandle();
		private MessageHandle replyMessageHandle = Solclient.Allocator
				.newMessageHandle();

		public ReplierFlowRxMsgCallback() {
		}

		@Override
		public void onMessage(Handle handle) {

			FlowHandle flowHandle = (FlowHandle) handle;
			MessageHandle rxMessage = flowHandle.getRxMessage();

			/* Get the SenderId */
			String senderId = rxMessage.getSenderId();

			/* Get ReplyTo address, this is only done to demonstrate what is happening */
			Destination replyTo = rxMessage.getReplyTo();

			/* Get the flow's Transacted Session Handle */
			transactedSessionHandle = flowHandle.getTransactedSession();

			print("Replier receives a request message from [" + senderId
					+ "]. It sends a reply message to [" + replyTo.getName()
					+ "] and then commits the transaction");

			/* Create a reply message */
			int rc = Solclient.createMessageForHandle(replyMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);

			/* Set the reply message Destination by copying the ReplyTo of rxMessage */
			replyMessageHandle.setDestinationFromMessageReplyTo(rxMessage);
			/* Set the CorrelationId by copying from rxMessage */
			replyMessageHandle.setCorrelationIdFromMessage(rxMessage);

			/* Set the reply message Delivery Mode */
			replyMessageHandle
					.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

			/* Send the reply message */
			rc = transactedSessionHandle.send(replyMessageHandle);
			assertReturnCode("transactedSessionHandle.send", rc,
					SolEnum.ReturnCode.OK);

			/* commit */
			rc = transactedSessionHandle.commit();
			assertReturnCode("transactedSessionHandle.commit", rc,
					SolEnum.ReturnCode.OK);

			replyMessageHandle.destroy();

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
		Transactions sample = new Transactions();
		sample.run(args);
	}

}
