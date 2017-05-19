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
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * SimpleFlowToQueue.java
 * 
 * This sample shows how to create message Flows to Queues. It demonstrates:
 * <ul>
 * <li>Binding to a Queue (temporary or durable)
 * <li>Client acknowledgement.
 * </ul>
 * 
 * For the case of a durable queue, this sample requires that a durable Queue
 * called 'my_sample_queue' be provisioned on the Appliance with at least
 * 'Consume' permissions.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class SimpleFlowToQueue extends AbstractSample {

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
			String[] flowProperties = new String[14];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			/*
			 * Client Acknowledgement, which means that the received messages on
			 * the Flow must be explicitly acked, otherwise they are be
			 * redelivered to the client when the Flow reconnects. The Client
			 * Acknowledgement was chosen to show this particular
			 * acknowledgement mode and that clients can use Auto
			 * Acknowledgement instead.
			 */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
			flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

			FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
			MessageCallbackSample flowMessageCallback = new MessageCallbackSample(
					"Flow");

			Queue queue = null;
			if (isDurable) {
				queue = Solclient.Allocator.newQueue(SampleUtils.SAMPLE_CONFIGURED_QUEUE, null);
			} else {
				// Create and provision a temporary queue
				queue = sessionHandle.createTemporaryQueue();
				flowProperties[flowProps++] =  Endpoint.PROPERTIES.DISCARD_BEHAVIOR;
				flowProperties[flowProps++] = SolEnum.EndpointDiscardBehavior.NOTIFY_SENDER_ON;
				
				flowProperties[flowProps++] = Endpoint.PROPERTIES.QUOTA_MB;
				flowProperties[flowProps++] = "100";
				
				flowProperties[flowProps++] = Endpoint.PROPERTIES.PERMISSION;
				flowProperties[flowProps++] = SolEnum.EndpointPermission.MODIFY_TOPIC;
				
				flowProperties[flowProps++] = Endpoint.PROPERTIES.MAXMSG_REDELIVERY;;
				flowProperties[flowProps++] =  "15";
				
				flowProperties[flowProps++] =  Endpoint.PROPERTIES.MAXMSG_SIZE;
				flowProperties[flowProps++] =  "500000";
			}

			print("Creating flow Queue Name [" + queue.getName()
					+ "] Durable? [" + queue.isDurable() + "]");

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					queue, null, flowMessageCallback, flowEventCallback);

			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Publish
			 *************************************************************************/

			int maxMessages = 10;

			print("Publishing [" + maxMessages + "] messages to Queue ["
					+ queue.getName() + "]");

			Destination destination = queue;

			/*
			 * Retrieve the temporary queue name from the Flow. NOTE:
			 * flowHandle.getDestination() can be used on temporary Queues
			 * or durable Flows. This sample demonstrates both.
			 */
			if (!isDurable) {
				destination = flowHandle.getDestination();
			}
			for (int publishCount = 0; publishCount < maxMessages; publishCount++) {

				print("Sending message " + publishCount);
				common_publishMessage(sessionHandle, txMessageHandle,
						destination, SolEnum.MessageDeliveryMode.PERSISTENT);

			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (flowMessageCallback.getMessageCount() != maxMessages) {
				throw new IllegalStateException(maxMessages
						+ " messages were expected, got ["
						+ flowMessageCallback.getMessageCount() + "] instead");
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
		SimpleFlowToQueue sample = new SimpleFlowToQueue();
		sample.run(args);
	}

}
