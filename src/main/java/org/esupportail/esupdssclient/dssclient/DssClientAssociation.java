package org.esupportail.esupdssclient.dssclient;

import java.util.Objects;

public class DssClientAssociation {

	private final String associatedUrl;
	private final String deviceId;
	private final String secret;
	private final String websocketUrl;

	public DssClientAssociation(String associatedUrl, String deviceId, String secret, String websocketUrl) {
		this.associatedUrl = associatedUrl;
		this.deviceId = deviceId;
		this.secret = secret;
		this.websocketUrl = websocketUrl;
	}

	public String getAssociatedUrl() {
		return associatedUrl;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getSecret() {
		return secret;
	}

	public String getWebsocketUrl() {
		return websocketUrl;
	}

	public boolean isComplete() {
		return isNotBlank(associatedUrl) && isNotBlank(deviceId) && isNotBlank(secret) && isNotBlank(websocketUrl);
	}

	private boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof DssClientAssociation association)) {
			return false;
		}
		return Objects.equals(deviceId, association.deviceId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(deviceId);
	}
}
