package org.esupportail.esupdssclient.dssclient;

import com.google.gson.Gson;
import org.esupportail.esupdssclient.UserPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

public class DssClientPairingService {

	private static final Logger logger = LoggerFactory.getLogger(DssClientPairingService.class);

	private final HttpClient httpClient;
	private final Gson gson = new Gson();
	private final UserPreferences preferences;
	private final String clientVersion;

	public DssClientPairingService(UserPreferences preferences, String clientVersion) {
		this.preferences = preferences;
		this.clientVersion = clientVersion;
		this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	public PairingResponse pair(String pairingUrlOrCode) throws IOException, InterruptedException {
		String apiUrl = buildPairingApiUrl(pairingUrlOrCode);
		logger.info("Pairing Esup-DSS-Client with esup-signature endpoint {}", apiUrl);
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("pairingUrlOrCode", pairingUrlOrCode);
		payload.put("displayName", defaultDisplayName());
		payload.put("hostname", hostname());
		payload.put("os", System.getProperty("os.name", "unknown"));
		payload.put("clientVersion", clientVersion);

		HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		logger.info("Pairing endpoint responded with HTTP {}", response.statusCode());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Association refusee par esup-signature (" + response.statusCode() + "): " + response.body());
		}
		PairingResponse pairingResponse = gson.fromJson(response.body(), PairingResponse.class);
		if (pairingResponse == null || pairingResponse.deviceId == null || pairingResponse.secret == null || pairingResponse.websocketUrl == null) {
			throw new IOException("Reponse d'association incomplete");
		}
		preferences.setDssClientCredential(pairingResponse.deviceId, pairingResponse.secret, pairingResponse.websocketUrl);
		logger.info("Pairing succeeded for device {}. WebSocket endpoint: {}", pairingResponse.deviceId, pairingResponse.websocketUrl);
		return pairingResponse;
	}

	private String buildPairingApiUrl(String pairingUrlOrCode) {
		URI pairingUri = URI.create(pairingUrlOrCode.trim());
		String scheme = pairingUri.getScheme();
		if (scheme == null || pairingUri.getHost() == null) {
			throw new IllegalArgumentException("Veuillez coller l'URL complete fournie par esup-signature");
		}
		StringBuilder root = new StringBuilder();
		root.append(scheme).append("://").append(pairingUri.getHost());
		if (pairingUri.getPort() > 0) {
			root.append(':').append(pairingUri.getPort());
		}
		String path = pairingUri.getPath();
		String contextPath = "";
		int marker = path == null ? -1 : path.indexOf("/dss-client/pair");
		if (marker > 0) {
			contextPath = path.substring(0, marker);
		}
		return root + contextPath + "/dss-client/api/pair";
	}

	private String defaultDisplayName() {
		String hostname = hostname();
		if (hostname == null || hostname.isBlank()) {
			return "Esup-DSS-Client";
		}
		return "Esup-DSS-Client - " + hostname;
	}

	private String hostname() {
		String computerName = System.getenv("COMPUTERNAME");
		if (computerName != null && !computerName.isBlank()) {
			return computerName;
		}
		String hostName = System.getenv("HOSTNAME");
		return hostName != null ? hostName : "";
	}

	public static class PairingResponse {
		private String deviceId;
		private String secret;
		private String websocketUrl;

		public String getDeviceId() {
			return deviceId;
		}

		public String getSecret() {
			return secret;
		}

		public String getWebsocketUrl() {
			return websocketUrl;
		}
	}
}
