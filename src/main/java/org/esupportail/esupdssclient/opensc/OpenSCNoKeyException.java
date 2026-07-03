package org.esupportail.esupdssclient.opensc;

import eu.europa.esig.dss.model.DSSException;

public class OpenSCNoKeyException extends DSSException {

	public OpenSCNoKeyException(final String message) {
		super(message);
	}

	public OpenSCNoKeyException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
