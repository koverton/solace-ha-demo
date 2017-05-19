/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.nio.ByteBuffer;
import java.util.Map;
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
 * PerfADPubSub.java
 * 
 * This is a GC-free performance sample for Assured (Guaranteed) Delivery, it demonstrates:
 * <ul>
 * <li>Binding to a Queue (temporary or durable)
 * <li>Publishing to a Queue
 * <li>Acknowledgments of messages on callback.
 * </ul>
 * 
 * For the case of a durable queue, this sample requires that a durable Queue
 * called 'my_sample_queue' be provisioned on the Appliance with at least
 * 'Consume' permissions.
 * 
 * 
 */
public class PerfADPubSub extends AbstractSample {

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();
	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();
	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();
	private FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();

	private int numOfMessages = 1000000;
	private int msgSize = 100;
	private ByteBuffer content;

	private static boolean quit = false;

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-d true|false]\t durable or non durable, default: false\n";
		System.out.println(usage);
		System.out.println("\t -n messages: number messages to send [default "
				+ numOfMessages + "] \n");
		System.out
				.println("\t -s messagesize: message size to publish [default "
						+ msgSize + "] \n");

		finish(1);
	}

	private static int[] returnCodes_OK = { SolEnum.ReturnCode.OK };

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Shutoff any verbose output which may consume memory from the sample
		// functions
		beSilent();

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

			Map<String, String> cmdLineArgs = config.getArgBag();
			if (cmdLineArgs.containsKey("-n")) {
				numOfMessages = Integer.parseInt(cmdLineArgs.get("-n"));
			}
			if (cmdLineArgs.containsKey("-s")) {
				msgSize = Integer.parseInt(cmdLineArgs.get("-s"));
				if (msgSize < 0) {
					System.out.println("messageSize should positive");
					printUsage(config instanceof SecureSessionConfiguration);
				}
			}

			content = ByteBuffer.allocateDirect(msgSize);

			// Init
			print(" Initializing the Java RTO Messaging API...");
			int rc = Solclient.init(new String[0]);
			assertReturnCode("Solclient.init()", rc, returnCodes_OK);

			// We don't care for any output unless it is an error
			Solclient.setLogLevel(Level.SEVERE);
			
			// Context
			print(" Creating a context ...");
			rc = Solclient.createContextForHandle(contextHandle, new String[0]);
			assertReturnCode("Solclient.createContext()", rc, returnCodes_OK);

			// Session
			print(" Creating a session ...");
			String[] sessionProps = getSessionProps(config, 0);
			CustomEventsAdapter sessionCustomEventsAdapter = new CustomEventsAdapter();

			rc = contextHandle.createSessionForHandle(sessionHandle,
					sessionProps, sessionCustomEventsAdapter,
					sessionCustomEventsAdapter);
			assertReturnCode("contextHandle.createSession()", rc,
					returnCodes_OK);

			// Connect
			print(" Connecting session ...");
			rc = sessionHandle.connect();
			assertReturnCode("sessionHandle.connect()", rc, returnCodes_OK);

			/*************************************************************************
			 * Create a Flow
			 *************************************************************************/

			// Flow Properties
			int flowProps = 0;
			String[] flowProperties = new String[4];

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

			CustomFlowEventCallback flowEventCallback = new CustomFlowEventCallback();
			FlowMessageAckCallback flowMessageAckCallback = new FlowMessageAckCallback(
					numOfMessages);

			Queue queue = null;
			if (isDurable) {
				queue = Solclient.Allocator.newQueue(SampleUtils.SAMPLE_CONFIGURED_QUEUE, null);
			} else {
				String tempQueueName = "sample_ADPubSub_"
						+ System.currentTimeMillis() % 100000;
				queue = sessionHandle.createTemporaryQueue(tempQueueName);
			}

			print("Creating flow Queue Name [" + queue.getName()
					+ "] Durable? [" + queue.isDurable() + "]");

			rc = sessionHandle.createFlowForHandle(flowHandle, flowProperties,
					queue, null, flowMessageAckCallback, flowEventCallback);

			assertReturnCode("sessionHandle.createFlowForHandle", rc,
					returnCodes_OK);

			/*************************************************************************
			 * Publish
			 *************************************************************************/

			print("Publishing [" + numOfMessages + "] messages to Queue ["
					+ queue.getName() + "] with an attached binary size of ["
					+ msgSize + "]");

			Destination destination = queue;

			/*
			 * Retrieve the temporary queue name from the Flow. NOTE:
			 * flowHandle.getDestination() can be used on temporary Queues or
			 * durable Flows. This sample demonstrates both.
			 */
			if (!isDurable) {
				destination = flowHandle.getDestination();
			}
			String tempStringCreate = "Solclient.createMessage()";
			rc = Solclient.createMessageForHandle(txMessageHandle);
			assertReturnCode(tempStringCreate, rc, returnCodes_OK);

			// Common to all publishing, do it once only.
			txMessageHandle.setDestination(destination);
			txMessageHandle
					.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

			if (content.capacity() != msgSize) {
				throw new IllegalStateException(
						"Unable to fill up the byte buffer?! to size ["
								+ msgSize + "] ? capacity was ["
								+ content.capacity() + "]");
			}

			long startTime = System.currentTimeMillis();

			// Send them as fast as possible
			for (int i = 0; i < numOfMessages; i++) {

				// Fill some message content
				if (msgSize > 0) {

					content.clear();

					// Fill the byte buffer, using int ( 4 bytes )
					for (int m = 0; m < msgSize / 4; m++) {
						content.putInt(m + i);
					}

					// Top up with bytes
					int remainder = msgSize % 4;
					for (byte b = 0; b < remainder; b++) {
						content.put(b);
					}

					content.flip();

					txMessageHandle.setBinaryAttachment(content);

				}

				rc = sessionHandle.send(txMessageHandle);
				// Count, and can limit rate...
			}

			long elapsedMs = System.currentTimeMillis() - startTime;
			double txRate = (double) numOfMessages / (double) elapsedMs;

			System.out.printf(
					"%nSent %d messages in %f seconds = %f msg/second%n",
					numOfMessages, elapsedMs / 1000.0, txRate * 1000);

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

			System.out.println();

			if (flowMessageAckCallback.getMessageCount() != numOfMessages) {
				throw new IllegalStateException(numOfMessages
						+ " messages were expected, got ["
						+ flowMessageAckCallback.getMessageCount()
						+ "] instead");
			} else
				print("Test Passed");

		} catch (Throwable t) {
			error("An error has occurred " + t.getMessage(), t);
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

		int expectedMax;

		int messageCount = 0;

		private int rc;

		FlowMessageAckCallback(int max) {
			expectedMax = max;
		}

		@Override
		public void onMessage(Handle handle) {

			messageCount++;

			rc = ((FlowHandle) handle).ack(((MessageSupport) handle)
					.getRxMessage());

			if (rc != SolEnum.ReturnCode.OK)
				return; // Ignore the errors for now...

			if (!quit && (messageCount >= expectedMax)) {
				quit = true;
			}

		}

		public long getMessageCount() {
			return messageCount;
		}

	}

	static class CustomEventsAdapter implements MessageCallback,
			SessionEventCallback {

		@Override
		public void onEvent(SessionHandle sessionHandle) {
		}

		@Override
		public void onMessage(Handle handle) {
		}

	}

	public static class CustomFlowEventCallback implements FlowEventCallback {

		@Override
		public void onEvent(FlowHandle handle) {
		}

	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		PerfADPubSub sample = new PerfADPubSub();
		sample.run(args);
	}

}
