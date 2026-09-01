/**
 * © Nowina Solutions, 2015-2015
 *
 * Concédée sous licence EUPL, version 1.1 ou – dès leur approbation par la Commission européenne - versions ultérieures de l’EUPL (la «Licence»).
 * Vous ne pouvez utiliser la présente œuvre que conformément à la Licence.
 * Vous pouvez obtenir une copie de la Licence à l’adresse suivante:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Sauf obligation légale ou contractuelle écrite, le logiciel distribué sous la Licence est distribué «en l’état»,
 * SANS GARANTIES OU CONDITIONS QUELLES QU’ELLES SOIENT, expresses ou implicites.
 * Consultez la Licence pour les autorisations et les restrictions linguistiques spécifiques relevant de la Licence.
 */
package org.esupportail.esupdssclient;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.esupportail.esupdssclient.dssclient.DssClientAssociation;
import org.esupportail.esupdssclient.dssclient.secret.PreferencesSecretStore;
import org.esupportail.esupdssclient.dssclient.secret.SecretStore;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class UserPreferences {

	private static final String DRIVER = "org.esupportail.esupdssclient.driver";
	private static final String CERT_ID = "org.esupportail.esupdssclient.certId";
	private static final String USE_SYSTEM_PROXY = "org.esupportail.esupdssclient.useSystemProxy";
	private static final String PROXY_SERVER = "org.esupportail.esupdssclient.proxyServer";
	private static final String PROXY_PORT = "org.esupportail.esupdssclient.proxyPort";
	private static final String PROXY_AUTHENTICATION = "org.esupportail.esupdssclient.proxyAuthentication";
	private static final String PROXY_USERNAME = "org.esupportail.esupdssclient.proxyUsername";
	private static final String PROXY_PASSWORD = "org.esupportail.esupdssclient.proxyPassword";
	private static final String PROXY_USE_HTTPS = "org.esupportail.esupdssclient.proxyHttps";
	private static final String DSS_CLIENT_DEVICE_ID = "org.esupportail.esupdssclient.dssClient.deviceId";
	private static final String DSS_CLIENT_SECRET = "org.esupportail.esupdssclient.dssClient.secret";
	private static final String DSS_CLIENT_WEBSOCKET_URL = "org.esupportail.esupdssclient.dssClient.websocketUrl";
	private static final String DSS_CLIENT_ASSOCIATED_URL = "org.esupportail.esupdssclient.dssClient.associatedUrl";
	private static final String DSS_CLIENT_ASSOCIATIONS = "org.esupportail.esupdssclient.dssClient.associations";
	private static final Type STORED_DSS_CLIENT_ASSOCIATION_LIST_TYPE =
			new TypeToken<List<StoredDssClientAssociation>>() {}.getType();

	private final Preferences prefs;
	private final SecretStore secretStore;
	private final Gson gson = new Gson();

	private String driver;
	private String certId;
	private Boolean useSystemProxy;
	private String proxyServer;
	private Integer proxyPort;
	private Boolean proxyUseHttps;
	private Boolean proxyAuthentication;
	private String proxyUsername;
	private String proxyPassword;
	private List<DssClientAssociation> dssClientAssociations;

	public UserPreferences(final String applicationName) {
		this(Preferences.userRoot().node(applicationName));
	}

	private UserPreferences(Preferences preferences) {
		this(preferences, new PreferencesSecretStore(preferences));
	}

	public UserPreferences(final Preferences preferences, final SecretStore secretStore) {
		this.prefs = preferences;
		this.secretStore = secretStore;

		driver = prefs.get(DRIVER, null);

		certId = prefs.get(CERT_ID, null);

		final String useSystemProxyStr = prefs.get(USE_SYSTEM_PROXY, null);
		useSystemProxy = (useSystemProxyStr != null) ? Boolean.valueOf(useSystemProxyStr) : null;
		
		proxyServer = prefs.get(PROXY_SERVER, null);
		
		final String proxyPortStr = prefs.get(PROXY_PORT, null);
		proxyPort = (proxyPortStr != null) ? Integer.valueOf(proxyPortStr) : null;
		
		final String proxyHttps = prefs.get(PROXY_USE_HTTPS, null);
		proxyUseHttps = (proxyHttps != null) ? Boolean.valueOf(proxyHttps) : null;
		
		final String proxyAuthenticationStr = prefs.get(PROXY_AUTHENTICATION, null);
		proxyAuthentication = (proxyAuthenticationStr != null) ? Boolean.valueOf(proxyAuthenticationStr) : null;
		
		proxyUsername = prefs.get(PROXY_USERNAME, null);
		proxyPassword = prefs.get(PROXY_PASSWORD, null);
		dssClientAssociations = loadDssClientAssociations();
	}

	public void setDriver(String driver) {
		if(driver != null) {
			prefs.put(DRIVER, driver);
		} else {
			prefs.remove(DRIVER);
		}
		this.driver = driver;
	}

	public void setCertId(String certId) {
		if(certId != null) {
			prefs.put(CERT_ID, certId);
		} else {
			prefs.remove(CERT_ID);
		}
		this.certId = certId;
	}

	public void setUseSystemProxy(Boolean useSystemProxy) {
		if(useSystemProxy != null) {
			prefs.putBoolean(USE_SYSTEM_PROXY, useSystemProxy);
		} else {
			prefs.remove(USE_SYSTEM_PROXY);
		}
		this.useSystemProxy = useSystemProxy;
	}

	public void setProxyServer(String proxyServer) {
		if(proxyServer != null) {
			prefs.put(PROXY_SERVER, proxyServer);
		} else {
			prefs.remove(PROXY_SERVER);
		}
		this.proxyServer = proxyServer;
	}

	public void setProxyPort(Integer proxyPort) {
		if(proxyPort != null) {
			prefs.putInt(PROXY_PORT, proxyPort);
		} else {
			prefs.remove(PROXY_PORT);
		}
		this.proxyPort = proxyPort;
	}
	
	public void setProxyUseHttps(Boolean proxyUseHttps) {
		if(proxyUseHttps != null) {
			prefs.putBoolean(PROXY_USE_HTTPS, proxyUseHttps);
		} else {
			prefs.remove(PROXY_USE_HTTPS);
		}
		this.proxyUseHttps = proxyUseHttps;
	}

	public void setProxyAuthentication(Boolean proxyAuthentication) {
		if(proxyAuthentication != null) {
			prefs.putBoolean(PROXY_AUTHENTICATION, proxyAuthentication);
		} else {
			prefs.remove(PROXY_AUTHENTICATION);
		}
		this.proxyAuthentication = proxyAuthentication;
	}

	public void setProxyUsername(String proxyUsername) {
		if(proxyUsername != null) {
			prefs.put(PROXY_USERNAME, proxyUsername);
		} else {
			prefs.remove(PROXY_USERNAME);
		}
		this.proxyUsername = proxyUsername;
	}

	public void setProxyPassword(String proxyPassword) {
		if(proxyPassword != null) {
			prefs.put(PROXY_PASSWORD, proxyPassword);
		} else {
			prefs.remove(PROXY_PASSWORD);
		}
		this.proxyPassword = proxyPassword;
	}

	public String getDriver() {
		return driver;
	}

	public String getCertId() {
		return certId;
	}

	public Boolean isUseSystemProxy() {
		return useSystemProxy;
	}

	public String getProxyServer() {
		return proxyServer;
	}

	public Integer getProxyPort() {
		return proxyPort;
	}
	
	public Boolean isProxyUseHttps() {
		return proxyUseHttps;
	}

	public Boolean isProxyAuthentication() {
		return proxyAuthentication;
	}

	public String getProxyUsername() {
		return proxyUsername;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public synchronized boolean hasDssClientCredential() {
		return !dssClientAssociations.isEmpty();
	}

	public synchronized List<DssClientAssociation> getDssClientAssociations() {
		return List.copyOf(dssClientAssociations);
	}

	public synchronized void addDssClientAssociation(DssClientAssociation association) {
		if (association == null || !association.isComplete()) {
			throw new IllegalArgumentException("L'association esup-signature est incomplete");
		}
		List<DssClientAssociation> replaced = dssClientAssociations.stream()
				.filter(existing -> existing.getDeviceId().equals(association.getDeviceId())
						|| existing.getAssociatedUrl().equals(association.getAssociatedUrl()))
				.toList();
		secretStore.store(association.getDeviceId(), association.getSecret());
		List<DssClientAssociation> updated = new ArrayList<>(dssClientAssociations);
		updated.removeAll(replaced);
		updated.add(association);
		storeDssClientAssociations(updated);
		replaced.stream()
				.filter(existing -> !existing.getDeviceId().equals(association.getDeviceId()))
				.forEach(existing -> secretStore.delete(existing.getDeviceId()));
		dssClientAssociations = updated;
	}

	public synchronized void removeDssClientAssociation(String deviceId) {
		List<DssClientAssociation> updated = dssClientAssociations.stream()
				.filter(association -> !association.getDeviceId().equals(deviceId))
				.toList();
		if (updated.size() != dssClientAssociations.size()) {
			storeDssClientAssociations(updated);
			secretStore.revoke(deviceId);
			dssClientAssociations = new ArrayList<>(updated);
		}
	}

	private List<DssClientAssociation> loadDssClientAssociations() {
		String storedAssociations = prefs.get(DSS_CLIENT_ASSOCIATIONS, null);
		if (storedAssociations != null && !storedAssociations.isBlank()) {
			try {
				List<StoredDssClientAssociation> stored = gson.fromJson(
						storedAssociations, STORED_DSS_CLIENT_ASSOCIATION_LIST_TYPE);
				if (stored != null) {
					boolean migrated = stored.stream().anyMatch(association -> isNotBlank(association.secret));
					List<DssClientAssociation> associations = stored.stream()
							.map(this::restoreAssociation)
							.filter(DssClientAssociation::isComplete)
							.toList();
					if (migrated) {
						storeDssClientAssociations(associations);
					}
					return new ArrayList<>(associations);
				}
			} catch (JsonParseException ignored) {
				// An unreadable local value is treated as an absent association.
			}
			return new ArrayList<>();
		}

		List<DssClientAssociation> associations = new ArrayList<>();
		String legacySecret = prefs.get(DSS_CLIENT_SECRET, null);
		DssClientAssociation legacyAssociation = new DssClientAssociation(
				prefs.get(DSS_CLIENT_ASSOCIATED_URL, null),
				prefs.get(DSS_CLIENT_DEVICE_ID, null),
				legacySecret,
				prefs.get(DSS_CLIENT_WEBSOCKET_URL, null));
		if (legacyAssociation.isComplete()) {
			secretStore.store(legacyAssociation.getDeviceId(), legacySecret);
			associations.add(legacyAssociation);
			storeDssClientAssociations(associations);
		}
		removeLegacyDssClientCredential();
		return associations;
	}

	private void storeDssClientAssociations() {
		storeDssClientAssociations(dssClientAssociations);
	}

	private void storeDssClientAssociations(List<DssClientAssociation> associations) {
		try {
			if (associations.isEmpty()) {
				prefs.remove(DSS_CLIENT_ASSOCIATIONS);
			} else {
				List<StoredDssClientAssociation> stored = associations.stream()
						.map(StoredDssClientAssociation::fromAssociation)
						.toList();
				prefs.put(DSS_CLIENT_ASSOCIATIONS, gson.toJson(stored));
			}
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new IllegalStateException("Impossible d'enregistrer les associations esup-signature", e);
		}
	}

	private DssClientAssociation restoreAssociation(StoredDssClientAssociation stored) {
		if (isNotBlank(stored.secret) && isNotBlank(stored.deviceId)) {
			secretStore.store(stored.deviceId, stored.secret);
		}
		String secret = isNotBlank(stored.deviceId) ? secretStore.retrieve(stored.deviceId).orElse(null) : null;
		return new DssClientAssociation(stored.associatedUrl, stored.deviceId, secret, stored.websocketUrl);
	}

	private boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	private void removeLegacyDssClientCredential() {
		prefs.remove(DSS_CLIENT_DEVICE_ID);
		prefs.remove(DSS_CLIENT_SECRET);
		prefs.remove(DSS_CLIENT_WEBSOCKET_URL);
		prefs.remove(DSS_CLIENT_ASSOCIATED_URL);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new IllegalStateException("Impossible de terminer la migration de l'association esup-signature", e);
		}
	}

	public void clear() {
		dssClientAssociations.forEach(association -> secretStore.delete(association.getDeviceId()));
		try {
			this.prefs.clear();
		} catch (BackingStoreException e) {
			throw new IllegalStateException(e);
		}
		useSystemProxy = null;
		proxyUseHttps = null;
		proxyServer = null;
		proxyPort = null;
		proxyAuthentication = null;
		proxyUsername = null;
		proxyPassword = null;
		dssClientAssociations = new ArrayList<>();
	}

	private static final class StoredDssClientAssociation {
		private String associatedUrl;
		private String deviceId;
		private String websocketUrl;
		// Read only during migration from the previous JSON representation.
		private String secret;

		private static StoredDssClientAssociation fromAssociation(DssClientAssociation association) {
			StoredDssClientAssociation stored = new StoredDssClientAssociation();
			stored.associatedUrl = association.getAssociatedUrl();
			stored.deviceId = association.getDeviceId();
			stored.websocketUrl = association.getWebsocketUrl();
			return stored;
		}
	}
}
