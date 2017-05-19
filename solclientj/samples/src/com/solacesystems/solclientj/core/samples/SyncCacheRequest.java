/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.CacheSessionEventCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.CacheSessionHandle;
import com.solacesystems.solclientj.core.handle.ContextHandle;
import com.solacesystems.solclientj.core.handle.MessageHandle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import com.solacesystems.solclientj.core.resource.Topic;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.CacheSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SampleUtils;
import com.solacesystems.solclientj.core.samples.common.SecureSessionConfiguration;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * SyncCacheRequest.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Creating a cache session.
 * <li>Sending an synchronous cache request.
 * </ul>
 * <p>
 * Sample Requirements:
 * <ul>
 * <li>A Appliance running SolOS-TR with an active cache.
 * <li>A cache running and caching on a pattern that matches "my/sample/topic".
 * <li>The cache name must be known and passed to this program as a command line
 * argument.
 * </ul>
 * 
 * In this sample, a synchronous cache request is sent. The cache request is
 * blocking, so it is possible to see that the cache response is processed
 * before the application continues with its execution.
 * 
 * Cached messages returned as a result of the cache request are handled by the
 * Session's message receive callback in the normal manner. This sample uses a
 * callback that simply dumps the message to STDOUT.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 * 
 */
public class SyncCacheRequest extends AbstractSample {

	private static SessionHandle sessionHandle = Solclient.Allocator
			.newSessionHandle();

	private ContextHandle contextHandle = Solclient.Allocator
			.newContextHandle();

	CacheSessionHandle cacheSessionHandle = Solclient.Allocator
			.newCacheSessionHandle();

	private static MessageHandle txMessageHandle = Solclient.Allocator
			.newMessageHandle();

	@Override
	protected void printUsage(boolean secureSession) {
		String usage = ArgumentsParser.getCacheArgUsage(secureSession);
		usage += "This sample:\n";
		usage += "\t[-t topic]\t Topic, default:" + SampleUtils.SAMPLE_TOPIC
				+ "\n";
		System.out.println(usage);
		finish(1);
	}

	@Override
	public int parse(String[] args, ArgumentsParser parser) {
		return parser.parseCacheSampleArgs(args);
	}

	/**
	 * This is the main method of the sample
	 */
	@Override
	protected void run(String[] args, SessionConfiguration config, Level logLevel)
			throws SolclientException {

		// Determine a topicName, or default to SampleUtils.SAMPLE_TOPIC
		String topicName = config.getArgBag().get("-t");
		if (topicName == null)
			topicName = SampleUtils.SAMPLE_TOPIC;

		CacheSessionConfiguration cacheSessionConfiguration = (CacheSessionConfiguration) config;

		// Determine a cacheName
		String cacheName = cacheSessionConfiguration.getCacheName();
		if (cacheName == null)
			printUsage(config instanceof SecureSessionConfiguration);

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

		/* Create a Session */
		print(" Create a Session.");
		String[] sessionProps = getSessionProps(config, 0);

		SessionEventCallback sessionEventCallback = getDefaultSessionEventCallback();
		MessageCallbackSample messageCallback = getMessageCallback(false);

		/* Create the Session. */
		rc = contextHandle.createSessionForHandle(sessionHandle, sessionProps,
				messageCallback, sessionEventCallback);
		assertReturnCode("contextHandle.createSession() - session", rc,
				SolEnum.ReturnCode.OK);

		/* Connect the Session. */
		print(" Connecting session ...");
		rc = sessionHandle.connect();
		assertReturnCode("sessionHandle.connect()", rc, SolEnum.ReturnCode.OK);

		print("Will be using topic [" + topicName + "]");
		Topic topic = Solclient.Allocator.newTopic(topicName);

		/*************************************************************************
		 * Publish a message (just to make sure there is one there cached)
		 *************************************************************************/

		common_publishMessage(sessionHandle, txMessageHandle, topic,
				SolEnum.MessageDeliveryMode.DIRECT);

		/*************************************************************************
		 * CREATE A CACHE SESSION (within the connected Session)
		 *************************************************************************/

		int cachePropsIndex = 0;
		String[] cacheProps = new String[2];

		cacheProps[cachePropsIndex++] = CacheSessionHandle.PROPERTIES.CACHE_NAME;
		cacheProps[cachePropsIndex++] = cacheName;

		CacheSessionEventCallback cacheSessionEventCallback = new CacheSessionEventCallback() {
			
			@Override
			public void onCacheSessionEvent(CacheSessionHandle cacheSessionHandle) {
			}
		};
		
		rc = sessionHandle.createCacheSessionForHandle(cacheSessionHandle,
				cacheProps, cacheSessionEventCallback);
		assertReturnCode("sessionHandle.createCacheSessionForHandle", rc,
				SolEnum.ReturnCode.OK);

		/*************************************************************************
		 * Cache the request (synchronous)
		 *************************************************************************/

		print("Sending cache request.");

		/*
		 * SolEnum.CacheLiveDataAction.QUEUE: The cache request completes when
		 * the cache response is returned. Live data matching the cache request
		 * topic is queued until the cache request completes. The queued live
		 * data is delivered to the application before the cache response
		 * message.
		 */
		long requestId = System.currentTimeMillis();
		rc = cacheSessionHandle.sendCacheRequest(requestId, topic, 0,
				SolEnum.CacheLiveDataAction.QUEUE, 0);
		assertReturnCode("cacheSessionHandle.sendCacheRequest", rc,
				SolEnum.ReturnCode.OK);

		print("Cache request sent.");

		// Confirm the message was received
		if (messageCallback.getMessageCount() == 1)
			print("Test Passed");
		else
			throw new IllegalStateException("TEST FAILED: messageCount["
					+ messageCallback.getMessageCount() + "]");
		print("Run() DONE");

	}

	/**
	 * Invoked when the sample finishes
	 */
	@Override
	protected void finish(int status) {

		/*************************************************************************
		 * Cleanup
		 *************************************************************************/

		finish_DestroyHandle(cacheSessionHandle, "cacheSessionHandle");

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
		SyncCacheRequest sample = new SyncCacheRequest();
		sample.run(args);
	}

}
