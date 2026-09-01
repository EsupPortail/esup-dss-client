package org.esupportail.esupdssclient.dssclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.esupportail.esupdssclient.StandaloneUIDisplay;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DssClientWebSocketService implements WebSocket.Listener {

	private static final Logger logger = LoggerFactory.getLogger(DssClientWebSocketService.class);
	static final int MAX_MESSAGE_CHARS = 2 * 1024 * 1024;
	static final int MAX_BATCH_ITEMS = 100;
	static final int MAX_DATA_TO_SIGN_BYTES = 64 * 1024;
	static final int MAX_BATCH_BYTES = 1024 * 1024;
	private static final long SESSION_TIMEOUT_SECONDS = 300;
	private static final long MAX_RECONNECT_DELAY_SECONDS = 300;

	private final EsupDSSClientAPI api;
	private final DssClientAssociation association;
	private final StandaloneUIDisplay uiDisplay;
	private final DssClientSigningCoordinator signingCoordinator;
	private final Consumer<DssClientConnectionManager.ConnectionStatus> statusListener;
	private final Runnable revokedListener;
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
	private volatile ScheduledFuture<?> reconnect;
	private final StringBuilder incomingText = new StringBuilder();
	private volatile SigningSession activeSession;
	private volatile boolean stopped;
	private volatile boolean connecting;
	private volatile boolean pendingApproval;
	private volatile boolean authenticated;
	private volatile int reconnectAttempt;

	DssClientWebSocketService(EsupDSSClientAPI api, DssClientAssociation association, StandaloneUIDisplay uiDisplay,
			DssClientSigningCoordinator signingCoordinator,
			Consumer<DssClientConnectionManager.ConnectionStatus> statusListener, Runnable revokedListener) {
		this.api = api;
		this.association = association;
		this.uiDisplay = uiDisplay;
		this.signingCoordinator = signingCoordinator;
		this.statusListener = statusListener;
		this.revokedListener = revokedListener;
	}

	public synchronized void connect() {
		if (stopped) {
			return;
		}
		if (connecting || webSocket != null && !webSocket.isOutputClosed() && !webSocket.isInputClosed()) {
			return;
		}
		logger.info("Connecting to esup-signature DSS client WebSocket {} with device {}",
				association.getWebsocketUrl(), maskDeviceId(association.getDeviceId()));
		statusListener.accept(pendingApproval
				? DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL
				: DssClientConnectionManager.ConnectionStatus.CONNECTING);
		connecting = true;
		URI websocketUri = URI.create(association.getWebsocketUrl());
		if ("ws".equalsIgnoreCase(websocketUri.getScheme()) && !DssClientPairingService.isInsecureHttpAllowed()) {
			connecting = false;
			logger.error("Refusing insecure DSS client WebSocket {}. Use -D{}=true only for development.",
					association.getWebsocketUrl(), DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY);
			statusListener.accept(DssClientConnectionManager.ConnectionStatus.DISCONNECTED);
			return;
		}
		HttpClient.newHttpClient().newWebSocketBuilder()
				.header("Origin", originFor(websocketUri))
				.header("User-Agent", "Esup-DSS-Client/" + api.getAppConfig().getApplicationVersion())
				.buildAsync(websocketUri, this)
				.whenComplete((socket, error) -> {
					connecting = false;
					if (error != null) {
						logger.warn("Unable to connect DSS client WebSocket {} for device {}: {}",
								association.getWebsocketUrl(), maskDeviceId(association.getDeviceId()),
								error.getMessage(), error);
						statusListener.accept(DssClientConnectionManager.ConnectionStatus.DISCONNECTED);
						logHandshakeFailure(error);
						scheduleReconnect();
					} else if (stopped) {
						socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
					} else {
						logger.info("DSS client WebSocket build completed for device {}", maskDeviceId(association.getDeviceId()));
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
		stopped = true;
		connecting = false;
		if (heartbeat != null) {
			heartbeat.cancel(true);
			heartbeat = null;
		}
		if (reconnect != null) {
			reconnect.cancel(true);
			reconnect = null;
		}
		WebSocket socket = webSocket;
		webSocket = null;
		incomingText.setLength(0);
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
		}
		abortActiveSession();
		scheduler.shutdownNow();
		statusListener.accept(DssClientConnectionManager.ConnectionStatus.DISCONNECTED);
	}

	@Override
	public synchronized void onOpen(WebSocket webSocket) {
		if (stopped) {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
			return;
		}
		this.webSocket = webSocket;
		incomingText.setLength(0);
		authenticated = false;
		logger.info("DSS client WebSocket opened for device {}", maskDeviceId(association.getDeviceId()));
		statusListener.accept(pendingApproval
				? DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL
				: DssClientConnectionManager.ConnectionStatus.CONNECTING);
		sendHello(webSocket);
		startHeartbeat();
		WebSocket.Listener.super.onOpen(webSocket);
	}

	@Override
	public synchronized CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		if (this.webSocket != webSocket) {
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}
		try {
			incomingText.append(data);
			if (incomingText.length() > MAX_MESSAGE_CHARS) {
				incomingText.setLength(0);
				webSocket.sendClose(1009, "message too large");
				return CompletableFuture.completedFuture(null);
			}
			if (last) {
				String payload = incomingText.toString();
				incomingText.setLength(0);
				logger.debug("DSS client WebSocket received payload: {}", payload);
				JsonObject message = JsonParser.parseString(payload).getAsJsonObject();
				handleMessage(message);
			}
		} catch (Exception e) {
			logger.error("Unable to process DSS client WebSocket message", e);
			sendError(null, "client.exception", e.getMessage());
		} finally {
			webSocket.request(1);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public synchronized CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		logger.warn("DSS client WebSocket closed for device {}: status={} reason={}",
				maskDeviceId(association.getDeviceId()), statusCode, reason);
		if (this.webSocket != webSocket) {
			return CompletableFuture.completedFuture(null);
		}
		this.webSocket = null;
		incomingText.setLength(0);
		authenticated = false;
		statusListener.accept(pendingApproval
				? DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL
				: DssClientConnectionManager.ConnectionStatus.DISCONNECTED);
		abortActiveSession();
		if (heartbeat != null) {
			heartbeat.cancel(true);
			heartbeat = null;
		}
		if (!stopped) {
			scheduleReconnect();
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public synchronized void onError(WebSocket webSocket, Throwable error) {
		logger.warn("DSS client WebSocket error for device {}: {}",
				maskDeviceId(association.getDeviceId()), error.getMessage(), error);
		if (this.webSocket != webSocket) {
			return;
		}
		this.webSocket = null;
		incomingText.setLength(0);
		authenticated = false;
		statusListener.accept(pendingApproval
				? DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL
				: DssClientConnectionManager.ConnectionStatus.DISCONNECTED);
		abortActiveSession();
		if (heartbeat != null) {
			heartbeat.cancel(true);
			heartbeat = null;
		}
		if (!stopped) {
			scheduleReconnect();
		}
	}

	private void sendHello(WebSocket socket) {
		String timestamp = Instant.now().toString();
		String nonce = nonce();
		String deviceId = association.getDeviceId();
		Map<String, String> hello = new LinkedHashMap<>();
		hello.put("type", "hello");
		hello.put("deviceId", deviceId);
		hello.put("timestamp", timestamp);
		hello.put("nonce", nonce);
		hello.put("signature", hmacHex(association.getSecret(), deviceId + "|" + timestamp + "|" + nonce));
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

	private synchronized void scheduleReconnect() {
		if (stopped || scheduler.isShutdown() || reconnect != null && !reconnect.isDone()) {
			return;
		}
		long delaySeconds = nextReconnectDelaySeconds(reconnectAttempt++, pendingApproval,
				ThreadLocalRandom.current().nextDouble(0.5, 1.5));
		logger.info("Scheduling DSS client WebSocket reconnect in {} seconds for device {}",
				delaySeconds, maskDeviceId(association.getDeviceId()));
		reconnect = scheduler.schedule(() -> {
			reconnect = null;
			connect();
		}, delaySeconds, TimeUnit.SECONDS);
	}

	static long nextReconnectDelaySeconds(int attempt, boolean pendingApproval, double jitter) {
		long base = pendingApproval ? 10 : Math.min(MAX_RECONNECT_DELAY_SECONDS,
				2L << Math.min(Math.max(attempt, 0), 7));
		return Math.max(1, Math.min(MAX_RECONNECT_DELAY_SECONDS, Math.round(base * jitter)));
	}

	private void handleMessage(JsonObject message) {
		String type = getString(message, "type");
		logger.info("DSS client WebSocket message received: type={} correlationId={}", type, getString(message, "correlationId"));
		switch (type) {
		case "device_revoked":
			logger.warn("DSS client device has been revoked by esup-signature: {}.", association.getAssociatedUrl());
			revokedListener.run();
			showInformation("Association révoquée", "L'association avec " + association.getAssociatedUrl()
					+ " a été révoquée dans esup-signature.");
			break;
		case "authentication_failed":
			pendingApproval = false;
			logger.info("DSS client authentication refused for {} (association pending or credentials invalid).",
					association.getAssociatedUrl());
			break;
		case "association_pending":
			pendingApproval = true;
			statusListener.accept(DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL);
			logger.info("DSS client association is waiting for approval on {}.", association.getAssociatedUrl());
			break;
		case "authenticated":
			pendingApproval = false;
			authenticated = true;
			reconnectAttempt = 0;
			statusListener.accept(DssClientConnectionManager.ConnectionStatus.CONNECTED);
			logger.info("DSS client authenticated on {}.", association.getAssociatedUrl());
			break;
		case "cancel_session":
			if (!requireAuthenticated(message)) {
				break;
			}
			handleCancelSession(message);
			break;
		case "certificate_request":
			if (!requireAuthenticated(message)) {
				break;
			}
			handleCertificateRequest(message);
			break;
		case "sign_request":
			if (!requireAuthenticated(message)) {
				break;
			}
			handleSignRequest(message);
			break;
		default:
			logger.warn("Unknown DSS client WebSocket message type {}", type);
			break;
		}
	}

	private boolean requireAuthenticated(JsonObject message) {
		if (authenticated) {
			return true;
		}
		String correlationId = getString(message, "correlationId");
		logger.warn("Rejecting DSS client request before server authentication: type={} correlationId={}",
				getString(message, "type"), correlationId);
		sendError(correlationId, "client.not_authenticated", "Le serveur esup-signature n'est pas authentifié");
		return false;
	}

	private void handleCertificateRequest(JsonObject message) {
		String correlationId = getString(message, "correlationId");
		String sessionId = getString(message, "sessionId");
		if (sessionId == null || sessionId.isBlank()) {
			sendError(correlationId, "client.invalid_session", "sessionId est obligatoire");
			return;
		}
		if (activeSession != null) {
			sendError(correlationId, "client.busy", "Une autre demande de signature est déjà en cours", null, sessionId);
			return;
		}
		if (!signingCoordinator.acquire(this)) {
			sendError(correlationId, "client.busy", "Une autre demande de signature est déjà en cours", null, sessionId);
			return;
		}
		SigningSession session = new SigningSession(sessionId, correlationId, documentContext(message));
		activeSession = session;
		session.timeout = scheduler.schedule(() -> expireSession(session), SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		uiDisplay.startDssClientSigningSession();
		try {
			logger.info("Processing certificate_request correlationId={}", correlationId);
			Execution<GetCertificateResponse> execution = api.getCertificate(new GetCertificateRequest());
			if (!execution.isSuccess()) {
				logger.warn("Certificate request failed with operation error: {}", execution.getErrorMessage());
				sendError(correlationId, execution.getError(), execution.getErrorMessage(), null, sessionId);
				finishWssUiFlow();
				return;
			}
			GetCertificateResponse certificate = execution.getResponse();
			session.certificate = certificate;
			logger.info("Certificate selected for correlationId={}, tokenId={}, keyId={}, encryptionAlgorithm={}",
					correlationId,
					certificate.getTokenId() != null ? certificate.getTokenId().getId() : null,
					certificate.getKeyId(),
					certificate.getEncryptionAlgorithm());
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("type", "certificate_response");
			response.put("sessionId", sessionId);
			response.put("correlationId", correlationId);
			response.put("certificate", encodeCertificate(certificate.getCertificate()));
			response.put("certificateChain", encodeCertificateChain(certificate.getCertificateChain()));
			response.put("encryptionAlgorithm", certificate.getEncryptionAlgorithm().getName());
			send(response);
		} catch (Exception e) {
			logger.error("Certificate request failed", e);
			sendError(correlationId, "client.exception", e.getMessage(), null, sessionId);
			finishWssUiFlow();
		}
	}

	void handleSignRequest(JsonObject message) {
		String correlationId = getString(message, "correlationId");
		String sessionId = getString(message, "sessionId");
		SigningSession session = activeSession;
		if (sessionId == null || session == null || !session.sessionId.equals(sessionId)) {
			sendError(correlationId, "client.invalid_session", "La session de signature est inconnue", null, sessionId);
			return;
		}
		session.correlationId = correlationId;
		DocumentContext documentContext = session.documentContext;
		try {
			boolean batchRequest = message.has("dataToSign") && message.get("dataToSign").isJsonArray();
			List<ToBeSigned> valuesToSign = parseDataToSign(message);
			logger.info("Processing sign_request correlationId={}, documentName={}, origin={}, signatureCount={}",
					correlationId, documentContext.documentName(), documentContext.origin(), valuesToSign.size());
			uiDisplay.setDssClientSigningStep(StandaloneUIDisplay.DssClientSigningStep.CONFIRMATION);
			if (!confirmSignature(documentContext.documentName(), documentContext.origin())) {
				logger.info("User cancelled sign_request correlationId={}", correlationId);
				send(Map.of("type", "user_cancel", "sessionId", sessionId, "correlationId", correlationId));
				finishWssUiFlow();
				return;
			}
			if (session.certificate == null) {
				throw new IllegalStateException("Aucun certificat n'a ete selectionne");
			}
			uiDisplay.setDssClientSigningStep(StandaloneUIDisplay.DssClientSigningStep.SIGNATURE);
			DigestAlgorithm digestAlgorithm = DigestAlgorithm.forName(getString(message, "digestAlgo"), DigestAlgorithm.SHA256);
			if (batchRequest) {
				SignatureBatchRequest request = new SignatureBatchRequest();
				request.setTokenId(session.certificate.getTokenId());
				request.setKeyId(session.certificate.getKeyId());
				request.setValuesToSign(valuesToSign);
				request.setDigestAlgorithm(digestAlgorithm);
				request.setProgressListener((completed, total) -> send(Map.of(
						"type", "sign_progress",
						"sessionId", sessionId,
						"correlationId", correlationId,
						"completed", completed,
						"total", total
				)));

				Execution<SignatureBatchResponse> execution = api.signBatch(request);
				if (!execution.isSuccess()) {
					logger.warn("Batch signature request failed at index={} with operation error: {}",
							execution.getFailedIndex(), execution.getErrorMessage());
					sendError(correlationId, execution.getError(), execution.getErrorMessage(), execution.getFailedIndex(), sessionId);
					finishWssUiFlow();
					return;
				}
				List<String> signatureValues = execution.getResponse().getSignatureValues().stream()
						.map(value -> Base64.getEncoder().encodeToString(value.getValue()))
						.toList();
				logger.info("Batch signature request completed correlationId={}, signatureCount={}",
						correlationId, signatureValues.size());
				send(Map.of(
						"type", "sign_response",
						"sessionId", sessionId,
						"correlationId", correlationId,
						"signatureValues", signatureValues
				));
			} else {
				SignatureRequest request = new SignatureRequest();
				request.setTokenId(session.certificate.getTokenId());
				request.setKeyId(session.certificate.getKeyId());
				request.setToBeSigned(valuesToSign.get(0));
				request.setDigestAlgorithm(digestAlgorithm);

				Execution<SignatureResponse> execution = api.sign(request);
				if (!execution.isSuccess()) {
					logger.warn("Signature request failed with operation error: {}", execution.getErrorMessage());
					sendError(correlationId, execution.getError(), execution.getErrorMessage(), null, sessionId);
					finishWssUiFlow();
					return;
				}
				String signatureValue = Base64.getEncoder().encodeToString(execution.getResponse().getSignatureValue());
				logger.info("Signature request completed correlationId={}, signatureLength={}",
						correlationId, execution.getResponse().getSignatureValue().length);
				send(Map.of(
						"type", "sign_response",
						"sessionId", sessionId,
						"correlationId", correlationId,
						"signatureValue", signatureValue
				));
			}
			finishWssUiFlow();
		} catch (Exception e) {
			logger.error("Signature request failed", e);
			sendError(correlationId, "client.exception", e.getMessage(), null, sessionId);
			finishWssUiFlow();
		}
	}

	private synchronized void finishWssUiFlow() {
		SigningSession session = activeSession;
		activeSession = null;
		if (session != null && session.timeout != null) {
			session.timeout.cancel(false);
		}
		uiDisplay.finishDssClientSigningSession();
		signingCoordinator.release(this);
	}

	private synchronized void abortActiveSession() {
		if (activeSession != null) {
			finishWssUiFlow();
		} else if (signingCoordinator.release(this)) {
			uiDisplay.finishDssClientSigningSession();
		}
	}

	private void expireSession(SigningSession expected) {
		synchronized (this) {
			if (activeSession != expected) {
				return;
			}
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("type", "session_expired");
		response.put("sessionId", expected.sessionId);
		if (expected.correlationId != null) {
			response.put("correlationId", expected.correlationId);
		}
		send(response);
		finishWssUiFlow();
	}

	private void handleCancelSession(JsonObject message) {
		String sessionId = getString(message, "sessionId");
		SigningSession session = activeSession;
		if (session != null && session.sessionId.equals(sessionId)) {
			finishWssUiFlow();
		}
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
		SigningSession session = activeSession;
		if (session != null && session.documentContext != null) {
			if (documentName == null) {
				documentName = session.documentContext.documentName();
			}
		}
		// The provenance shown to the user is the locally stored association.
		// A network message must not be able to replace this trusted display value.
		return new DocumentContext(documentName, association.getAssociatedUrl());
	}

	private void sendError(String correlationId, String errorCode, String errorMessage) {
		sendError(correlationId, errorCode, errorMessage, null);
	}

	private void sendError(String correlationId, String errorCode, String errorMessage, Integer failedIndex) {
		sendError(correlationId, errorCode, errorMessage, failedIndex, null);
	}

	private void sendError(String correlationId, String errorCode, String errorMessage, Integer failedIndex, String sessionId) {
		logger.warn("Sending DSS client error correlationId={}, code={}, message={}", correlationId, errorCode, errorMessage);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("type", "error");
		if (sessionId != null) {
			response.put("sessionId", sessionId);
		}
		if (correlationId != null) {
			response.put("correlationId", correlationId);
		}
		if (errorCode != null && !errorCode.isBlank()) {
			response.put("errorCode", errorCode);
		}
		if (failedIndex != null) {
			response.put("failedIndex", failedIndex);
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

	static List<ToBeSigned> parseDataToSign(JsonObject message) {
		JsonElement dataToSign = message.get("dataToSign");
		if (dataToSign == null || dataToSign.isJsonNull()) {
			throw new IllegalArgumentException("dataToSign est obligatoire");
		}

		List<JsonElement> encodedValues = new ArrayList<>();
		if (dataToSign.isJsonArray()) {
			dataToSign.getAsJsonArray().forEach(encodedValues::add);
		} else {
			encodedValues.add(dataToSign);
		}
		if (encodedValues.isEmpty()) {
			throw new IllegalArgumentException("dataToSign ne peut pas etre une liste vide");
		}
		if (encodedValues.size() > MAX_BATCH_ITEMS) {
			throw new IllegalArgumentException("Le lot dépasse la limite de " + MAX_BATCH_ITEMS + " signatures");
		}

		List<ToBeSigned> valuesToSign = new ArrayList<>(encodedValues.size());
		long batchBytes = 0;
		for (JsonElement encodedValue : encodedValues) {
			if (encodedValue == null || !encodedValue.isJsonPrimitive() || !encodedValue.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("Chaque dataToSign doit etre une chaine Base64");
			}
			byte[] decoded = Base64.getDecoder().decode(encodedValue.getAsString());
			if (decoded.length > MAX_DATA_TO_SIGN_BYTES) {
				throw new IllegalArgumentException("Une donnée à signer dépasse la limite de " + MAX_DATA_TO_SIGN_BYTES + " octets");
			}
			batchBytes += decoded.length;
			if (batchBytes > MAX_BATCH_BYTES) {
				throw new IllegalArgumentException("Le lot dépasse la limite totale de " + MAX_BATCH_BYTES + " octets");
			}
			ToBeSigned toBeSigned = new ToBeSigned();
			toBeSigned.setBytes(decoded);
			valuesToSign.add(toBeSigned);
		}
		return valuesToSign;
	}

	private record DocumentContext(String documentName, String origin) {}

	private static final class SigningSession {
		private final String sessionId;
		private final DocumentContext documentContext;
		private volatile String correlationId;
		private volatile GetCertificateResponse certificate;
		private volatile ScheduledFuture<?> timeout;

		private SigningSession(String sessionId, String correlationId, DocumentContext documentContext) {
			this.sessionId = sessionId;
			this.correlationId = correlationId;
			this.documentContext = documentContext;
		}
	}
}
