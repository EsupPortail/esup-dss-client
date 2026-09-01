package org.esupportail.esupdssclient.dssclient.secret;

import org.junit.Test;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreferencesSecretStoreTest {

	@Test
	public void storesDeletesAndRevokesSecrets() throws Exception {
		Preferences preferences = Preferences.userRoot().node("esup-dss-client-secret-test-" + UUID.randomUUID());
		try {
			SecretStore store = new PreferencesSecretStore(preferences);

			store.store("device", "secret");
			assertEquals("secret", store.retrieve("device").orElseThrow());

			store.delete("device");
			assertTrue(store.retrieve("device").isEmpty());

			store.store("device", "new-secret");
			store.revoke("device");
			assertTrue(store.retrieve("device").isEmpty());
		} finally {
			preferences.removeNode();
		}
	}

	@Test
	public void restrictsFilesystemPreferencesToCurrentUser() throws Exception {
		String applicationName = "esup-dss-client-secret-test-" + UUID.randomUUID();
		Preferences preferences = Preferences.userRoot().node(applicationName);
		try {
			new PreferencesSecretStore(preferences).store("device", "secret");
			Path secretDirectory = Path.of(System.getProperty("java.util.prefs.userRoot",
					System.getProperty("user.home")), ".java", ".userPrefs", applicationName,
					"dssClientSecrets");
			Path secretFile = secretDirectory.resolve("prefs.xml");
			if (Files.exists(secretFile)
					&& Files.getFileAttributeView(secretFile, PosixFileAttributeView.class) != null) {
				assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
						Files.getPosixFilePermissions(secretFile));
				assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
							PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(secretDirectory));
			}
		} finally {
			preferences.removeNode();
		}
	}
}
