package org.esupportail.esupdssclient.dssclient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.esupportail.esupdssclient.StandaloneUIDisplay;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

public class DssClientWebSocketService implements WebSocket.Listener {

	private static final Logger logger = LoggerFactory.getLogger(DssClientWebSocketService.class);

	private final EsupDSSClientAPI api;
	private final UserPreferences preferences;
	private final StandaloneUIDisplay uiDisplay;
	private final Gson gson = new Gson();
	private final SecureRandom secureRandom = new SecureRandom();
	private final DssClientPromptWindow promptWindow = new DssClientPromptWindow();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "dss-client-wss-heartbeat");
		thread.setDaemon(true);
		return thread;
	});

	private volatile WebSocket webSocket;
	private volatile ScheduledFuture<?> heartbeat;
	private volatile GetCertificateResponse lastCertificate;
	private volatile DocumentContext lastDocumentContext;

	public DssClientWebSocketService(EsupDSSClientAPI api, UserPreferences preferences, StandaloneUIDisplay uiDisplay) {
		this.api = api;
		this.preferences = preferences;
		this.uiDisplay = uiDisplay;
	}

	public void startIfConfigured() {
		if (!preferences.hasDssClientCredential()) {
			logger.info("No associated esup-signature device configured.");
			return;
		}
		logger.info("Associated esup-signature device found: {}. Starting WebSocket connection.",
				maskDeviceId(preferences.getDssClientDeviceId()));
		connect();
	}

	public synchronized void connect() {
		if (!preferences.hasDssClientCredential()) {
			return;
		}
		if (webSocket != null && !webSocket.isOutputClosed() && !webSocket.isInputClosed()) {
			return;
		}
		logger.info("Connecting to esup-signature DSS client WebSocket {} with device {}",
				preferences.getDssClientWebsocketUrl(), maskDeviceId(preferences.getDssClientDeviceId()));
		URI websocketUri = URI.create(preferences.getDssClientWebsocketUrl());
		HttpClient.newHttpClient().newWebSocketBuilder()
				.header("Origin", originFor(websocketUri))
				.header("User-Agent", "Esup-DSS-Client/" + api.getAppConfig().getApplicationVersion())
				.buildAsync(websocketUri, this)
				.whenComplete((socket, error) -> {
					if (error != null) {
						logger.warn("Unable to connect DSS client WebSocket {} for device {}: {}",
								preferences.getDssClientWebsocketUrl(), maskDeviceId(preferences.getDssClientDeviceId()),
								error.getMessage(), error);
						logHandshakeFailure(error);
						scheduleReconnect();
					} else {
						logger.info("DSS client WebSocket build completed for device {}", maskDeviceId(preferences.getDssClientDeviceId()));
						webSocket = socket;
					}
				});
	}

	private String originFor(URI websocketUri) {
		String scheme = "wss".equalsIgnoreCase(websocketUri.getScheme()) ? "https" : "http";
		StringBuilder origin = new StringBuilder(scheme).append("://").append(websocketUri.getHost());
		if (websocketUri.getPort() > 0) {
			origin.append(':').append(websocketUri.getPort());
		}
		return origin.toString();
	}

	private void logHandshakeFailure(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof WebSocketHandshakeException handshakeException) {
				HttpResponse<?> response = handshakeException.getResponse();
				if (response != null) {
					logger.warn("DSS client WebSocket handshake HTTP response: status={}, uri={}, headers={}",
							response.statusCode(), response.uri(), response.headers().map());
				}
				return;
			}
			current = current.getCause();
		}
	}

	public synchronized void stop() {
		if (heartbeat != null) {
			heartbeat.cancel(true);
			heartbeat = null;
		}
		WebSocket socket = webSocket;
		webSocket = null;
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
		}
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		this.webSocket = webSocket;
		logger.info("DSS client WebSocket opened for device {}", maskDeviceId(preferences.getDssClientDeviceId()));
		sendHello(webSocket);
		startHeartbeat();
		WebSocket.Listener.super.onOpen(webSocket);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		if (!last) {
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}
		try {
			logger.debug("DSS client WebSocket received payload: {}", data);
			JsonObject message = JsonParser.parseString(data.toString()).getAsJsonObject();
			handleMessage(message);
		} catch (Exception e) {
			logger.error("Unable to process DSS client WebSocket message", e);
			sendError(null, "client.exception", e.getMessage());
		} finally {
			webSocket.request(1);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		logger.warn("DSS client WebSocket closed for device {}: status={} reason={}",
				maskDeviceId(preferences.getDssClientDeviceId()), statusCode, reason);
		this.webSocket = null;
		if (heartbeat != null) {
			heartbeat.cancel(true);
			heartbeat = null;
		}
		if (preferences.hasDssClientCredential()) {
			scheduleReconnect();
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		logger.warn("DSS client WebSocket error for device {}: {}",
				maskDeviceId(preferences.getDssClientDeviceId()), error.getMessage(), error);
		this.webSocket = null;
		if (preferences.hasDssClientCredential()) {
			scheduleReconnect();
		}
	}

	private void sendHello(WebSocket socket) {
		String timestamp = Instant.now().toString();
		String nonce = nonce();
		String deviceId = preferences.getDssClientDeviceId();
		Map<String, String> hello = new LinkedHashMap<>();
		hello.put("type", "hello");
		hello.put("deviceId", deviceId);
		hello.put("timestamp", timestamp);
		hello.put("nonce", nonce);
		hello.put("signature", hmacHex(preferences.getDssClientSecret(), deviceId + "|" + timestamp + "|" + nonce));
		logger.info("Sending DSS client hello for device {} at {}", maskDeviceId(deviceId), timestamp);
		socket.sendText(gson.toJson(hello), true).whenComplete((ignored, error) -> {
			if (error != null) {
				logger.warn("Unable to send DSS client hello for device {}: {}", maskDeviceId(deviceId), error.getMessage(), error);
			} else {
				logger.info("DSS client hello sent for device {}", maskDeviceId(deviceId));
			}
		});
	}

	private void startHeartbeat() {
		if (heartbeat != null) {
			heartbeat.cancel(true);
		}
		heartbeat = scheduler.scheduleAtFixedRate(() -> send(Map.of("type", "heartbeat")), 30, 30, TimeUnit.SECONDS);
		logger.debug("DSS client heartbeat scheduled.");
	}

	private void scheduleReconnect() {
		logger.info("Scheduling DSS client WebSocket reconnect in 30 seconds for device {}",
				maskDeviceId(preferences.getDssClientDeviceId()));
		scheduler.schedule(this::connect, 30, TimeUnit.SECONDS);
	}

	private void handleMessage(JsonObject message) {
		String type = getString(message, "type");
		logger.info("DSS client WebSocket message received: type={} correlationId={}", type, getString(message, "correlationId"));
		switch (type) {
		case "device_revoked":
			logger.warn("DSS client device has been revoked by esup-signature.");
			preferences.clearDssClientCredential();
			stop();
			showInformation("Association revoquee", "Ce client a ete revoque dans esup-signature. Il repasse en mode nouvelle installation.");
			break;
		case "certificate_request":
			handleCertificateRequest(message);
			break;
		case "sign_request":
			handleSignRequest(message);
			break;
		default:
			logger.warn("Unknown DSS client WebSocket message type {}", type);
			break;
		}
	}

	private void handleCertificateRequest(JsonObject message) {
		String correlationId = getString(message, "correlationId");
		lastDocumentContext = documentContext(message);
		uiDisplay.startDssClientSigningSession();
		try {
			logger.info("Processing certificate_request correlationId={}", correlationId);
			Execution<GetCertificateResponse> execution = api.getCertificate(new GetCertificateRequest());
			if (!execution.isSuccess()) {
				logger.warn("Certificate request failed with operation error: {}", execution.getErrorMessage());
				sendError(correlationId, execution.getError(), execution.getErrorMessage());
				finishWssUiFlow();
				return;
			}
			GetCertificateResponse certificate = execution.getResponse();
			lastCertificate = certificate;
			logger.info("Certificate selected for correlationId={}, tokenId={}, keyId={}, encryptionAlgorithm={}",
					correlationId,
					certificate.getTokenId() != null ? certificate.getTokenId().getId() : null,
					certificate.getKeyId(),
					certificate.getEncryptionAlgorithm());
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("type", "certificate_response");
			response.put("correlationId", correlationId);
			response.put("certificate", encodeCertificate(certificate.getCertificate()));
			response.put("certificateChain", encodeCertificateChain(certificate.getCertificateChain()));
			response.put("encryptionAlgorithm", certificate.getEncryptionAlgorithm().getName());
			send(response);
		} catch (Exception e) {
			logger.error("Certificate request failed", e);
			sendError(correlationId, "client.exception", e.getMessage());
			finishWssUiFlow();
		}
	}

	private void handleSignRequest(JsonObject message) {
		String correlationId = getString(message, "correlationId");
		DocumentContext documentContext = documentContext(message);
		try {
			logger.info("Processing sign_request correlationId={}, documentName={}, origin={}",
					correlationId, documentContext.documentName(), documentContext.origin());
			uiDisplay.setDssClientSigningStep(StandaloneUIDisplay.DssClientSigningStep.CONFIRMATION);
			if (!confirmSignature(documentContext.documentName(), documentContext.origin())) {
				logger.info("User cancelled sign_request correlationId={}", correlationId);
				send(Map.of("type", "user_cancel", "correlationId", correlationId));
				finishWssUiFlow();
				return;
			}
			if (lastCertificate == null) {
				throw new IllegalStateException("Aucun certificat n'a ete selectionne");
			}
			SignatureRequest request = new SignatureRequest();
			request.setTokenId(lastCertificate.getTokenId());
			request.setKeyId(lastCertificate.getKeyId());
			ToBeSigned toBeSigned = new ToBeSigned();
			toBeSigned.setBytes(Base64.getDecoder().decode(getString(message, "dataToSign")));
			request.setToBeSigned(toBeSigned);
			request.setDigestAlgorithm(DigestAlgorithm.forName(getString(message, "digestAlgo"), DigestAlgorithm.SHA256));

			uiDisplay.setDssClientSigningStep(StandaloneUIDisplay.DssClientSigningStep.SIGNATURE);
			Execution<SignatureResponse> execution = api.sign(request);
			if (!execution.isSuccess()) {
				logger.warn("Signature request failed with operation error: {}", execution.getErrorMessage());
				sendError(correlationId, execution.getError(), execution.getErrorMessage());
				finishWssUiFlow();
				return;
			}
			logger.info("Signature request completed correlationId={}, signatureLength={}",
					correlationId, execution.getResponse().getSignatureValue().length);
			send(Map.of(
					"type", "sign_response",
					"correlationId", correlationId,
					"signatureValue", Base64.getEncoder().encodeToString(execution.getResponse().getSignatureValue())
			));
			finishWssUiFlow();
		} catch (Exception e) {
			logger.error("Signature request failed", e);
			sendError(correlationId, "client.exception", e.getMessage());
			finishWssUiFlow();
		}
	}

	private void finishWssUiFlow() {
		uiDisplay.finishDssClientSigningSession();
	}

	private boolean confirmSignature(String documentName, String origin) throws InterruptedException {
		return uiDisplay.confirmDssClientSignature(documentName, origin);
	}

	private void showInformation(String title, String message) {
		promptWindow.showInformation(title, message);
	}

	private DocumentContext documentContext(JsonObject message) {
		String documentName = getString(message, "documentName");
		if (documentName == null) {
			documentName = getString(message, "documentTitle");
		}
		String origin = getString(message, "origin");
		if (lastDocumentContext != null) {
			if (documentName == null) {
				documentName = lastDocumentContext.documentName();
			}
			if (origin == null) {
				origin = lastDocumentContext.origin();
			}
		}
		return new DocumentContext(documentName, origin);
	}

	private void sendError(String correlationId, String errorCode, String errorMessage) {
		logger.warn("Sending DSS client error correlationId={}, code={}, message={}", correlationId, errorCode, errorMessage);
		Map<String, String> response = new LinkedHashMap<>();
		response.put("type", "error");
		if (correlationId != null) {
			response.put("correlationId", correlationId);
		}
		if (errorCode != null && !errorCode.isBlank()) {
			response.put("errorCode", errorCode);
		}
		response.put("errorMessage", errorMessage == null ? "Erreur Esup-DSS-Client" : errorMessage);
		send(response);
	}

	private void send(Object message) {
		WebSocket socket = webSocket;
		if (socket != null && !socket.isOutputClosed()) {
			String json = gson.toJson(message);
			logger.debug("DSS client WebSocket sending payload: {}", json);
			socket.sendText(json, true).whenComplete((ignored, error) -> {
				if (error != null) {
					logger.warn("Unable to send DSS client WebSocket message: {}", error.getMessage(), error);
				}
			});
		} else {
			logger.warn("Cannot send DSS client WebSocket message because socket is not open: {}", message);
		}
	}

	private String maskDeviceId(String deviceId) {
		if (deviceId == null || deviceId.length() < 8) {
			return String.valueOf(deviceId);
		}
		return deviceId.substring(0, 8) + "...";
	}

	private String encodeCertificate(CertificateToken certificate) {
		return Base64.getEncoder().encodeToString(certificate.getEncoded());
	}

	private String[] encodeCertificateChain(CertificateToken[] chain) {
		if (chain == null) {
			return new String[0];
		}
		String[] result = new String[chain.length];
		for (int i = 0; i < chain.length; i++) {
			result[i] = encodeCertificate(chain[i]);
		}
		return result;
	}

	private String nonce() {
		byte[] bytes = new byte[16];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hmacHex(String secret, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Impossible de calculer la signature HMAC", e);
		}
	}

	private String getString(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
	}

	private record DocumentContext(String documentName, String origin) {}
}
