package org.esupportail.esupdssclient.dssclient.secret;

import java.util.Optional;

/**
 * Storage abstraction for local DSS client association secrets.
 */
public interface SecretStore {

	void store(String secretId, String secret);

	Optional<String> retrieve(String secretId);

	void delete(String secretId);

	/**
	 * Permanently invalidates the local copy of a revoked association secret.
	 */
	default void revoke(String secretId) {
		delete(secretId);
	}
}
