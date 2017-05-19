/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Logger;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.Solclient;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.samples.common.AbstractSample;
import com.solacesystems.solclientj.core.samples.common.ArgumentsParser;
import com.solacesystems.solclientj.core.samples.common.SessionConfiguration;

/**
 * 
 * Logging.java
 * 
 * This sample demonstrates:
 * <ul>
 * <li>Setting up a logging callback.
 * <li>Changing the log level.
 * </ul>
 * 
 * <p>
 * This sample demonstrates using the Java logging facilities to write
 * solclientj log messages to a file, as well as to the console. This is a
 * basic example, so there is no interaction with the appliance.
 * 
 * <strong>This sample illustrates the ease of use of concepts, and may not be
 * GC-free.<br>
 * See Perf* samples for GC-free examples. </strong>
 */
public class Logging extends AbstractSample {

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

		// Add a custom handler to write logs to file
		try {
			Handler handler = new FileHandler("test.log");
			Logger.getLogger("").addHandler(handler);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Init
		print(" Initializing the Java RTO Messaging API...");
		int rc = Solclient.init(new String[0]);
		assertReturnCode("Solclient.init()", rc, SolEnum.ReturnCode.OK);

		// Set a log level (not necessary as there is a default)
		Solclient.setLogLevel(logLevel);

		// Session
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

		finish_Solclient();
	}

/**
     * Boilerplate, calls {@link #run(String[])
     * @param args
     */
	public static void main(String[] args) {
		Logging sample = new Logging();
		sample.run(args);
	}

}
