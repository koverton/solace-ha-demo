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
import com.solacesystems.solclientj.core.handle.MessageDispatchTargetHandle;
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
 * TopicToQueueMapping.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>adding a Topic to a Queue provisioned on a Appliance using the session
 * subscribe(Endpoint... ) function.
 * <li>adding a Topic to a Queue provisioned on a Appliance using the flow
 * subscribe(MessageDispatchTargetHandle....) function.
 * </ul>
 * 
 * Sample Requirements:
 * <ul>
 * <li>The Appliance connection must support adding Topics to Queues.
 * </ul>
 * 
 * 
 * In this sample, we create 1 session to a SolOS-TR Appliance:
 * <ul>
 * <li>create a durable Queue endpoint
 * <li>add Topic my/sample/topic/1 using session function
 * <li>add Topic my/sample/topic/2 using flow function
 * <li>add Topic my/sample/topic/3 using session function
 * <li>publish message to my/sample/topic/1 and verify receipt
 * <li>publish message to my/sample/topic/2 and verify receipt
 * <li>publish message to my/sample/topic/3 and verify receipt
 * </ul>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class TopicToQueueMapping extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private Queue queue = null;

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
			int spareRoom = 2;
			String[] sessionProps = getSessionProps(config, spareRoom);
			int sessionPropsIndex = sessionProps.length - spareRoom;
			/*************************************************************************
			 * Enable Topic Dispatch on the Session.
			 *************************************************************************/
			sessionProps[sessionPropsIndex++] = SessionHandle.PROPERTIES.TOPIC_DISPATCH;
			sessionProps[sessionPropsIndex++] = SolEnum.BooleanValue.ENABLE;

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

			/*************************************************************************
			 * Ensure Topic to Queue mapping is supported by this client
			 * connection
			 *************************************************************************/
			/*
			 * The same Appliance is used for all Sessions in this sample, so
			 * just check one.
			 */
			print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_QUEUE_SUBSCRIPTIONS...");
			if (!sessionHandle
					.isCapable(SolEnum.CapabilityName.CAPABILITY_QUEUE_SUBSCRIPTIONS)) {
				throw new RuntimeException(
						"Required Capability is not present: Topic To Queue Mapping is not supported on this client connection.");
			}

			/*************************************************************************
			 * Provision a Queue on the Appliance
			 *************************************************************************/

			queue = common_createQueue(sessionHandle, SampleUtils.SAMPLE_QUEUE);

			/*************************************************************************
			 * Subscribe through the Session
			 *************************************************************************/
			String topic1Str = SampleUtils.SAMPLE_TOPIC + "/1";
			Topic topic1 = Solclient.Allocator.newTopic(topic1Str);
			rc = sessionHandle.subscribe(queue, topic1,
					SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandle.subscribe", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Create a Flow to the Queue
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[2];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
			MessageCallbackSample flowMessageCallback = new MessageCallbackSample(
					"flowMessageCallback");

			print("Creating flow");

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					queue, null, flowMessageCallback, flowEventCallback);

			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Subscribe to the second Topic through the Flow. Regardless of the
			 * dispatch callback, this still adds a subscription to Queue.
			 *************************************************************************/

			String topic2Str = SampleUtils.SAMPLE_TOPIC + "/2";
			Topic topic2 = Solclient.Allocator.newTopic(topic2Str);

			print("Adding subscription [" + topic2Str + "] to queue ["
					+ queue.getName() + "] via flow.");

			MessageCallbackSample messageDispatchTargetCallback = new MessageCallbackSample(
					"messageDispatchTargetCallback");

			MessageDispatchTargetHandle messageDispatchTargetHandleTopic2 = Solclient.Allocator
					.newMessageDispatchTargetHandle(topic2,
							messageDispatchTargetCallback, false);

			rc = flowHandle.subscribe(messageDispatchTargetHandleTopic2,
					SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("flowHandle.subscribe", rc, SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Subscribe to the third Topic through the Session.
			 *************************************************************************/

			String topic3Str = SampleUtils.SAMPLE_TOPIC + "/3";
			Topic topic3 = Solclient.Allocator.newTopic(topic3Str);

			print("Adding subscription [" + topic3Str + "] to queue ["
					+ queue.getName() + "] via session.");

			rc = sessionHandle.subscribe(queue, topic3,
					SolEnum.SubscribeFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandle.subscribe", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Publish a message on each Topic
			 *************************************************************************/

			print("Publishing 3 messages, expect 2 message received on flow and 1 message received on MessageDispatchTarget");

			common_publishMessage(sessionHandle, txMessageHandle, topic1,
					SolEnum.MessageDeliveryMode.PERSISTENT);

			common_publishMessage(sessionHandle, txMessageHandle, topic2,
					SolEnum.MessageDeliveryMode.PERSISTENT);

			common_publishMessage(sessionHandle, txMessageHandle, topic3,
					SolEnum.MessageDeliveryMode.PERSISTENT);

			/* pause to let callback receive messages */
			Thread.sleep(4000);

			if ((messageDispatchTargetCallback.getMessageCount() + flowMessageCallback
					.getMessageCount()) != 3) {
				throw new IllegalStateException(
						"3 messages were expected, got messageDispatchTargetCallback ["
								+ messageDispatchTargetCallback
										.getMessageCount()
								+ "] flowMessageCallback ["
								+ flowMessageCallback.getMessageCount()
								+ "] instead");
			} else
				print("Test Passed");

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

		finish_Deprovision(queue, sessionHandle);

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
		TopicToQueueMapping sample = new TopicToQueueMapping();
		sample.run(args);
	}

}
