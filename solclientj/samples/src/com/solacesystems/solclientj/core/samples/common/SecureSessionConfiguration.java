/**
 * Copyright 2004-2017 Solace Corporation. All rights reserved.
 *
 */
package com.solacesystems.solclientj.core.samples.common;

public class SecureSessionConfiguration extends SessionConfiguration {

	private String protocol;
	private String excludedProtocols;
	private String ciphers;
	private String trustStoreDir;
	private String commonNames;
	private boolean validateCertificates = false;
	private boolean validateCertificateDates = false;
	private String sslDowngrade;
	private String privateKeyFile;
	private String privateKeyPassword;
	private String certificateFile;

	@Override
	public String toString() {
		StringBuilder bldr = new StringBuilder(super.toString());
		bldr.append(", protocol=");
		bldr.append(protocol);
		bldr.append(", ciphers=");
		bldr.append(ciphers);
		bldr.append(", trustStoreDir=");
		bldr.append(trustStoreDir);
		bldr.append(", keyStore=");
		bldr.append(privateKeyFile);
		bldr.append(", commonNames=");
		bldr.append(commonNames);
		bldr.append(", validateCertificates=");
		bldr.append(validateCertificates);
		bldr.append(", validateCertificateDates=");
		bldr.append(validateCertificateDates);
		bldr.append(", sslDowngrade=");
		bldr.append(sslDowngrade);
		return bldr.toString();
	}

	/**
	 * @return the protocol
	 */
	public String getProtocol() {
		return protocol;
	}
	/**
	 * @return the excludeProtocols
	 */
	public String getExcludedProtocols() {
		return excludedProtocols;
	}
	/**
	 * @param protocol
	 *            the protocol to set
	 */
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	/**
	 * @param excludedProtocols
	 *            comma separated list of excluded SSL protocol(s).
	 */
	public void setExcludedProtocols(String protocol) {
		this.excludedProtocols = protocol;
	}

	/**
	 * @return the ciphers
	 */
	public String getCiphers() {
		return ciphers;
	}

	/**
	 * @param ciphers
	 *            the ciphers to set
	 */
	public void setCiphers(String ciphers) {
		this.ciphers = ciphers;
	}

	/**
	 * @return the trustStoreDir
	 */
	public String getTrustStoreDir() {
		return trustStoreDir;
	}

	/**
	 * @param trustStoreDir
	 *            the trustStoreDir to set
	 */
	public void setTrustStoreDir(String trustStoreDir) {
		this.trustStoreDir = trustStoreDir;
	}

	/**
	 * @return the commonNames
	 */
	public String getCommonNames() {
		return commonNames;
	}

	/**
	 * @param commonNames
	 *            the commonNames to set
	 */
	public void setCommonNames(String commonNames) {
		this.commonNames = commonNames;
	}

	/**
	 * @return the validateCertificates
	 */
	public boolean getValidateCertificates() {
		return validateCertificates;
	}

	/**
	 * @param validateCertificates
	 *            the validateCertificates to set
	 */
	public void setValidateCertificates(boolean validateCertificates) {
		this.validateCertificates = validateCertificates;
	}

	/**
	 * @return the validateCertificateDates
	 */
	public boolean getValidateCertificateDates() {
		return validateCertificateDates;
	}

	/**
	 * @param validateCertificateDates
	 *            the validateCertificateDates to set
	 */
	public void setValidateCertificateDates(boolean validateCertificateDates) {
		this.validateCertificateDates = validateCertificateDates;
	}

	/**
	 * @return the privateKeyFile
	 */
	public String getPrivateKeyFile() {
		return privateKeyFile;
	}

	/**
	 * @param privateKeyFile
	 *            the privateKeyFile to set
	 */
	public void setPrivateKeyFile(String privateKeyFile) {
		this.privateKeyFile = privateKeyFile;
	}

	/**
	 * @return the privateKeyPassword
	 */
	public String getPrivateKeyPassword() {
		return privateKeyPassword;
	}

	/**
	 * @param privateKeyPassword
	 *            the privateKeyPassword to set
	 */
	public void setPrivateKeyPassword(String privateKeyPassword) {
		this.privateKeyPassword = privateKeyPassword;
	}

	/**
	 * @return the certificateFile
	 */
	public String getCertificateFile() {
		return certificateFile;
	}

	/**
	 * @param certificateFile
	 *            the certificateFile to set
	 */
	public void setCertificateFile(String certificateFile) {
		this.certificateFile = certificateFile;
	}

	/**
	 * @return the sslDowngrade
	 */
	public String getSslDowngrade() {
		return sslDowngrade;
	}

	/**
	 * @param sslDowngrade
	 *            the sslDowngrade to set
	 */
	public void setSslDowngrade(String sslDowngrade) {
		this.sslDowngrade = sslDowngrade;
	}	
}
