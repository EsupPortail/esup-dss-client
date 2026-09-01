package org.esupportail.esupdssclient;

import org.esupportail.esupdssclient.dssclient.DssClientAssociation;
import org.junit.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserPreferencesTest {

	private static final String DEVICE_ID = "org.esupportail.esupdssclient.dssClient.deviceId";
	private static final String SECRET = "org.esupportail.esupdssclient.dssClient.secret";
	private static final String WEBSOCKET_URL = "org.esupportail.esupdssclient.dssClient.websocketUrl";
	private static final String ASSOCIATED_URL = "org.esupportail.esupdssclient.dssClient.associatedUrl";

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
