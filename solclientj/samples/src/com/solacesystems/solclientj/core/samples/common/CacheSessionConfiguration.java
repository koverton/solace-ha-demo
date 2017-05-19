/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

import com.solacesystems.solclientj.core.SolEnum;

public class CacheSessionConfiguration extends SessionConfiguration {

	private String mCacheName = null;
	private int mMaxAge = 0;
	private int mMaxMsgs = 1;
	private int mTimeout = 11000;
	private boolean mSubscribe = false;
	private int mAction = SolEnum.CacheLiveDataAction.FLOWTHRU;

	public String getCacheName() {
		return mCacheName;
	}

	public void setCacheName(String cacheName) {
		mCacheName = cacheName;
	}

	public int getMaxAge() {
		return mMaxAge;
	}

	public void setMaxAge(int maxAge) {
		mMaxAge = maxAge;
	}

	public int getMaxMsgs() {
		return mMaxMsgs;
	}

	public void setMaxMsgs(int maxMsgs) {
		mMaxMsgs = maxMsgs;
	}

	public int getTimeout() {
		return mTimeout;
	}

	public void setTimeout(int timeout) {
		mTimeout = timeout;
	}

	public boolean getSubscribe() {
		return mSubscribe;
	}

	public void setSubscribe(boolean subscribe) {
		mSubscribe = subscribe;
	}

	public int getAction() {
		return mAction;
	}

	public void setAction(int action) {
		mAction = action;
	}

	@Override
	public String toString() {
		StringBuilder bldr = new StringBuilder(super.toString());
		bldr.append(", cacheName=");
		bldr.append(mCacheName);
		bldr.append(", maxAge=");
		bldr.append(mMaxAge);
		bldr.append(", maxMsgs=");
		bldr.append(mMaxMsgs);
		bldr.append(", timeout=");
		bldr.append(mTimeout);
		bldr.append(", subscribe=");
		bldr.append(mSubscribe);
		bldr.append(", action=");
		bldr.append(mAction);
		return bldr.toString();
	}
}