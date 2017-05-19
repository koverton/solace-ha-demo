/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

import java.lang.reflect.Array;

/**
 * An example of using a sparse array to bind and object and a long correlation
 * key
 * 
 * @param <T>
 */
public class CorrelationArrayUtil<T> {

	private static final int WINDOWSIZE = 255;

	// Just make it big enough to feel safe and avoid any roll backs
	private static final int ARRAYSIZE = (WINDOWSIZE * 2) + 2;

	private T[] correlationArray;

	// Using a start position of 1 to ensure Zero values are reserved for null
	// representation.
	private long correlationKey = 1;

	@SuppressWarnings("unchecked")
	public CorrelationArrayUtil(Class<T> clazz) {
		this.correlationArray = (T[]) Array.newInstance(clazz, ARRAYSIZE);
	}

	/**
	 * Given a correlationKey, remove the correlation and return whatever was
	 * correlated with the correlationKey
	 * 
	 * @param key
	 *            a correlationKey
	 * @return What ever was correlated to the correlationKey (could be null)
	 */
	public T uncorrelate(long key) {
		T t = correlationArray[(int) key];
		correlationArray[(int) key] = null;
		return t;
	}

	/**
	 * Given an object of type t, it will be stored into a correlation array, and
	 * a correlationKey is returned, access to the correlation array is
	 * synchronized.
	 * 
	 * @param t
	 * @return a correlationKey
	 */
	public long correlate(T t) {

		synchronized (correlationArray) {
			if (correlationKey == ARRAYSIZE-1) {
				correlationKey = 1;
			} else
				correlationKey++;

			correlationArray[(int) correlationKey] = t;
		}

		return correlationKey;
	}

}
