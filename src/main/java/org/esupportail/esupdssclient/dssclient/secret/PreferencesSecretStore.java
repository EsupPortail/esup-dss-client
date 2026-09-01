package org.esupportail.esupdssclient.dssclient.secret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * V1 secret storage backed by the current user's Java preferences.
 *
 * This implementation does not encrypt secrets. It isolates the storage
 * mechanism so it can later be replaced by DPAPI, Keychain or Secret Service.
 */
public class PreferencesSecretStore implements SecretStore {

	private static final String SECRETS_NODE = "dssClientSecrets";
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE);

	private final Preferences secrets;
	private final List<Path> filesystemDirectories;
	private final Path filesystemPreferencesFile;

	public PreferencesSecretStore(Preferences preferences) {
		this.secrets = preferences.node(SECRETS_NODE);
		this.filesystemDirectories = filesystemDirectories(this.secrets);
		this.filesystemPreferencesFile = filesystemDirectories.isEmpty()
				? null
				: filesystemDirectories.get(filesystemDirectories.size() - 1).resolve("prefs.xml");
	}

	@Override
	public synchronized void store(String secretId, String secret) {
		requireValue(secretId, "secretId");
		requireValue(secret, "secret");
		secrets.put(secretId, secret);
		flushAndHarden();
	}

	@Override
	public synchronized Optional<String> retrieve(String secretId) {
		requireValue(secretId, "secretId");
		return Optional.ofNullable(secrets.get(secretId, null)).filter(value -> !value.isBlank());
	}

	@Override
	public synchronized void delete(String secretId) {
		requireValue(secretId, "secretId");
		secrets.remove(secretId);
		flushAndHarden();
	}

	private void flushAndHarden() {
		try {
			secrets.flush();
			for (Path directory : filesystemDirectories) {
				setPosixPermissions(directory, DIRECTORY_PERMISSIONS);
			}
			setPosixPermissions(filesystemPreferencesFile, FILE_PERMISSIONS);
		} catch (BackingStoreException | IOException e) {
			throw new IllegalStateException("Impossible de sécuriser le stockage local du secret", e);
		}
	}

	private void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		if (Files.isSymbolicLink(path)) {
			throw new IOException("Le stockage des préférences ne doit pas être un lien symbolique : " + path);
		}
		if (Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
			Files.setPosixFilePermissions(path, permissions);
		}
	}

	private List<Path> filesystemDirectories(Preferences preferences) {
		if (!preferences.getClass().getName().endsWith("FileSystemPreferences")) {
			return List.of();
		}
		String base = System.getProperty("java.util.prefs.userRoot", System.getProperty("user.home"));
		if (base == null || base.isBlank()) {
			return List.of();
		}
		Path current = Path.of(base).resolve(".java").resolve(".userPrefs");
		List<Path> directories = new ArrayList<>();
		directories.add(current);
		for (String node : preferences.absolutePath().split("/")) {
			if (node.isBlank()) {
				continue;
			}
			if (!node.matches("[A-Za-z0-9.-]+")) {
				return List.of();
			}
			current = current.resolve(node);
			directories.add(current);
		}
		return List.copyOf(directories);
	}

	private void requireValue(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " ne peut pas être vide");
		}
	}
}
