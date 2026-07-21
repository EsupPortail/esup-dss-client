package org.esupportail.esupdssclient.api;

import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SignatureBatchResponse {

	private final List<SignatureValue> signatureValues;
	private final CertificateToken certificate;
	private final CertificateToken[] certificateChain;

	public SignatureBatchResponse(List<SignatureValue> signatureValues, CertificateToken certificate,
			CertificateToken[] certificateChain) {
		this.signatureValues = Collections.unmodifiableList(new ArrayList<>(signatureValues));
		this.certificate = certificate;
		this.certificateChain = certificateChain;
	}

	public List<SignatureValue> getSignatureValues() {
		return signatureValues;
	}

	public CertificateToken getCertificate() {
		return certificate;
	}

	public CertificateToken[] getCertificateChain() {
		return certificateChain;
	}
}
