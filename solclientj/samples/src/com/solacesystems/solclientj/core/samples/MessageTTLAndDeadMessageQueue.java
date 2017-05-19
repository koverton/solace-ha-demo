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
 * 
 * MessageTTLAndDeadMessageQueue.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Setting TimeToLive on a published message.
 * <li>Setting Dead Message Queue Eligible on a published message.
 * <li>Publishing messages with and without a Time-to-Live (TTL) and verifying
 * expected results.
 * </ul>
 * Sample Requirements:
 * <ul>
 * <li>A Solace running SolOS-TR
 * </ul>
 * 
 * 
 * In this sample, a Session to a SolOS-TR Appliance is created and then the
 * following tasks are performed:
 * <ul>
 * <li>create a durable Queue endpoint
 * <li>create a Dead Message Queue (DMQ)
 * <li>publish three messages with TTL=0
 * <li>publish one message with TTL=3000, DMQ=FALSE
 * <li>publish one message with TTL=3000, DMQ=TRUE
 * <li>bind to the Queue and verify all that five messages were received
 * <li>flow control queue (solClient_flow_stop)
 * <li>publish five messages again
 * <li>wait five seconds
 * <li>open a transport window to Queue (solClient_flow_start)
 * <li>verify only three messages are received
 * <li>bind to the Dead Message Queue
 * <li>verify that one message was received
 * <li>remove the durable Queue and DMQ
 * </ul>
 * 
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class MessageTTLAndDeadMessageQueue extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();
	private FlowHandle dmqFlowHandle = Solclient.Allocator.newFlowHandle();

	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private boolean keepRxMsgs = false;

	Queue testq = null;

	Queue dmq = null;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		System.out.println(usage);
		finish(1);
	}

	/*****************************************************************************
	 * publishMessageWithTTL
	 * 
	 * Publishes an empty message to the Topic MY_SAMPLE_TOPIC. An empty message
	 * is used because we only care about whether it gets delivered and not what
	 * the contents are. Set TTL and DMQE in the message.
	 * 
	 *****************************************************************************/
	private void publishMessageWithTTL(int ttl, boolean dmqEligible) {

		print("About to publish\n");

		int rc = 0;

		if (!txMessageHandle.isBound()) {
			// Allocate the message
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);
		}

		/* Set the message delivery mode. */
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.NONPERSISTENT);

		// Set the destination/topic
		txMessageHandle.setDestination(topic);

		txMessageHandle.setTimeToLive(ttl);
		txMessageHandle.setDMQEligible(dmqEligible);

		/* Send the message. */
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
			MessageCallbackSample messageCallback = getMessageCallback(keepRxMsgs);

			rc = contextHandle.createSessionForHandle(sessionHandle,
					sessionProps, messageCallback, sessionEventCallback);
			assertReturnCode("contextHandle.createSession()", rc,
					SolEnum.ReturnCode.OK);

			// Connect
			print(" Connecting session ...");
			rc = sessionHandle.connect();
			assertReturnCode("sessionHandle.connect()", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Ensure that TTL is supported on the Appliance
			 *************************************************************************/
			/* The same Appliance is used for all Sessions, just check one. */
			print("Checking for capability SolEnum.CapabilityType.CAPABILITY_ENDPOINT_MESSAGE_TTL...");
			if (!sessionHandle
					.isCapable(SolEnum.CapabilityName.CAPABILITY_ENDPOINT_MESSAGE_TTL)) {
				throw new RuntimeException(
						"Required Capability is not present: Time to live is not supported by Appliance. Exiting.");
			}
			print("OK");

			/************************************************************************
			 * Provision a Queue on the Appliance
			 ***********************************************************************/
			testq = common_createQueue(sessionHandle, SampleUtils.SAMPLE_QUEUE);

			/************************************************************************
			 * Provision a Dead Message Queue on the Appliance
			 ***********************************************************************/
			dmq = common_createQueue(sessionHandle, SampleUtils.COMMON_DMQ_NAME);

			/*************************************************************************
			 * Subscribe through the Session
			 *************************************************************************/
			print("Adding subscription [" + topic.getName() + "] to queue ["
					+ testq.getName() + "] through Session.");
			rc = sessionHandle.subscribe(testq, topic,
					SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandle.subscribe ", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Publish three messages without a TTL or DMQ
			 *************************************************************************/

			print("Publishing three messages without TTL and DMQ\n");

			for (int i = 0; i < 3; i++) {
				publishMessageWithTTL(0, false);
			}

			/*************************************************************************
			 * Publish a message with TTL=3000 and DMQ=FALSE
			 ************************************************************************/

			print("Publishing message with TTL=3000 ms and DMQ Eligible=FALSE\n");
			publishMessageWithTTL(3000, false);

			/*************************************************************************
			 * Publish a message with TTL=3000 and DMQ=TRUE
			 ************************************************************************/

			print("Publishing message with TTL=3000 ms and DMQ Eligible=TRUE\n");
			publishMessageWithTTL(3000, true);

			/*************************************************************************
			 * Create a Flow to the Queue
			 *************************************************************************/
			print("Bind to queue [" + testq.getName() + "]");
			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[2];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			FlowEventCallback defaultFlowEventCallback = getDefaultFlowEventCallback();
			MessageCallbackSample testqFlowMessageCallback = new MessageCallbackSample(
					testq.getName());

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					testq, null, testqFlowMessageCallback,
					defaultFlowEventCallback);
			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Create a Flow to the Dead Message Queue
			 *************************************************************************/

			print("Bind to queue [" + dmq.getName() + "]");

			MessageCallbackSample dmqFlowMessageCallback = new MessageCallbackSample(
					dmq.getName());

			rc = sessionHandle.createFlowForHandle(dmqFlowHandle,
					flowProperties, dmq, null, dmqFlowMessageCallback,
					defaultFlowEventCallback);
			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/* Wait for up to 250 milliseconds for the messages to be received. */
			int x = 0;
			while ((testqFlowMessageCallback.getMessageCount() != 5) && (x < 8)) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				x++;
			}

			if (testqFlowMessageCallback.getMessageCount() != 5) {
				throw new IllegalStateException(
						testqFlowMessageCallback.getMessageCount()
								+ " messages received on flow, 5 messages expected");
			}

			if (dmqFlowMessageCallback.getMessageCount() != 0) {
				throw new IllegalStateException(
						dmqFlowMessageCallback.getMessageCount()
								+ " messages received on DMQ, no messages expected");
			}

			print("All sent messages received");

			/*************************************************************************
			 * Stop the Flow for TTL and DMQ tests
			 *************************************************************************/

			rc = flowHandle.stop();
			assertReturnCode("flowHandle.stop", rc, SolEnum.ReturnCode.OK);

			testqFlowMessageCallback.setMessageCount(0);

			/*************************************************************************
			 * Publish three messages without TTL and DMQ
			 *************************************************************************/

			print("Resend 3 messages\n");

			for (int i = 0; i < 3; i++) {
				publishMessageWithTTL(0, false);
			}

			/*************************************************************************
			 * Publish a message with TTL=3000 and DMQ=FALSE
			 ************************************************************************/
			publishMessageWithTTL(3000, false);

			/*************************************************************************
			 * Publish a message with TTL=3000 and DMQ=TRUE
			 ************************************************************************/
			publishMessageWithTTL(3000, true);

			print("Wait five seconds to allow messages to expire\n");
			/* Wait for messages to expire */
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			/* Should have received one message on DMQ */
			if (dmqFlowMessageCallback.getMessageCount() != 1) {
				throw new IllegalStateException(
						dmqFlowMessageCallback.getMessageCount()
								+ " messages received on DMQ, 1 messages expected");
			}

			/*
			 * Restart the Flow to Queue and look for three messages that did
			 * not have TTLs.
			 */

			rc = flowHandle.start();
			assertReturnCode("flowHandle.start", rc, SolEnum.ReturnCode.OK);

			/* Wait up to a few seconds for messages to be received. */
			x = 0;
			while ((testqFlowMessageCallback.getMessageCount() != 3) && (x < 8)) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				x++;
			}

			if (testqFlowMessageCallback.getMessageCount() != 3) {
				throw new IllegalStateException(
						testqFlowMessageCallback.getMessageCount()
								+ " messages received on flow, 3 messages expected");

			}

			print("Test Passed: Three messages with no TTL received and one message received on Dead Message Queue as expected");

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

		/*
		 * Destroy the Flow before deleting the Queue or else the API will log
		 * at NOTICE level for the unsolicited unbind.
		 */
		finish_DestroyHandle(flowHandle, "flowHandle");

		finish_DestroyHandle(dmqFlowHandle, "dmqFlowHandle");

		finish_Deprovision(testq, sessionHandle);

		finish_Deprovision(dmq, sessionHandle);

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
		MessageTTLAndDeadMessageQueue sample = new MessageTTLAndDeadMessageQueue();
		sample.run(args);
	}

}
