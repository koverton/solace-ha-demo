/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientErrorInfo;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.FlowEvent;
import com.solacesystems.solclientj.core.event.FlowEventCallback;
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.Handle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.MessageSupport;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Destination;
import com.solacesystems.solclientj.core.resource.Endpoint;
import com.solacesystems.solclientj.core.resource.Queue;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration.AuthenticationScheme;

public abstract class AbstractSample {

	protected static final Logger LOGGER = Logger
			.getLogger(AbstractSample.class.getName());
	protected static final Level defaultLogLevel = Level.INFO;

	boolean monitorMemory = false;

	private static boolean printAssertionSuccess = true;

	private static boolean logCallbacks = true;

	Monitor monitor = new Monitor();

	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	public AbstractSample() {
	}

	private MessageCallbackSample _defaultMessageCallbackSample = new MessageCallbackSample(
			"");
	private FlowEventCallbackSample _defaultFlowEventCallbackSample = new FlowEventCallbackSample();
	private SessionEventCallbackSample _defaultSessionEventCallbackSample = new SessionEventCallbackSample(
			"");

	private ByteBuffer messageContentBuffer = ByteBuffer.allocateDirect(512);

	protected static void beSilent() {
		printAssertionSuccess = false;
		logCallbacks = false;
	}

	protected static void print(String message) {
		System.out.println(message);
	}

	protected static void log(String message) {
		LOGGER.log(defaultLogLevel, message);
	}

	protected static void error(String message, Throwable t) {
		LOGGER.log(Level.SEVERE, message, t);
	}

	/**
	 * Uses the given ArgumentsParser and parses the command line arguments as
	 * desired.
	 * 
	 * @param args
	 * @param parser
	 * @return
	 */
	public int parse(String[] args, ArgumentsParser parser) {
		return parser.parse(args);
	}

	public void run(String[] args) {

		ArgumentsParser parser = new ArgumentsParser();
		SessionConfiguration configuration = null;
		int rc = parse(args, parser);
		if (rc != 0) {
			printUsage(parser.isSecure());
		} else {
			configuration = parser.getConfig();
		}
		if (configuration == null) {
			print("Exited.");
			finish(1);
			System.exit(1);
		}

		int finishCode = 0;
		try {
			String logLevelStr = configuration.getArgBag().get("-l");

			Level logLevel = Level.INFO;
			if (logLevelStr != null) {
				try {
					logLevel = Level.parse(logLevelStr);
				} catch (IllegalArgumentException ex) {
					error(String.format(
							"LogLevel defaulted to INFO, as we encountered an exception parsing the logLevel '%s'",
							ex.getMessage()), ex);
				}
			}

			String mmStr = configuration.getArgBag().get("-mm");
			int intervalInSeconds = -1;
			if (mmStr != null) {
				try {
					intervalInSeconds = Integer.parseInt(mmStr);
					monitorMemory = true;
				} catch (NumberFormatException e) {
					error(String.format(
							"Encountered an exception parsing '%s'", mmStr), e);
					throw e;
				}
			}

			if (monitorMemory) {
				scheduler.scheduleAtFixedRate(monitor, 0, intervalInSeconds,
						TimeUnit.SECONDS);
			}

			run(args, configuration, logLevel);
		} catch (Throwable ex) {
			error(String.format(
					"Encountered an exception while running sample '%s'", this
							.getClass().getName()), ex);
			ex.printStackTrace();
			finishCode = 1;
		} finally {
			finish(finishCode);
			if (monitorMemory) {
				scheduler.shutdown();
				// Once last check
				monitor.checkUsedMemory();
				monitor.report();
			}
			print("Exited.");
		}
	}

	protected static void assertReturnCode(String operation, int returnCode,
			int... rc) throws IllegalStateException {
		boolean oneRCMatched = false;
		for (int i = 0; i < rc.length; i++) {
			if (rc[i] == returnCode) {
				oneRCMatched = true;
				break;
			}
		}
		if (!oneRCMatched) {
			throw new IllegalStateException(String.format(
					"'%s' returned unexpected returnCode %d:%s", operation,
					returnCode, SolEnum.ReturnCode.toString(returnCode)));
		} else {
			if (printAssertionSuccess)
				print(String.format("'%s' completed successfully ", operation));
		}
	}

	protected static void assertExpectedCount(String operation, int expected,
			int actual) {
		if (expected != actual) {
			throw new IllegalStateException(String.format(
					"was expecting [%d] but got [%d] for :%s", expected,
					actual, operation));
		} else {
			if (printAssertionSuccess)
				print(String.format("** '%s' completed successfully ",
						operation));
		}

	}

	protected MessageCallbackSample getMessageCallback(boolean keepRxMessages) {
		_defaultMessageCallbackSample.keepRxMessages(keepRxMessages);
		return _defaultMessageCallbackSample;
	}

	protected MessageCallback getDefaultMessageCallback() {
		return _defaultMessageCallbackSample;
	}

	protected FlowEventCallback getDefaultFlowEventCallback() {
		return _defaultFlowEventCallbackSample;
	}

	protected SessionEventCallback getDefaultSessionEventCallback() {
		return _defaultSessionEventCallbackSample;
	}

	protected String[] getSessionProps(SessionConfiguration sessionConfig) {
		return getSessionProps(sessionConfig, 0);
	}

	protected String[] getSessionProps(SessionConfiguration sessionConfig,
			int spare) {

		ArrayList<String> sessionProperties = new ArrayList<String>();

		if (sessionConfig.getHost() != null) {
			sessionProperties.add(SessionHandle.PROPERTIES.HOST);
			sessionProperties.add(sessionConfig.getHost());
		}

		if (sessionConfig.getRouterUserVpn() != null) {
			sessionProperties.add(SessionHandle.PROPERTIES.VPN_NAME);
			sessionProperties.add(sessionConfig.getRouterUserVpn().get_vpn());
			sessionProperties.add(SessionHandle.PROPERTIES.USERNAME);
			sessionProperties.add(sessionConfig.getRouterUserVpn().get_user());
		}

		if (sessionConfig.getRouterPassword() != null) {
			sessionProperties.add(SessionHandle.PROPERTIES.PASSWORD);
			sessionProperties.add(sessionConfig.getRouterPassword());
		}
		
		if (sessionConfig.isCompression()) {
		    sessionProperties.add(SessionHandle.PROPERTIES.COMPRESSION_LEVEL);
		    sessionProperties.add("9");
		}
		
		sessionProperties.add(SessionHandle.PROPERTIES.REAPPLY_SUBSCRIPTIONS);
		sessionProperties.add(SolEnum.BooleanValue.ENABLE);
		
		if (sessionConfig.getAuthenticationScheme() == AuthenticationScheme.CLIENT_CERTIFICATE) {
			sessionProperties.add(SessionHandle.PROPERTIES.AUTHENTICATION_SCHEME);			
		    sessionProperties.add(SolEnum.AuthenticationScheme.CLIENT_CERTIFICATE);
		}
		else if (sessionConfig.getAuthenticationScheme() == AuthenticationScheme.KERBEROS) {
			sessionProperties.add(SessionHandle.PROPERTIES.AUTHENTICATION_SCHEME);			
		    sessionProperties.add(SolEnum.AuthenticationScheme.GSS_KRB);
		}
		
		if (sessionConfig instanceof SecureSessionConfiguration) {
			SecureSessionConfiguration secureSessionConfiguration = (SecureSessionConfiguration) sessionConfig;

			if (sessionConfig.getAuthenticationScheme() == AuthenticationScheme.CLIENT_CERTIFICATE) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_CLIENT_CERTIFICATE_FILE);
				sessionProperties.add(secureSessionConfiguration
						.getCertificateFile());

				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_CLIENT_PRIVATE_KEY_FILE);
				sessionProperties.add(secureSessionConfiguration
						.getPrivateKeyFile());

				if (secureSessionConfiguration.getPrivateKeyPassword() != null) {
					sessionProperties
							.add(SessionHandle.PROPERTIES.SSL_CLIENT_PRIVATE_KEY_FILE_PASSWORD);
					sessionProperties.add(secureSessionConfiguration
							.getPrivateKeyPassword());
				}
			}

			if (!secureSessionConfiguration.getValidateCertificates()) {
				/*
				 * The certificate validation property is ignored on non-SSL
				 * sessions. For simple demo applications, disable it on SSL
				 * sesssions (host string begins with tcps:) so a local trusted
				 * root and certificate store is not required. See the API users
				 * guide for documentation on how to setup a trusted root so the
				 * servers certificate returned on the secure connection can be
				 * verified if this is desired.
				 */
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_VALIDATE_CERTIFICATE);
				sessionProperties.add(SolEnum.BooleanValue.DISABLE);
			}

			if (!secureSessionConfiguration.getValidateCertificateDates()) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_VALIDATE_CERTIFICATE_DATE);
				sessionProperties.add(SolEnum.BooleanValue.DISABLE);
			}

			if (secureSessionConfiguration.getTrustStoreDir() != null) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_TRUST_STORE_DIR);
				sessionProperties.add(secureSessionConfiguration
						.getTrustStoreDir());
			}

			if (secureSessionConfiguration.getCiphers() != null) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_CIPHER_SUITES);
				sessionProperties.add(secureSessionConfiguration.getCiphers());
			}

			if (secureSessionConfiguration.getCommonNames() != null) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_TRUSTED_COMMON_NAME_LIST);
				sessionProperties.add(secureSessionConfiguration
						.getCommonNames());
			}

			if (secureSessionConfiguration.getProtocol() != null) {
				sessionProperties.add(SessionHandle.PROPERTIES.SSL_PROTOCOL);
				sessionProperties.add(secureSessionConfiguration.getProtocol());
			}
			
			if (secureSessionConfiguration.getSslDowngrade() != null) {
				sessionProperties
						.add(SessionHandle.PROPERTIES.SSL_CONNECTION_DOWNGRADE_TO);
				sessionProperties.add(SolEnum.SSLDowngradeTransportProtocol.PLAIN_TEXT);
			}
		}

		// Make an array >= than the current Session Properties List with room
		// to "spare"
		String[] props = new String[sessionProperties.size() + spare];

		return sessionProperties.toArray(props);
	}

	protected void common_deleteQueue(SessionHandle sessionHandle, Queue queue) {

		if (sessionHandle == null)
			throw new IllegalArgumentException("SessionHandle may not be null");

		if (queue == null)
			throw new IllegalArgumentException("Queue may not be null");

		print("Deprovisioning queue [" + queue.getName() + "]");

		int rc = sessionHandle.deprovision(queue,
				SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM
						| SolEnum.ProvisionFlags.IGNORE_EXIST_ERRORS, 0);
		if (rc == SolEnum.ReturnCode.FAIL) {
			throw new IllegalStateException("sessionHandle.deprovision");
		}

	}

	protected Queue common_createQueue(SessionHandle sessionHandle,
			String queueName) {

		print("Provisioning queue [" + queueName + "]");

		// Queue Properties

		int queueProps = 0;

		// Enough to fit the needed properties.
		String[] queueProperties = new String[8];

		queueProperties[queueProps++] = Endpoint.PROPERTIES.ACCESSTYPE;
		queueProperties[queueProps++] = SolEnum.EndpointAccessType.EXCLUSIVE;

		queueProperties[queueProps++] = Endpoint.PROPERTIES.PERMISSION;
		queueProperties[queueProps++] = SolEnum.EndpointPermission.DELETE;

		queueProperties[queueProps++] = Endpoint.PROPERTIES.QUOTA_MB;
		queueProperties[queueProps++] = "100";

		/*************************************************************************
		 * If this is not the Dead Message Queue, set the Respects TTL property
		 * to TRUE.
		 *************************************************************************/
		if (!queueName.equals(SampleUtils.COMMON_DMQ_NAME)) {
			queueProperties[queueProps++] = Endpoint.PROPERTIES.RESPECTS_MSG_TTL;
			queueProperties[queueProps++] = SolEnum.BooleanValue.ENABLE;
		}

		// The Queue with name
		Queue queue = Solclient.Allocator.newQueue(queueName, queueProperties);

		int rc = sessionHandle.provision(queue,
				SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM
						| SolEnum.ProvisionFlags.IGNORE_EXIST_ERRORS, 0);
		assertReturnCode("sessionHandle.provision()", rc, SolEnum.ReturnCode.OK);
		print("OK");

		return queue;
	}

	protected void common_publishMessage(SessionHandle aSessionHandle,
			MessageHandle aMessageHandle, String topicStr,
			int messageDeliveryModeFlags) {

		Topic topic = Solclient.Allocator.newTopic(topicStr);
		common_publishMessage(aSessionHandle, aMessageHandle, topic,
				messageDeliveryModeFlags);

	}

	protected void common_publishMessage(SessionHandle aSessionHandle,
			MessageHandle aMessageHandle, Destination destination,
			int messageDeliveryModeFlags) {

		if (aSessionHandle == null)
			throw new IllegalArgumentException("SessionHandle may not be null");

		if (aMessageHandle == null)
			throw new IllegalArgumentException("MessageHandle may not be null");

		int rc = 0;

		if (!aMessageHandle.isBound()) {
			// Allocate the message
			rc = Solclient.createMessageForHandle(aMessageHandle);
			assertReturnCode("Solclient.createMessageForHandle()", rc,
					SolEnum.ReturnCode.OK);
		}

		/* Set the message delivery mode. */
		aMessageHandle.setMessageDeliveryMode(messageDeliveryModeFlags);

		// Set the destination/topic
		aMessageHandle.setDestination(destination);

		String message = "Some message about topic " + destination.getName();
		messageContentBuffer.clear();
		messageContentBuffer.put(message.getBytes(Charset.defaultCharset()));
		messageContentBuffer.flip();

		/* Add some content to the message. */
		aMessageHandle.setBinaryAttachment(messageContentBuffer);

		/* Send the message. */
		rc = aSessionHandle.send(aMessageHandle);
		assertReturnCode("SessionHandle.send()", rc, SolEnum.ReturnCode.OK,
				SolEnum.ReturnCode.OK);
	}

	protected abstract void run(String[] args, SessionConfiguration config,
			Level logLevel) throws SolclientException;

	protected abstract void printUsage(boolean secureSession);

	protected abstract void finish(int status);

	public void finish_DestroyHandle(Handle handle, String description) {
		try {
			if (handle != null && handle.isBound()) {
				handle.destroy();
				print("Destroyed [" + description + "]");
			}
		} catch (Throwable t) {
			error("Unable to destroy [" + description + "]", t);
		}
	}

	public void finish_Disconnect(SessionHandle sessionHandle) {
		try {
			if (sessionHandle != null && sessionHandle.isBound()) {
				sessionHandle.disconnect();
				print("sessionHandle.disconnected");
			}
		} catch (Throwable t) {
			error("Unable to disconnect a sessionHandle ", t);
		}
	}

	public void finish_Solclient() {
		print("All done");
	}

	public void finish_Deprovision(Endpoint endpoint,
			SessionHandle sessionHandle) {
		try {

			if (endpoint != null) {

				int rc = sessionHandle
						.deprovision(
								endpoint,
								SolEnum.ProvisionFlags.WAIT_FOR_CONFIRM
										| SolEnum.ProvisionFlags.IGNORE_EXIST_ERRORS,
								0);

				assertReturnCode("SessionHandle.deprovision()", rc,
						SolEnum.ReturnCode.OK);

				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}

		} catch (Throwable t) {
			error("Unable to deprovision endpoint ", t);
		}
	}

	public static class MessageCallbackSample implements MessageCallback {

		private boolean _keepRxMessage = false;
		private java.util.List<MessageHandle> _rxMessages = new java.util.LinkedList<MessageHandle>();
		private int messageCount = 0;

		String m_id;

		public MessageCallbackSample(String id) {
			m_id = id;
		}

		@Override
		public void onMessage(Handle handle) {
			MessageSupport messageSupport = (MessageSupport) handle;
			setMessageCount(getMessageCount() + 1);

			if (this._keepRxMessage) {
				if (logCallbacks)
					print(m_id + " -> Received message [" + messageCount
							+ "], adding it to received messages list");
				MessageHandle takenMessage = Solclient.Allocator
						.newMessageHandle();
				messageSupport.takeRxMessage(takenMessage);
				this._rxMessages.add(takenMessage);
			} else {
				if (logCallbacks)
					print(m_id + " -> Received message [" + messageCount
							+ "], dumping content");
				MessageHandle rxMessage = messageSupport.getRxMessage();
				if (logCallbacks)
					print(rxMessage.dump(SolEnum.MessageDumpMode.BRIEF));
			}
		}

		public void keepRxMessages(boolean keep) {
			this._keepRxMessage = keep;
		}

		public void setId(String id) {
			this.m_id = id;
		}

		public java.util.List<MessageHandle> getRxMessages() {
			return this._rxMessages;
		}

		public int getMessageCount() {
			return messageCount;
		}

		public void setMessageCount(int messageCount) {
			this.messageCount = messageCount;
		}

		public void destroy() {
			if (_rxMessages.size() > 0) {
				print(String.format("Destroying %d kept messages",
						_rxMessages.size()));
				for (Iterator<MessageHandle> iterator = _rxMessages.iterator(); iterator
						.hasNext();) {

					MessageHandle messageHandle = (MessageHandle) iterator
							.next();
					try {
						messageHandle.destroy();
					} catch (Throwable t) {
						error("Unable to destroy a messageHandle ", t);
					}
					print(".");
				}
			}
		}

	}

	public static class SessionEventCallbackSample implements
			SessionEventCallback {

		private int eventCount = 0;
		String m_id;

		public SessionEventCallbackSample(String id) {
			m_id = id;
		}

		@Override
		public void onEvent(SessionHandle sessionHandle) {
			eventCount++;
			if (logCallbacks)
				print(m_id + "- Received SessionEvent[" + eventCount + "]:"
						+ sessionHandle.getSessionEvent());
		}

		public int getEventCount() {
			return eventCount;
		}

		public void setEventCount(int eventCount) {
			this.eventCount = eventCount;
		}
	}

	public static class FlowEventCallbackSample implements FlowEventCallback {

		String m_id;
		private FlowEvent m_lastflowEvent;

		public FlowEventCallbackSample() {
			this("");
		}

		public FlowEventCallbackSample(String id) {
			m_id = id;
		}

		public FlowEvent getLastFlowEvent() {
			return m_lastflowEvent;
		}

		@Override
		public void onEvent(FlowHandle handle) {
			m_lastflowEvent = handle.getFlowEvent();
			int eventEnum = m_lastflowEvent.getFlowEventEnum();
			switch (eventEnum) {

			case SolEnum.FlowEventCode.UP_NOTICE:
			case SolEnum.FlowEventCode.SESSION_DOWN:
			case SolEnum.FlowEventCode.ACTIVE:
			case SolEnum.FlowEventCode.INACTIVE:
				if (logCallbacks)
					print(m_id + " Received Flow Event "
							+ m_lastflowEvent.toString());
				break;

			case SolEnum.FlowEventCode.DOWN_ERROR:
			case SolEnum.FlowEventCode.BIND_FAILED_ERROR:
			case SolEnum.FlowEventCode.REJECTED_MSG_ERROR:
				SolclientErrorInfo solclientErrorInfo = Solclient
						.getLastErrorInfo();
				print(m_id + " Got some error for ["
						+ m_lastflowEvent.toString() + "]");
				if (solclientErrorInfo != null)
					print(m_id + " Error [" + solclientErrorInfo.getErrorStr()
							+ "] SubCode [" + solclientErrorInfo.getSubCode()
							+ "]");
				break;

			default:
				error(m_id
						+ " FlowEventCallback.onEvent called. Unrecognized or deprecated event. "
						+ m_lastflowEvent.toString(), null);
				break;
			}

		}

	}

	public static class Monitor implements Runnable {

		Runtime runtime = Runtime.getRuntime();
		List<Long> usedMemoryHistory = new ArrayList<Long>();

		@Override
		public void run() {
			checkUsedMemory();
		}

		long lastTotalMemory;
		long lastFreeMemory;
		long lastUsedMemory;

		public void checkUsedMemory() {
			// Checked used memory
			lastTotalMemory = runtime.totalMemory();
			lastFreeMemory = runtime.freeMemory();
			lastUsedMemory = lastTotalMemory - lastFreeMemory;
			usedMemoryHistory.add(lastUsedMemory);
		}

		public void report() {
			for (int i = 0; i < usedMemoryHistory.size(); i++) {
				System.out.println("UsedMemory:" + usedMemoryHistory.get(i));
			}

			printGCStats();
		}

	}

	public static void printGCStats() {
		long totalGarbageCollections = 0;
		long garbageCollectionTime = 0;

		for (GarbageCollectorMXBean gc : ManagementFactory
				.getGarbageCollectorMXBeans()) {

			long count = gc.getCollectionCount();

			if (count >= 0) {
				totalGarbageCollections += count;
			}

			long time = gc.getCollectionTime();

			if (time >= 0) {
				garbageCollectionTime += time;
			}
		}

		System.out.println("Total Garbage Collections: "
				+ totalGarbageCollections);
		System.out.println("Total Garbage Collection Time (ms): "
				+ garbageCollectionTime);
	}

}
