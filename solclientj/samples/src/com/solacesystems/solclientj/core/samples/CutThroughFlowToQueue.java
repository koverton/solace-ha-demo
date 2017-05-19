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
import com.solacesystems.solclientj.core.handle.MessageSupport;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * CutThroughFlowToQueue.java
 * 
 * This sample shows how to create a cut-through message Flow to a queue. It
 * demonstrates:
 * <ul>
 * <li>Binding to a Queue (temporary or durable)
 * <li>Client acknowledgment.
 * </ul>
 * 
 * For the case of a durable queue, this sample requires that a durable Queue
 * called 'my_sample_queue' be provisioned on the Appliance with at least
 * 'Consume' permissions.
 * 
 * Cut-through persistence is not a low latency replacement for traditional
 * Solace guaranteed messaging. It is intended only for guaranteed messaging
 * applications that require the absolute lowest possible latency and can trade
 * off feature support and interoperability to achieve it. It is further
 * intended only for applications with relatively low message rates (less than
 * 100k msgs/s) and a small number of clients (hundreds).
 * 
 * See the Cut-Through Persistence Technical Note for details on implementing
 * and managing applications that use cut-through persistence.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class CutThroughFlowToQueue extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private static boolean quit = false;

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
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
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
			 * Create a Flow
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[6];

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

			/*
			 * Cut-through persistence is not a low latency replacement for
			 * traditional Solace guaranteed messaging. It is intended only for
			 * guaranteed messaging applications that require the absolute
			 * lowest possible latency and can trade off feature support and
			 * interoperability to achieve it. It is further intended only for
			 * applications with relatively low message rates (less than 100k
			 * msgs/s) and a small number of clients (hundreds).
			 * 
			 * See the Cut-Through Persistence Technical Note for details on
			 * implementing and managing applications that use cut-through
			 * persistence.
			 */
			flowProperties[flowProps++] = FlowHandle.PROPERTIES.FORWARDING_MODE;
			flowProperties[flowProps++] = SolEnum.ForwardingMode.CUT_THROUGH;

			// Number of messages we plan to send..
			int maxMessages = 10;

			FlowEventCallback flowEventCallback = getDefaultFlowEventCallback();
			FlowMessageAckCallback flowMessageAckCallback = new FlowMessageAckCallback(
					maxMessages);

			Queue queue = null;
			if (isDurable) {
				queue = Solclient.Allocator.newQueue(SampleUtils.SAMPLE_CONFIGURED_QUEUE, null);
			} else {
				String tempQueueName = "sample_CutThroughFlowToQueue_"
						+ System.currentTimeMillis() % 100000;
				queue = sessionHandle.createTemporaryQueue(tempQueueName);
			}

			print("Creating flow Queue Name [" + queue.getName()
					+ "] Durable? [" + queue.isDurable() + "]");

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					queue, null, flowMessageAckCallback, flowEventCallback);

			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					SolEnum.ReturnCode.OK);

			/*************************************************************************
			 * Publish
			 *************************************************************************/

			print("Publishing [" + maxMessages + "] messages to Queue ["
					+ queue.getName() + "]");

			Destination destination = queue;

			/*
			 * Retrieve the temporary queue name from the Flow. NOTE:
			 * flowHandle.getDestination() can be used on temporary Queues or
			 * durable Flows. This sample demonstrates both.
			 */
			if (!isDurable) {
				destination = flowHandle.getDestination();
			}
			for (int publishCount = 0; publishCount < maxMessages; publishCount++) {

				print("Sending message " + publishCount);
				common_publishMessage(sessionHandle, txMessageHandle,
						destination, SolEnum.MessageDeliveryMode.PERSISTENT);

			}

			// Register a shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					quit = true;
				}
			});

			// Do some waiting, the registered callback will handle replies, it
			// will quit when done
			while (!quit) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			print("Quitting time");

			if (flowMessageAckCallback.getMessageCount() != maxMessages) {
				throw new IllegalStateException(maxMessages
						+ " messages were expected, got ["
						+ flowMessageAckCallback.getMessageCount()
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

		finish_DestroyHandle(txMessageHandle, "messageHandle");

		finish_Disconnect(sessionHandle);

		finish_DestroyHandle(sessionHandle, "sessionHandle");

		finish_DestroyHandle(contextHandle, "contextHandle");

		finish_Solclient();

	}

	public static class FlowMessageAckCallback implements MessageCallback {

		private int messageCount = 0;
		int expectedMax;

		FlowMessageAckCallback(int max) {
			expectedMax = max;
		}

		@Override
		public void onMessage(Handle handle) {

			try {

				MessageSupport messageSupport = (MessageSupport) handle;
				MessageHandle rxMessage = messageSupport.getRxMessage();
				FlowHandle flowHandle = (FlowHandle) handle;

				long msgId = rxMessage.getGuaranteedMessageId();
				print(String.format(
						"Received message on flow. (Message ID: %d).", msgId));
				print(rxMessage.dump(SolEnum.MessageDumpMode.BRIEF));

				print(String.format("Acknowledging message: %d.", msgId));

				// Alternate between both approaches of ack calls, just to demo.
				if (messageCount % 2 == 0) {
					int rc = flowHandle.ack(msgId);
					assertReturnCode("flowHandle.ack", rc,
							SolEnum.ReturnCode.OK);
				} else {
					int rc = flowHandle.ack(rxMessage);
					assertReturnCode("flowHandle.ack", rc,
							SolEnum.ReturnCode.OK);

				}

				//
				messageCount++;

			} catch (IllegalStateException ise) {
				// quit = true;
				// throw ise;

			} finally {
				if (!quit && (messageCount >= expectedMax))
					quit = true;
			}
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
		CutThroughFlowToQueue sample = new CutThroughFlowToQueue();
		sample.run(args);
	}

}
