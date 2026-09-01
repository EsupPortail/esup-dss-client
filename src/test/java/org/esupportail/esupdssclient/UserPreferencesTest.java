package org.esupportail.esupdssclient;

import org.esupportail.esupdssclient.dssclient.DssClientAssociation;
import org.esupportail.esupdssclient.dssclient.secret.PreferencesSecretStore;
import org.esupportail.esupdssclient.dssclient.secret.SecretStore;
import org.junit.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserPreferencesTest {

	private static final String DEVICE_ID = "org.esupportail.esupdssclient.dssClient.deviceId";
	private static final String SECRET = "org.esupportail.esupdssclient.dssClient.secret";
	private static final String WEBSOCKET_URL = "org.esupportail.esupdssclient.dssClient.websocketUrl";
	private static final String ASSOCIATED_URL = "org.esupportail.esupdssclient.dssClient.associatedUrl";
	private static final String ASSOCIATIONS = "org.esupportail.esupdssclient.dssClient.associations";

	@Test
	public void shouldRejectLegacyCredentialWithoutAssociatedUrl() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			storeLegacyCredential(storedPreferences, false);

			assertFalse(new UserPreferences(applicationName).hasDssClientCredential());
		} finally {
			storedPreferences.removeNode();
		}
	}

	@Test
	public void shouldMigrateCompleteLegacyCredential() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			storeLegacyCredential(storedPreferences, true);

			UserPreferences preferences = new UserPreferences(applicationName);

			assertTrue(preferences.hasDssClientCredential());
			assertEquals(1, preferences.getDssClientAssociations().size());
			assertEquals("https://signature.example.org/esup-signature",
					preferences.getDssClientAssociations().get(0).getAssociatedUrl());
			assertEquals(1, new UserPreferences(applicationName).getDssClientAssociations().size());
		} finally {
			storedPreferences.removeNode();
		}
	}

	@Test
	public void shouldStoreAndRemoveAssociationsIndividually() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			UserPreferences preferences = new UserPreferences(applicationName);
			preferences.addDssClientAssociation(association("one", "https://one.example.org"));
			preferences.addDssClientAssociation(association("two", "https://two.example.org"));

			UserPreferences reloaded = new UserPreferences(applicationName);
			assertEquals(2, reloaded.getDssClientAssociations().size());

			reloaded.removeDssClientAssociation("one");

			assertEquals(1, reloaded.getDssClientAssociations().size());
			assertEquals("two", reloaded.getDssClientAssociations().get(0).getDeviceId());
			assertEquals(1, new UserPreferences(applicationName).getDssClientAssociations().size());
		} finally {
			storedPreferences.removeNode();
		}
	}

	@Test
	public void shouldNotStoreSecretInsideAssociationMetadata() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			UserPreferences preferences = new UserPreferences(applicationName);
			preferences.addDssClientAssociation(association("one", "https://one.example.org"));

			String metadata = storedPreferences.get(ASSOCIATIONS, "");
			assertFalse(metadata.contains("secret-one"));
			assertEquals("secret-one", new UserPreferences(applicationName)
					.getDssClientAssociations().get(0).getSecret());
		} finally {
			storedPreferences.removeNode();
		}
	}

	@Test
	public void shouldMigrateSecretEmbeddedInAssociationMetadata() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			storedPreferences.put(ASSOCIATIONS, """
					[{"associatedUrl":"https://signature.example.org","deviceId":"device-id",
					"secret":"old-secret","websocketUrl":"wss://signature.example.org/ws"}]
					""");

			UserPreferences migrated = new UserPreferences(applicationName);

			assertEquals("old-secret", migrated.getDssClientAssociations().get(0).getSecret());
			assertFalse(storedPreferences.get(ASSOCIATIONS, "").contains("old-secret"));
			assertEquals("old-secret", new UserPreferences(applicationName)
					.getDssClientAssociations().get(0).getSecret());
		} finally {
			storedPreferences.removeNode();
		}
	}

	@Test
	public void shouldResumeMigrationAfterInterruption() throws Exception {
		String applicationName = applicationName();
		Preferences storedPreferences = Preferences.userRoot().node(applicationName);
		try {
			storedPreferences.put(ASSOCIATIONS, """
					[{"associatedUrl":"https://signature.example.org","deviceId":"device-id",
					"secret":"old-secret","websocketUrl":"wss://signature.example.org/ws"}]
					""");
			storedPreferences.flush();
			SecretStore delegate = new PreferencesSecretStore(storedPreferences);
			SecretStore interruptedStore = new SecretStore() {
				@Override
				public void store(String secretId, String secret) {
					delegate.store(secretId, secret);
					throw new IllegalStateException("simulated interruption");
				}

				@Override
				public java.util.Optional<String> retrieve(String secretId) {
					return delegate.retrieve(secretId);
				}

				@Override
				public void delete(String secretId) {
					delegate.delete(secretId);
				}
			};

			try {
				new UserPreferences(storedPreferences, interruptedStore);
				fail("La migration interrompue doit être signalée");
			} catch (IllegalStateException expected) {
				assertEquals("simulated interruption", expected.getMessage());
			}
			assertTrue(storedPreferences.get(ASSOCIATIONS, "").contains("old-secret"));

			UserPreferences recovered = new UserPreferences(storedPreferences, delegate);
			assertEquals("old-secret", recovered.getDssClientAssociations().get(0).getSecret());
			assertFalse(storedPreferences.get(ASSOCIATIONS, "").contains("old-secret"));
		} finally {
			storedPreferences.removeNode();
		}
	}

	private String applicationName() {
		return "esup-dss-client-test-" + UUID.randomUUID();
	}

	private void storeLegacyCredential(Preferences preferences, boolean includeAssociatedUrl) {
		preferences.put(DEVICE_ID, "device-id");
		preferences.put(SECRET, "secret");
		preferences.put(WEBSOCKET_URL, "wss://signature.example.org/ws");
		if (includeAssociatedUrl) {
			preferences.put(ASSOCIATED_URL, "https://signature.example.org/esup-signature");
		}
	}

	private DssClientAssociation association(String deviceId, String associatedUrl) {
		return new DssClientAssociation(associatedUrl, deviceId, "secret-" + deviceId,
				associatedUrl.replace("https://", "wss://") + "/ws");
	}
}
