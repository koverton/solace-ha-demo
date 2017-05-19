/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.impl.util.SolEnumToStringUtil;
import com.solacesystems.solclientj.core.samples.common.SampleUtils.UserVpn;

public class ArgumentsParser {
	SessionConfiguration sc;

	public void setConfig(SessionConfiguration value) {
		this.sc = value;
	}

	public SessionConfiguration getConfig() {
		return sc;
	}

	public boolean isSecure() {
		return sc instanceof SecureSessionConfiguration;
	}

	/**
	 * Parse command-line: the common params are stored in dedicated
	 * SessionConfiguration fields, while program-specific params go into the
	 * argBag map field.
	 */
	public int parse(String[] args) {
		if (getConfig() == null) {
			setConfig(new SessionConfiguration());
		}

		try {
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-h")) {
					i++;
					sc.setHost(args[i]);
				} else if (args[i].equals("-x")) {
					i++;
					if (i >= args.length)
						return 1;

					if (args[i].toLowerCase().equals("client_certificate")) {
						if (!(sc instanceof SecureSessionConfiguration)) {
							System.err
									.println("Invalid value for -x : Must be either BASIC or KERBEROS");
							return 1;
						}
						sc.setAuthenticationScheme(SessionConfiguration.AuthenticationScheme.CLIENT_CERTIFICATE);
					} else if (args[i].toLowerCase().equals("basic")) {
						sc.setAuthenticationScheme(SessionConfiguration.AuthenticationScheme.BASIC);
					} else if (args[i].toLowerCase().equals("kerberos")) {
						sc.setAuthenticationScheme(SessionConfiguration.AuthenticationScheme.KERBEROS);
					} else {
						if (sc instanceof SecureSessionConfiguration) {
							System.err
									.println("Invalid value for -x : Must be either BASIC or CLIENT_CERTIFICATE or KERBEROS");
						} else {
							System.err
									.println("Invalid value for -x : Must be either BASIC or KERBEROS");
						}
						return 1;
					}
					// Do nothing, its already been handled
				} else if (args[i].equals("-u")) {
					i++;
					sc.setRouterUsername(UserVpn.parse(args[i]));
				} else if (args[i].equals("-w")) {
					i++;
					sc.setRouterPassword(args[i]);
				} else if (args[i].equals("-z")) {
					sc.setCompression(true);
				} else if (args[i].equals("-dm")) {
					i++;
					String dm = args[i].toLowerCase();
					int dmobj = parseDeliveryMode(dm);
					if (dmobj != -1)
						sc.setDeliveryMode(dmobj);
					else
						return 1; // err
				} else if (args[i].equals("--help")) {
					return 1; // err: print help
				} else {
					String str_key = args[i];
					String str_value = "";
					if (i + 1 < args.length) {
						String str_tmpvalue = args[i + 1]; // lookahead
						if (!str_tmpvalue.startsWith("-")) {
							// we have a value!
							i++;
							str_value = args[i];
						}
					}
					sc.getArgBag().put(str_key, str_value);
				}
			}
		} catch (Exception e) {
			return 1; // err
		}

		if (sc.getHost() == null
				|| (sc.getRouterUserVpn() == null && sc
						.getAuthenticationScheme()
						.equals(SessionConfiguration.AuthenticationScheme.BASIC))) {
			return 1; // err
		}

		// Make sure the username if provided when certificate authentication is
		// not used (and -u with @vpn is)
		if (sc.getRouterUserVpn() != null) {
			if (sc.getRouterUserVpn().get_user().trim().equals("")
					&& (sc.getAuthenticationScheme()
							.equals(SessionConfiguration.AuthenticationScheme.BASIC))) {
				System.err
						.println("USER must be specified when using basic authentication scheme.");
				return 1; // Must provide a username when certificate
							// authentication is not used.
			}
		}

		if (sc.getAuthenticationScheme().equals(
				SessionConfiguration.AuthenticationScheme.CLIENT_CERTIFICATE)
				&& (((SecureSessionConfiguration) sc).getCertificateFile() == null)) {
			System.err
					.println("Certificate file must be specified when using client certificate authentication scheme.");
			return 1;
		}

		return 0; // success
	}

	public int parseCacheSampleArgs(String[] args) {
		CacheSessionConfiguration cf = new CacheSessionConfiguration();
		this.sc = cf;
		parse(args); // parse common arguments

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-c")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setCacheName(args[i]);
			} else if (args[i].equals("-m")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setMaxMsgs(Integer.parseInt(args[i]));
			} else if (args[i].equals("-a")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setMaxAge(Integer.parseInt(args[i]));
			} else if (args[i].equals("-o")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setTimeout(Integer.parseInt(args[i]));
			} else if (args[i].equals("-s")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setSubscribe(Boolean.parseBoolean(args[i]));
			} else if (args[i].equals("-ld")) {
				i++;
				if (i >= args.length)
					return 1;
				// TODO covert string to enum value
				// cf.setAction(CacheLiveDataAction.valueOf(args[i]));
			}
		}

		if (cf.getCacheName() == null) {
			System.err.println("No cache name specified");
			return 1;
		}
		return 0;
	}

	public int parseSecureSampleArgs(String[] args) {

		// parse common arguments
		if (readSecureArgs(args) != 0 || parse(args) != 0) {
			return 1;
		}

		return 0;
	}

	protected int readSecureArgs(String[] args) {
		SecureSessionConfiguration cf = new SecureSessionConfiguration();
		this.sc = cf;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-no_prot")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setExcludedProtocols(args[i]);
			} else if (args[i].equals("-ciphers")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setCiphers(args[i]);
			} else if (args[i].equals("-ts")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setTrustStoreDir(args[i]);
			} else if (args[i].equals("-pkf")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setPrivateKeyFile(args[i]);
			} else if (args[i].equals("-cf")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setCertificateFile(args[i]);
			} else if (args[i].equals("-pkfpwd")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setPrivateKeyPassword(args[i]);
			} else if (args[i].equals("-no_validate_certificates")) {
				cf.setValidateCertificates(false);
			} else if (args[i].equals("-no_validate_dates")) {
				cf.setValidateCertificateDates(false);
			} else if (args[i].equals("-cn")) {
				i++;
				if (i >= args.length)
					return 1;
				cf.setCommonNames(args[i]);
			} else if (args[i].equals("-ssldowngr")) {
				i++;
				if (i >= args.length)
					return 1;
                try {
				    cf.setSslDowngrade(
                        SolEnumToStringUtil.toString(
                            SolEnum.SSLDowngradeTransportProtocol.class,
                            args[i]));
                } catch (IllegalArgumentException e) {
                    System.out.println(
                        "Cannot parse -ssldowngr option. " +
                        "Unsupported value: " + args[i]);
                    return 1;
                }
			}
		}

		return 0;
	}

	public static int parseDeliveryMode(String dm) {
		if (dm == null)
			return -1;
		dm = dm.toLowerCase();
		if (dm.equals("direct")) {
			return SolEnum.MessageDeliveryMode.DIRECT;
		} else if (dm.equals("persistent")) {
			return SolEnum.MessageDeliveryMode.PERSISTENT;
		} else if (dm.equals("non-persistent")) {
			return SolEnum.MessageDeliveryMode.NONPERSISTENT;
		} else {
			return -1;
		}
	}

	public static String getCommonUsage(boolean secure) {
		String str = "Common parameters:\n";
		str += "\t -h HOST[:PORT]  Appliance IP address [:port, omit for default]\n";
		str += "\t -u USER[@VPN]   Authentication username [@vpn, omit for default]\n";
		str += "\t[-w PASSWORD]    Authentication password\n";
		str += "\t[-z]             Enable compression\n";
		str += "\t[-x AUTH_METHOD] authentication scheme (One of : BASIC, KERBEROS). (Default: BASIC).  Specifying USER is mandatory when BASIC is used.\n";
		str += "\t[-l logLevel ]   Java Log Level to override file based configuration\n";
		str += "\t[-mm interval]   Monitor and record used memory at interval in seconds, prints a report at the end.\n";
		if (secure) {
			str += getSecureArgUsage();
		}

		return str;
	}

	public static String getCacheArgUsage(boolean secure) {
		StringBuffer buf = new StringBuffer();
		buf.append(ArgumentsParser.getCommonUsage(secure));
		buf.append("Cache request parameters:\n");
		buf.append("\t -c CACHE_NAME  Cache for the initial cache request\n");
		buf.append("\t[-m MAX_MSGS]   Maximum messages per topic to retrieve (default: 1)\n");
		buf.append("\t[-a MAX_AGE]    Maximum age of messages to retrieve (default: 0)\n");
		buf.append("\t[-o TIMEOUT]    Cache request timeout in ms (default: 5000)\n");
		buf.append("\t[-s SUBSCRIBE]  Subscribe to cache topic (default: false)\n");
		buf.append("\t[-ld ACTION]     Live data action (default: FLOW_THRU), one of\n");
		buf.append("\t                  FLOWTHRU (Pass through live data that arrives while a\n");
		buf.append("\t                             cache request is outstanding)\n");
		buf.append("\t                  QUEUE     (Queue live data that arrives that matches \n");
		buf.append("\t                             the topic until the cache request completes)\n");
		buf.append("\t                  FULFILL   (Consider the cache request finished when live\n");
		buf.append("\t                             data arrives that matches the topic)\n");
		return buf.toString();
	}

	public static String getSecureArgUsage() {
		StringBuffer buf = new StringBuffer();
		buf.append("Secure request parameters:\n");
		buf.append("\t -h HOST[:PORT]  Appliance IP address [:port, omit for default]\n");
		buf.append("\t[-u [USER][@VPN]]  Authentication username [USER part is optional with client certificate authentication (which is in use when -x CLIENT_CERTIFICATE is specified)] [@vpn, omit for default]\n");
		buf.append("\t[-x AUTH_METHOD] authentication scheme (One of : BASIC, CLIENT_CERTIFICATE, KERBEROS). (Default: BASIC).  Specifying USER is mandatory when BASIC is used.\n");
		buf.append("\t[-w PASSWORD]    Authentication password\n");
		buf.append("\t[-z]             Enable compression\n");
		buf.append("\t[-no_prot NO_PROTOCOLS]  A comma separated list of excluded SSL protocol(s) (valid SSL protocols are 'sslv3', 'tlsv1.1', 'tlsv1.2')\n");
		buf.append("\t[-ciphers CIPHERS]   A comma separated list of the cipher suites to enable (default: all supported ciphers)\n");
		buf.append("\t[-ts TRUST_STORE]   The path to a trust store file that contains trusted root CAs.  This parameter is mandatory unless -no_validate_certificates is specified.  Used to validate the Appliance's server certificate.\n");
		buf.append("\t[-pkf PRIVATE_KEY_FILE]   The private key file.  Required when -x CLIENT_CERTIFICATE is specified.\n");
		buf.append("\t[-cf CERTIFICATE_FILE]   The certificate file to use for client certificate authentication.  Required when -x CLIENT_CERTIFICATE is specified.\n");
		buf.append("\t[-pkfpwd CLIENT_PRIVATE_KEY_FILE_PASSWORD]   The password for the specified private key file. \n");
		buf.append("\t[-no_validate_certificates]  Disables validation of the server certificate (default: validation enabled)\n");
		buf.append("\t[-no_validate_dates]    Disables validation of the server certificate expiry and not before dates (default: date validation enabled)\n");
		buf.append("\t[-cn COMMON_NAMES]    Specifies the list of acceptable common names for matching in server certificates (default: no validation performed)\n");
		buf.append("\t[-ssldowngr PROTOCOL] SSL downgrade transport protocol. Allowed transport protocol is 'PLAIN_TEXT'."); 
		return buf.toString();
	}
}
