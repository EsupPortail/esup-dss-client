package org.esupportail.esupdssclient.api;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.ToBeSigned;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class SignatureBatchRequest extends EsupDSSClientRequest {

	private TokenId tokenId;
	private List<ToBeSigned> valuesToSign = Collections.emptyList();
	private DigestAlgorithm digestAlgorithm;
	private String keyId;
	private transient BiConsumer<Integer, Integer> progressListener;

	public TokenId getTokenId() {
		return tokenId;
	}

	public void setTokenId(TokenId tokenId) {
		this.tokenId = tokenId;
	}

	public List<ToBeSigned> getValuesToSign() {
		return valuesToSign;
	}

	public void setValuesToSign(List<ToBeSigned> valuesToSign) {
		this.valuesToSign = valuesToSign == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(valuesToSign));
	}

	public DigestAlgorithm getDigestAlgorithm() {
		return digestAlgorithm;
	}

	public void setDigestAlgorithm(DigestAlgorithm digestAlgorithm) {
		this.digestAlgorithm = digestAlgorithm;
	}

	public String getKeyId() {
		return keyId;
	}

	public void setKeyId(String keyId) {
		this.keyId = keyId;
	}

	public void setProgressListener(BiConsumer<Integer, Integer> progressListener) {
		this.progressListener = progressListener;
	}

	public void notifyProgress(int completed, int total) {
		if (progressListener != null) {
			progressListener.accept(completed, total);
		}
	}
}
