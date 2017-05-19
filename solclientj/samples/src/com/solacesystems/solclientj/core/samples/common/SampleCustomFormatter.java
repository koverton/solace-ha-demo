package com.solacesystems.solclientj.core.samples.common;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class SampleCustomFormatter extends Formatter {

	@Override
	public String format(LogRecord record) {

		// Using a StringBuffer to make the formatted record
		StringBuffer sb = new StringBuffer();

		// Time
		sb.append(record.getMillis());
		sb.append(" - ");
		// Logger Name
		sb.append(record.getLoggerName());
		sb.append(" - ");
		// Logger Level
		sb.append(record.getLevel().getName());
		sb.append(" - ");
		// Localized message
		sb.append(formatMessage(record));
		sb.append("\n");

		return sb.toString();
	}
}
