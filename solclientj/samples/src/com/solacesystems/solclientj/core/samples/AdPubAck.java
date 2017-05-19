/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientErrorInfo;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEvent;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.Handle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.CorrelationArrayUtil;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * AdPubAck.java
 * 
 * This sample shows the publishing of Guaranteed messages and how message
 * Acknowledgments can be handled on callback.
 * 
 * To accomplish this, the publisher makes use of a correlation key for each
 * sent message. Then in the event callback the correlation key is presented
 * along with SessionEventCode which indicates the success or failure of sending
 * the message.
 * 
 * In this specific sample, the publisher is using a sparse array utility, the
 * array index is the correlation key, the array bucket holds whatever structure
 * is desired for tracking the message and its state. In the callback,
 * correlated objects are cleared from the array and further processing can
 * occur with them as desired.
 * 
 * For simplicity, this sample treats both message acceptance and rejection the
 * same way: the message is freed. In real world applications, the client should
 * decide what to do in the failure scenario.
 * 
 * The reason the message is not processed in the event callback in this sample
 * is because it is not possible to make blocking calls from within the event
 * callback. In general, it is often simpler to send messages as blocking, as is
 * done in the publish thread of this sample. So, consequently, if an
 * application wanted to re-send rejected messages, it would have to avoid doing
 * this in the callback or update the code to use non-blocking sends. This
 * sample chooses to avoid processing the message within the callback.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class AdPubAck extends AbstractSample {

	private SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	private MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	private Topic topic = Solclient.Allocator
			.newTopic(SampleUtils.SAMPLE_TOPIC);

	private AdPubAckEventAdapter adPubAckEventAdapter = new AdPubAckEventAdapter();

	private ByteBuffer content = ByteBuffer.allocateDirect(400);

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCommonUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-n number]\t Number of messages to publish, default: 1\n";
		System.out.println(usage);
		finish(1);
	}

	/*
	 * Used to track the correlation state for a given message publisher event
	 * callback when the message is acknowledged or rejected.
	 */
	class MsgInfo {
		public volatile boolean acked = false;
		public volatile boolean accepted = false;

		public final long id;

		public MsgInfo(long id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return id + ": acked[" + acked + "] accepted [" + accepted + "]";
		}
	}

	public static class AdPubAckEventAdapter implements SessionEventCallback,
			MessageCallback {

		// An sample Correlation Array Utility for making correlationKey and
		// tracking associated objects
		CorrelationArrayUtil<MsgInfo> correlationArrayMsgInfo = new CorrelationArrayUtil<MsgInfo>(
				MsgInfo.class);

		public CorrelationArrayUtil<MsgInfo> getCorrelationArrayMsgInfo() {
			return correlationArrayMsgInfo;
		}

		public void setCorrelationArrayMsgInfo(
				CorrelationArrayUtil<MsgInfo> correlationArrayMsgInfo) {
			this.correlationArrayMsgInfo = correlationArrayMsgInfo;
		}

		private int acknowledgedCount = 0;

		@Override
		public void onEvent(SessionHandle sessionHandle) {

			SessionEvent se = sessionHandle.getSessionEvent();

			int sessionEventCode = se.getSessionEventCode();

			long correlationKey = se.getCorrelationKey();

			MsgInfo msgInfo = correlationArrayMsgInfo
					.uncorrelate(correlationKey);
			if (msgInfo != null)
				setAcknowledgedCount(getAcknowledgedCount() + 1);

			SolclientErrorInfo solclientErrorInfo = Solclient
					.getLastErrorInfo();

			switch (sessionEventCode) {
			case SolEnum.SessionEventCode.ACKNOWLEDGEMENT:

				if (msgInfo != null) {
					msgInfo.acked = true;
					msgInfo.accepted = true;
				}
				print("AdPubAckEventAdapter - Received ACKNOWLEDGEMENT for correlationKey ["
						+ correlationKey + "]  MsgInfo [" + msgInfo + "]");

				break;
			case SolEnum.SessionEventCode.REJECTED_MSG_ERROR:
				if (msgInfo != null) {
					msgInfo.acked = true;
					msgInfo.accepted = false;
				}
				print("AdPubAckEventAdapter - Received REJECTED_MSG_ERROR for correlationKey ["
						+ correlationKey
						+ "]  MsgInfo ["
						+ msgInfo
						+ "] "
						+ solclientErrorInfo);
				break;

			case SolEnum.SessionEventCode.UP_NOTICE:
			case SolEnum.SessionEventCode.TE_UNSUBSCRIBE_OK:
			case SolEnum.SessionEventCode.CAN_SEND:
			case SolEnum.SessionEventCode.RECONNECTING_NOTICE:
			case SolEnum.SessionEventCode.RECONNECTED_NOTICE:
			case SolEnum.SessionEventCode.PROVISION_OK:
			case SolEnum.SessionEventCode.SUBSCRIPTION_OK:
				print("AdPubAckEventAdapter - Received SessionEvent [" + se
						+ "]");
				break;

			case SolEnum.SessionEventCode.DOWN_ERROR:
			case SolEnum.SessionEventCode.CONNECT_FAILED_ERROR:
			case SolEnum.SessionEventCode.SUBSCRIPTION_ERROR:
			case SolEnum.SessionEventCode.TE_UNSUBSCRIBE_ERROR:
			case SolEnum.SessionEventCode.PROVISION_ERROR:
				print("AdPubAckEventAdapter - Error Received SessionEvent ["
						+ se + "] " + solclientErrorInfo);
				break;

			default:
				print("AdPubAckEventAdapter - Received Unrecognized or deprecated event, SessionEvent ["
						+ se + "]");

				break;
			}
		}

		@Override
		public void onMessage(Handle handle) {
			print("AdPubAckEventAdapter - Received onMessage");
		}

		public int getAcknowledgedCount() {
			return acknowledgedCount;
		}

		public void setAcknowledgedCount(int acknowledgedCount) {
			this.acknowledgedCount = acknowledgedCount;
		}

	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine the numberOfMessageToPublish
		int numberOfMessageToPublish = 1;

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
		assertReturnCode("Solclient.createContext()", rc, SolEnum.ReturnCode.OK);

		// Session - Notice the registered adPubAckEventAdapter with the session
		print(" Creating a session ...");
		String[] sessionProps = getSessionProps(config, 0);
		rc = contextHandle.createSessionForHandle(sessionHandle, sessionProps,
				adPubAckEventAdapter, adPubAckEventAdapter);
		assertReturnCode("contextHandle.createSession()", rc,
				SolEnum.ReturnCode.OK);

		// Connect
		print(" Connecting session ...");
		rc = sessionHandle.connect();
		assertReturnCode("sessionHandle.connect()", rc, SolEnum.ReturnCode.OK);

		// Allocate a message.
		print(" Created a Message Handle ...");
		rc = Solclient.createMessageForHandle(txMessageHandle);
		assertReturnCode("Solclient.createMessageForHandle()", rc,
				SolEnum.ReturnCode.OK);

		// Set the delivery mode for the message.
		txMessageHandle
				.setMessageDeliveryMode(SolEnum.MessageDeliveryMode.PERSISTENT);

		// Set the destination/topic
		txMessageHandle.setDestination(topic);

		print(" Will send [" + numberOfMessageToPublish + "] messages");

		for (int i = 0; i < numberOfMessageToPublish; i++) {

			// Track it
			MsgInfo msgInfo = new MsgInfo(i);
			long correlationKey = adPubAckEventAdapter
					.getCorrelationArrayMsgInfo().correlate(msgInfo);

			// Using CorrelationArrayUtil generated correlationKey
			txMessageHandle.setCorrelationKey(correlationKey);

			String msg = "AdPubAck MsgInfo [" + i + "] correlationKey ["
					+ correlationKey + "]";

			// Reuse the content buffer with new content
			content.clear();
			content.put(msg.getBytes(Charset.defaultCharset()));
			content.flip();

			// Copies the content to the message
			txMessageHandle.setBinaryAttachment(content);

			// Send it
			rc = sessionHandle.send(txMessageHandle);
			assertReturnCode("sessionHandle.send()", rc, SolEnum.ReturnCode.OK,
					SolEnum.ReturnCode.IN_PROGRESS);

			print("Sending [" + msg + "]");

			// Wait
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

		// Wait some more..
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertExpectedCount("Acknowledged count ", numberOfMessageToPublish,
				adPubAckEventAdapter.getAcknowledgedCount());

		print("Test Passed");

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
		AdPubAck sample = new AdPubAck();
		sample.run(args);
	}

}
