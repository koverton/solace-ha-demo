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
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.resource.TopicEndpoint;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * SimpleFlowToTopic.java
 * 
 * This sample shows the following:
 * <ul>
 * <li>Binding Flows to a Topic Endpoint (non-durable or durable)
 * <li>Auto-acknowledgement
 * </ul>
 * 
 * For the durable Topic Endpoint, a durable Topic Endpoint called
 * 'my_sample_topicendpoint' must be provisioned on the Appliance with at least
 * 'Modify Topic' permissions.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class SimpleFlowToTopic extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-d true|false]\t durable or non durable, default: false\n";
		System.out.println(usage);
		finish(1);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config,Level logLevel)
			throws SolclientException {

		try {

			// Default false
			boolean isDurable = false;

			String isDurableBoolStr = config.getArgBag().get("-d");
			if (isDurableBoolStr != null) {
				try {
					isDurable = Boolean.parseBoolean(isDurableBoolStr);
				} catch (Exception e) {
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
			 * Create a Flow
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[10];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			/*
			 * Send an ack when the message has been received. The default value
			 * is to automatically acknowledge on return from the message
			 * receive callback but it is recommended to use client
			 * aknowledgement when using flows.
			 */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
			flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

			FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
			MessageCallbackSample flowMessageCallback = new MessageCallbackSample(
					"Flow");

			TopicEndpoint te = null;
			Topic topic = null;
			if (isDurable) {
				te = Solclient.Allocator
						.newTopicEndpoint(SampleUtils.SAMPLE_CONFIGURED_TOPICENDPOINT, null);
				topic = Solclient.Allocator.newTopic(SampleUtils.SAMPLE_TOPIC);
			} else {
				te = sessionHandle.createNonDurableTopicEndpoint();
				topic = sessionHandle.createTemporaryTopic();
			}

			print("Creating flow using TopicEndpoint Name [" + te.getName()
					+ "] Durable? [" + te.isDurable() + "] Topic ["
					+ topic.getName() + "]");

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					te, topic, flowMessageCallback, flowEventCallback);
			
			print("Created flow for TopicEndpoint Name [" + te.getName()
					+ "] Durable? [" + te.isDurable() + "] Topic ["
					+ topic.getName() + "]");

			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Publish
			 *************************************************************************/

			int maxMessages = 10;

			if (isDurable) {
				print("Publishing [" + maxMessages
						+ "] messages to durable Topic Endpoint ["
						+ te.getName() + "] flowHandle.destination [" + flowHandle.getDestination().getName() + "]");
			} else {
				print("Publishing [" + maxMessages
						+ "] messages to a non-durable Topic Endpoint [" + te.getName() + "] flowHandle.destination [" + flowHandle.getDestination().getName() + "]");
			}
			
			

			for (int publishCount = 0; publishCount < maxMessages; publishCount++) {

				print("Sending message " + publishCount);
				common_publishMessage(sessionHandle, txMessageHandle, topic,
						SolEnum.MessageDeliveryMode.PERSISTENT);

			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (flowMessageCallback.getMessageCount() != maxMessages) {
				throw new IllegalStateException(maxMessages
						+ " messages were expected, got ["
						+ flowMessageCallback.getMessageCount() + "] instead");
			} else
				print("Test Passed");

			// Clean up
			try {
				flowHandle.destroy();
				print("flowHandle.destroy");
			} catch (Throwable t) {
				error("Unable to destroy a flowHandle ", t);
			}

			/*
			 * Durable Topic Endpoints continue getting messages on the
			 * registered Topic subscription if client applications do not
			 * unsubscribe. Non-durable Topic Endpoints are be cleaned up
			 * automatically after client applications dispose the Flows bound
			 * to them.
			 * 
			 * The following code block demonstrates how to unsubscribe or
			 * remove a subscribed Topic on the durable Topic Endpoint. Two
			 * conditions must be met: - The durable Topic Endpoint must have
			 * 'Modify Topic' permission enabled (at least). - No flows are
			 * currently bound to the durable Topic Endpoint in question.
			 */
			if (isDurable) {
				rc = sessionHandle.unsubscribe(te, 0);
				assertReturnCode("sessionHandle.unsubscribe", rc,
						SolEnum.ReturnCode.OK);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

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
		SimpleFlowToTopic sample = new SimpleFlowToTopic();
		sample.run(args);
	}

}
