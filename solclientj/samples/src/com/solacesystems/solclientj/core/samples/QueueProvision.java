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
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * QueueProvision.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Provisioning a durable Queue on the Appliance.
 * <li>Binding a Flow to the provisioned Queue and receiving messages from it.
 * <li>Publishing messages to the provisioned Queue.
 * </ul>
 * 
 * Sample Requirements:
 * <ul>
 * <li>SolOS Appliance supporting Queue provisioning.
 * </ul>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class QueueProvision extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private Queue queue = null;

	private boolean keepRxMsgs = false;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-n number]\t Number of messages to publish, default: 1\n";
		System.out.println(usage);
		finish(1);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		try {

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
			 * Ensure the endpoint provisioning is supported
			 *************************************************************************/
			print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_ENDPOINT_MANAGEMENT...");
			if (!sessionHandle
					.isCapable(SolEnum.CapabilityName.CAPABILITY_ENDPOINT_MANAGEMENT)) {
				throw new RuntimeException(
						"Required Capability is not present: Endpoint management not supported.");
			}

			/*************************************************************************
			 * Provision Queue
			 *************************************************************************/

			// Make a name
			String provQueueName = "sample_queue_Provision__"
					+ System.currentTimeMillis() % 100000;

			print("Provisioning queue " + provQueueName + "] ...");



			// Queue Properties

			int queueProps = 0;

			String[] queueProperties = new String[10];

			queueProperties[queueProps++] = Endpoint.PROPERTIES.PERMISSION;
			queueProperties[queueProps++] = SolEnum.EndpointPermission.MODIFY_TOPIC;

			queueProperties[queueProps++] = Endpoint.PROPERTIES.QUOTA_MB;
			queueProperties[queueProps++] = "100";

			queueProperties[queueProps++] = Endpoint.PROPERTIES.MAXMSG_REDELIVERY;
			queueProperties[queueProps++] = "15";
			
			// The Queue with name
			queue = Solclient.Allocator.newQueue(provQueueName,queueProperties);

			rc = sessionHandle.provision(queue, SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandle.provision()", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Bind a Flow to the provisioned endpoint
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[10];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.DISABLE;

			/* Set Acknowledge mode to CLIENT_ACK */

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
			flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

			FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();

			/*************************************************************************
			 * Creating flow
			 *************************************************************************/
			print("Creating flow");

			// Get a Handle flow

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					queue, null, messageCallback, flowEventCallback);

			assertReturnCode("sessionHandle.createFlowForHandle()", rc,
					SolEnum.ReturnCode.IN_PROGRESS);

			// Publish PERSISTENT messages to Queue
			for (int i = 0; i < numberOfMessageToPublish; i++) {
				common_publishMessage(sessionHandle, txMessageHandle, queue,
						SolEnum.MessageDeliveryMode.PERSISTENT);
				try {
					Thread.sleep(200);
				} catch (InterruptedException ie) {
				}
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException ie) {
			}

			assertExpectedCount("Received Message Count",
					numberOfMessageToPublish, messageCallback.getMessageCount());

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
		QueueProvision sample = new QueueProvision();
		sample.run(args);
	}

}
