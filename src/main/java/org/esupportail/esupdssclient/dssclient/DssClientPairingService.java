package org.esupportail.esupdssclient.dssclient;

import com.google.gson.Gson;
import org.esupportail.esupdssclient.EsupDSSClientLauncher;
import org.esupportail.esupdssclient.UserPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DssClientPairingService {

	private static final Logger logger = LoggerFactory.getLogger(DssClientPairingService.class);
	static final String ALLOW_INSECURE_HTTP_PROPERTY = "dssClient.allowInsecureHttp";

	private final HttpClient httpClient;
	private final Gson gson = new Gson();
	private final UserPreferences preferences;
	private final String clientVersion;
	private final SecureRandom secureRandom = new SecureRandom();

	public DssClientPairingService(UserPreferences preferences, String clientVersion) {
		this.preferences = preferences;
		this.clientVersion = clientVersion;
		this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	public PairingResponse pair(String pairingUrlOrCode) throws IOException, InterruptedException {
		String apiUrl = buildPairingApiUrl(pairingUrlOrCode);
		String associatedUrl = buildAssociatedUrl(pairingUrlOrCode);
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
		validateWebsocketUrl(associatedUrl, pairingResponse.websocketUrl);
		preferences.addDssClientAssociation(new DssClientAssociation(associatedUrl, pairingResponse.deviceId,
				pairingResponse.secret, pairingResponse.websocketUrl));
		logger.info("Pairing succeeded for device {}. WebSocket endpoint: {}", pairingResponse.deviceId, pairingResponse.websocketUrl);
		return pairingResponse;
	}

	public void revoke(DssClientAssociation association) throws IOException, InterruptedException {
		if (isInsecureScheme(URI.create(association.getAssociatedUrl()).getScheme()) && !isInsecureHttpAllowed()) {
			throw new IOException("HTTP non sécurisé désactivé ; utilisez -D" + ALLOW_INSECURE_HTTP_PROPERTY + "=true pour le développement");
		}
		String timestamp = Instant.now().toString();
		byte[] nonceBytes = new byte[18];
		secureRandom.nextBytes(nonceBytes);
		String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("type", "revoke");
		payload.put("deviceId", association.getDeviceId());
		payload.put("timestamp", timestamp);
		payload.put("nonce", nonce);
		payload.put("signature", signature(association.getSecret(),
				association.getDeviceId() + "|" + timestamp + "|" + nonce));

		HttpRequest request = HttpRequest.newBuilder(
				URI.create(association.getAssociatedUrl() + "/dss-client/api/revoke"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Révocation refusée par esup-signature (" + response.statusCode() + ")");
		}
		logger.info("Association revoked on esup-signature for device {}", association.getDeviceId());
	}

	private String signature(String secret, String value) throws IOException {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IOException("Impossible de signer la demande de révocation", e);
		}
	}

	private String buildPairingApiUrl(String pairingUrlOrCode) {
		return buildAssociatedUrl(pairingUrlOrCode) + "/dss-client/api/pair";
	}

	static String buildAssociatedUrl(String pairingUrlOrCode) {
		URI pairingUri = URI.create(pairingUrlOrCode.trim());
		String scheme = pairingUri.getScheme() == null ? null : pairingUri.getScheme().toLowerCase();
		if (!("http".equals(scheme) || "https".equals(scheme)) || pairingUri.getHost() == null
					|| pairingUri.getUserInfo() != null || pairingUri.getFragment() != null) {
			throw new IllegalArgumentException("Veuillez coller l'URL complete fournie par esup-signature");
		}
		if (isInsecureScheme(scheme) && !isInsecureHttpAllowed()) {
			throw new IllegalArgumentException("HTTP non sécurisé désactivé ; utilisez -D"
					+ ALLOW_INSECURE_HTTP_PROPERTY + "=true pour le développement");
		}
		StringBuilder root = new StringBuilder();
		root.append(scheme).append("://");
		if (pairingUri.getHost().contains(":")) {
			root.append('[').append(pairingUri.getHost()).append(']');
		} else {
			root.append(pairingUri.getHost());
		}
		if (pairingUri.getPort() > 0) {
			root.append(':').append(pairingUri.getPort());
		}
		String path = pairingUri.getPath();
		String contextPath = "";
		int marker = path == null ? -1 : path.indexOf("/dss-client/pair");
		if (marker < 0 || !path.endsWith("/dss-client/pair")) {
			throw new IllegalArgumentException("URL d'association esup-signature invalide");
		}
		if (marker > 0) {
			contextPath = path.substring(0, marker);
		}
		return root + contextPath;
	}

	static void validateWebsocketUrl(String associatedUrl, String websocketUrl) throws IOException {
		try {
			URI associated = URI.create(associatedUrl);
			URI websocket = URI.create(websocketUrl);
			if (isInsecureScheme(associated.getScheme()) && !isInsecureHttpAllowed()) {
				throw new IOException("HTTP non sécurisé désactivé");
			}
			String expectedScheme = "https".equalsIgnoreCase(associated.getScheme()) ? "wss" : "ws";
			String expectedPath = normalizedPath(associated.getPath()) + "/dss-client/ws";
			if (!expectedScheme.equalsIgnoreCase(websocket.getScheme())
					|| websocket.getHost() == null
					|| !associated.getHost().equalsIgnoreCase(websocket.getHost())
					|| effectivePort(associated) != effectivePort(websocket)
					|| !expectedPath.equals(normalizedPath(websocket.getPath()))
					|| websocket.getUserInfo() != null || websocket.getQuery() != null || websocket.getFragment() != null) {
				throw new IOException("L'adresse WebSocket retournee ne correspond pas a l'instance esup-signature associee");
			}
		} catch (IllegalArgumentException e) {
			throw new IOException("Adresse WebSocket d'association invalide", e);
		}
	}

	static boolean isInsecureHttpAllowed() {
		String configuredValue = "false";
		if (EsupDSSClientLauncher.getProperties() != null) {
			configuredValue = EsupDSSClientLauncher.getProperties()
					.getProperty(ALLOW_INSECURE_HTTP_PROPERTY, configuredValue);
		}
		return Boolean.parseBoolean(System.getProperty(ALLOW_INSECURE_HTTP_PROPERTY, configuredValue));
	}

	private static boolean isInsecureScheme(String scheme) {
		return "http".equalsIgnoreCase(scheme) || "ws".equalsIgnoreCase(scheme);
	}

	private static int effectivePort(URI uri) {
		if (uri.getPort() >= 0) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static String normalizedPath(String path) {
		if (path == null || path.isBlank() || "/".equals(path)) {
			return "";
		}
		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
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

		public String getApprovalCode() {
			return DssClientApprovalCode.calculate(deviceId, secret);
		}
	}
}
