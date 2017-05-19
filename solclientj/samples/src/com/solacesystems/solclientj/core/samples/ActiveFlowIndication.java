/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.FlowEvent;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * ActiveFlowIndication.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Provisioning an exclusive durable Queue on the Appliance.
 * <li>Binding a first Flow to the provisioned Queue and receiving a
 * SolEnum.FlowEventCode.ACTIVE event
 * <li>Binding a second Flow to the provisioned Queue and receiving a
 * SolEnum.FlowEventCode.UP_NOTICE event only.
 * <li>Closing the first Flow and receiving a SolEnum.FlowEventCode.ACTIVE event
 * for the second Flow.
 * <li>Cleanup and deprovisioning an exclusive durable Queue on the Appliance.
 * </ul>
 * 
 * Sample Requirements:
 * <ul>
 * <li>SolOS Appliance supporting Queue provisioning and Active Flow Indication.
 * </ul>
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class ActiveFlowIndication extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private Queue queue;
	private FlowHandle flowHandle1 = Solclient.Allocator.newFlowHandle();
	private FlowHandle flowHandle2 = Solclient.Allocator.newFlowHandle();

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
			String[] sessionProps = getSessionProps(config, 0);
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
			 * Ensure the endpoint provisioning is supported
			 *************************************************************************/
			print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_ENDPOINT_MANAGEMENT...");
			if (!sessionHandle
					.isCapable(SolEnum.CapabilityName.CAPABILITY_ENDPOINT_MANAGEMENT)) {
				throw new RuntimeException(
						"Required Capability is not present: Endpoint management not supported.");
			}

			/*************************************************************************
			 * Ensure Active Flow Indication is supported
			 *************************************************************************/
			print("Checking for capability SOLCLIENT_SESSION_CAPABILITY_ACTIVE_FLOW_INDICATION...");
			if (!sessionHandle
					.isCapable(SolEnum.CapabilityName.CAPABILITY_ACTIVE_FLOW_INDICATION)) {
				throw new RuntimeException(
						"Required Capability is not present: Active Flow Indication not supported.");
			}

			/*************************************************************************
			 * Provision Queue
			 *************************************************************************/

			// Make a name
			String provQueueName = "sample_ActiveFlowIndication_"
					+ System.currentTimeMillis() % 100000;

			print("Provisioning queue " + provQueueName + "]");

			// Queue Properties

			int queueProps = 0;

			String[] queueProperties = new String[6];

			queueProperties[queueProps++] = Endpoint.PROPERTIES.ACCESSTYPE;
			queueProperties[queueProps++] = SolEnum.EndpointAccessType.EXCLUSIVE;

			queueProperties[queueProps++] = Endpoint.PROPERTIES.PERMISSION;
			queueProperties[queueProps++] = SolEnum.EndpointPermission.MODIFY_TOPIC;

			queueProperties[queueProps++] = Endpoint.PROPERTIES.QUOTA_MB;
			queueProperties[queueProps++] = "100";

			// The Queue with name
			queue = Solclient.Allocator
					.newQueue(provQueueName, queueProperties);

			rc = sessionHandle.provision(queue,
					SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM, 0);
			assertReturnCode("sessionHandle.provision()", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Preparing flowProperties
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[6];

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.BIND_BLOCKING;
			flowProperties[flowProps++] = SolEnum.BooleanValue.DISABLE;

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACTIVE_FLOW_IND;
			flowProperties[flowProps++] = SolEnum.BooleanValue.ENABLE;

			flowProperties[flowProps++] = FlowHandle.PROPERTIES.ACKMODE;
			flowProperties[flowProps++] = SolEnum.AckMode.CLIENT;

			/*************************************************************************
			 * Creating flow 1
			 *************************************************************************/
			print("Creating flow 1...");

			FlowEventCallbackSample flowEventCallback1 = new FlowEventCallbackSample(
					"Flow 1>");

			// Get a Handle flow 1
			rc = sessionHandle.createFlowForHandle(flowHandle1, flowProperties,
					queue, null, messageCallback, flowEventCallback1);

			assertReturnCode(
					"Creating flow1 - sessionHandle.createFlowForHandle()", rc,
					SolEnum.ReturnCode.IN_PROGRESS);

			try {
				Thread.sleep(2000);
			} catch (InterruptedException ie) {
			}

			FlowEvent lastFlowEvent = flowEventCallback1.getLastFlowEvent();
			if (lastFlowEvent != null) {
				if (lastFlowEvent.getFlowEventEnum() != SolEnum.FlowEventCode.ACTIVE)
					throw new IllegalStateException(
							"Flow 1 did not received the expected 'ACTIVE' flow event, got this instead "
									+ lastFlowEvent);
			} else {
				throw new IllegalStateException("No Flow event received?");
			}

			print("Creating flow 2...");

			FlowEventCallbackSample flowEventCallback2 = new FlowEventCallbackSample(
					"Flow 2>");

			// Get a Handle flow 2
			rc = sessionHandle.createFlowForHandle(flowHandle2, flowProperties,
					queue, null, messageCallback, flowEventCallback2);

			assertReturnCode(
					"Creating flow2 - sessionHandle.createFlowForHandle()", rc,
					SolEnum.ReturnCode.IN_PROGRESS);

			try {
				Thread.sleep(2000);
			} catch (InterruptedException ie) {
			}

			lastFlowEvent = flowEventCallback2.getLastFlowEvent();
			if (lastFlowEvent != null) {
				if (lastFlowEvent.getFlowEventEnum() != SolEnum.FlowEventCode.UP_NOTICE)
					throw new IllegalStateException(
							"Flow 2 did not received the expected 'UP_NOTICE' flow event, got this instead "
									+ lastFlowEvent);
			} else {
				throw new IllegalStateException("No Flow event received?");
			}

			try {
				if (flowHandle1 != null && flowHandle1.isBound()) {
					flowHandle1.destroy();
					print("flowHandle1.destroy");
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ie) {
					}
				}
			} catch (Throwable t) {
				error("Unable to destroy a flow", t);
			}

			lastFlowEvent = flowEventCallback2.getLastFlowEvent();
			if (lastFlowEvent != null) {
				if (lastFlowEvent.getFlowEventEnum() != SolEnum.FlowEventCode.ACTIVE)
					throw new IllegalStateException(
							"Flow 2 did not received the expected 'ACTIVE' flow event, got this instead "
									+ lastFlowEvent);
			} else {
				throw new IllegalStateException("No Flow event received?");
			}

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

		finish_DestroyHandle(flowHandle1, "flowHandle1");

		finish_DestroyHandle(flowHandle2, "flowHandle2");

		finish_Deprovision(queue, sessionHandle);

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
		ActiveFlowIndication sample = new ActiveFlowIndication();
		sample.run(args);
	}

}
