package org.esupportail.esupdssclient.api;

import org.esupportail.esupdssclient.EsupDSSClientException;

public class SignatureBatchException extends EsupDSSClientException {

	private static final long serialVersionUID = 1L;

	private final int failedIndex;

	public SignatureBatchException(int failedIndex, Throwable cause) {
		super("La signature a echoue pour l'element d'index " + failedIndex, cause);
		this.failedIndex = failedIndex;
	}

	public int getFailedIndex() {
		return failedIndex;
	}
}
