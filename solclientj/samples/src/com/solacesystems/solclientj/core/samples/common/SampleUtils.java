/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

/**
 * Common utilities to support the samples
 */
public class SampleUtils {

	public static final String SAMPLE_TOPIC = "my/sample/topic";
	public static final String SAMPLE_CONFIGURED_TOPICENDPOINT = "my_sample_topicendpoint";
	public static final String SAMPLE_QUEUE = "my/sample/queue";
	public static final String SAMPLE_CONFIGURED_QUEUE = "my_sample_queue";
	public static final String COMMON_DMQ_NAME = "#DEAD_MSG_QUEUE";


	/**
	 * Structure representing a Username/VPN combination.
	 */
	public static final class UserVpn {
		private final String _user, _vpn;

		public UserVpn(String user, String vpn) {
			_user = user;
			_vpn = vpn;
		}

		public String get_user() {
			return _user;
		}

		public String get_vpn() {
			return _vpn;
		}

		public static UserVpn parse(final String uservpn) {
			final String[] parts = uservpn.split("@");
			switch (parts.length) {
			case 1:
				return new UserVpn(parts[0], "default");
			case 2:
				return new UserVpn(parts[0], parts[1]);
			}
			throw new IllegalArgumentException("Unable to parse " + uservpn);
		}

		@Override
		public String toString() {
			if (_vpn == null) {
				return _user;
			} else {
				return _user + "@" + _vpn;
			}
		}
	}

}
