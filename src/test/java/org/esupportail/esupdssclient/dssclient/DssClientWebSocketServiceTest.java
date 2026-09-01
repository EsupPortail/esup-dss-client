package org.esupportail.esupdssclient.dssclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.esupportail.esupdssclient.StandaloneUIDisplay;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class DssClientWebSocketServiceTest {

	@Test
	public void parsesSingleDataToSign() {
		JsonObject message = JsonParser.parseString("{\"dataToSign\":\"cHJlbWllcg==\"}").getAsJsonObject();

		List<ToBeSigned> values = DssClientWebSocketService.parseDataToSign(message);

		assertEquals(1, values.size());
		assertArrayEquals("premier".getBytes(StandardCharsets.UTF_8), values.get(0).getBytes());
	}

	@Test
	public void parsesDataToSignListInOrder() {
		JsonObject message = JsonParser.parseString(
				"{\"dataToSign\":[\"cHJlbWllcg==\",\"ZGV1eGllbWU=\"]}").getAsJsonObject();

		List<ToBeSigned> values = DssClientWebSocketService.parseDataToSign(message);

		assertEquals(2, values.size());
		assertArrayEquals("premier".getBytes(StandardCharsets.UTF_8), values.get(0).getBytes());
		assertArrayEquals("deuxieme".getBytes(StandardCharsets.UTF_8), values.get(1).getBytes());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsEmptyDataToSignList() {
		JsonObject message = JsonParser.parseString("{\"dataToSign\":[]}").getAsJsonObject();

		DssClientWebSocketService.parseDataToSign(message);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonStringDataToSignItem() {
		JsonObject message = JsonParser.parseString("{\"dataToSign\":[123]}").getAsJsonObject();

		DssClientWebSocketService.parseDataToSign(message);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTooManyBatchItems() {
		JsonArray items = new JsonArray();
		for (int i = 0; i <= DssClientWebSocketService.MAX_BATCH_ITEMS; i++) {
			items.add("dW4=");
		}
		JsonObject message = new JsonObject();
		message.add("dataToSign", items);
		DssClientWebSocketService.parseDataToSign(message);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOversizedDataToSign() {
		byte[] bytes = new byte[DssClientWebSocketService.MAX_DATA_TO_SIGN_BYTES + 1];
		JsonObject message = new JsonObject();
		message.addProperty("dataToSign", java.util.Base64.getEncoder().encodeToString(bytes));
		DssClientWebSocketService.parseDataToSign(message);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOversizedBatch() {
		JsonArray items = new JsonArray();
		byte[] bytes = new byte[DssClientWebSocketService.MAX_DATA_TO_SIGN_BYTES];
		String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
		for (int i = 0; i <= DssClientWebSocketService.MAX_BATCH_BYTES / bytes.length; i++) {
			items.add(encoded);
		}
		JsonObject message = new JsonObject();
		message.add("dataToSign", items);
		DssClientWebSocketService.parseDataToSign(message);
	}

	@Test
	public void reassemblesTextFragments() throws Exception {
		WebSocketContext context = contextWithoutSession(new DssClientSigningCoordinator(), "fragmented");
		setField(context.service, "authenticated", false);
		context.statuses.clear();

		context.service.onText(context.webSocket, "{\"type\":\"auth", false);
		assertFalse(context.statuses.contains(DssClientConnectionManager.ConnectionStatus.CONNECTED));

		context.service.onText(context.webSocket, "enticated\"}", true);
		assertTrue(context.statuses.contains(DssClientConnectionManager.ConnectionStatus.CONNECTED));
	}

	@Test
	public void staleSocketCannotCloseCurrentSocket() throws Exception {
		WebSocketContext context = contextWithoutSession(new DssClientSigningCoordinator(), "current");
		WebSocket replacement = mock(WebSocket.class);
		setField(context.service, "webSocket", replacement);

		context.service.onClose(context.webSocket, 1000, "old socket");

		Field field = DssClientWebSocketService.class.getDeclaredField("webSocket");
		field.setAccessible(true);
		assertSame(replacement, field.get(context.service));
	}

	@Test
	public void reconnectBackoffIsBounded() {
		assertEquals(2, DssClientWebSocketService.nextReconnectDelaySeconds(0, false, 1.0));
		assertEquals(32, DssClientWebSocketService.nextReconnectDelaySeconds(4, false, 1.0));
		assertEquals(300, DssClientWebSocketService.nextReconnectDelaySeconds(20, false, 1.5));
		assertEquals(10, DssClientWebSocketService.nextReconnectDelaySeconds(20, true, 1.0));
	}

	@Test
	public void rejectsSignRequestForAnotherSession() throws Exception {
		WebSocketContext context = context();

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"sessionId\":\"other\",\"correlationId\":\"wrong\","
						+ "\"dataToSign\":\"dW4=\",\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		assertEquals("client.invalid_session", lastSentMessage(context.webSocket).get("errorCode").getAsString());
		verify(context.api, never()).sign(any(SignatureRequest.class));
	}

	@Test
	public void arrayUsesOneBatchApiCallAndReturnsSignatureValuesInOrder() throws Exception {
		WebSocketContext context = context();
		SignatureValue first = new SignatureValue(SignatureAlgorithm.RSA_SHA256, "sig-un".getBytes(StandardCharsets.UTF_8));
		SignatureValue second = new SignatureValue(SignatureAlgorithm.RSA_SHA256, "sig-deux".getBytes(StandardCharsets.UTF_8));
		when(context.api.signBatch(any(SignatureBatchRequest.class))).thenAnswer(invocation -> {
			SignatureBatchRequest request = invocation.getArgument(0);
			request.notifyProgress(1, 2);
			request.notifyProgress(2, 2);
			return new Execution<>(new SignatureBatchResponse(List.of(first, second), null, null));
		});

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"sessionId\":\"session-1\",\"correlationId\":\"batch-1\","
						+ "\"dataToSign\":[\"dW4=\",\"ZGV1eA==\"],\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		verify(context.api, times(1)).signBatch(any(SignatureBatchRequest.class));
		verify(context.api, never()).sign(any(SignatureRequest.class));
		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("sign_response", response.get("type").getAsString());
		assertEquals("c2lnLXVu", response.getAsJsonArray("signatureValues").get(0).getAsString());
		assertEquals("c2lnLWRldXg=", response.getAsJsonArray("signatureValues").get(1).getAsString());
		org.mockito.ArgumentCaptor<CharSequence> payload = org.mockito.ArgumentCaptor.forClass(CharSequence.class);
		verify(context.webSocket, times(3)).sendText(payload.capture(), anyBoolean());
		JsonObject firstProgress = JsonParser.parseString(payload.getAllValues().get(0).toString()).getAsJsonObject();
		JsonObject secondProgress = JsonParser.parseString(payload.getAllValues().get(1).toString()).getAsJsonObject();
		assertEquals("sign_progress", firstProgress.get("type").getAsString());
		assertEquals(1, firstProgress.get("completed").getAsInt());
		assertEquals(2, secondProgress.get("completed").getAsInt());
	}

	@Test
	public void scalarKeepsHistoricalApiAndResponseFormat() throws Exception {
		WebSocketContext context = context();
		SignatureValue signature = new SignatureValue(SignatureAlgorithm.RSA_SHA256,
				"scalar-signature".getBytes(StandardCharsets.UTF_8));
		when(context.api.sign(any(SignatureRequest.class))).thenReturn(
				new Execution<>(new SignatureResponse(signature, null, null)));

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"sessionId\":\"session-1\",\"correlationId\":\"scalar-1\","
						+ "\"dataToSign\":\"dW4=\",\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		verify(context.api, times(1)).sign(any(SignatureRequest.class));
		verify(context.api, never()).signBatch(any(SignatureBatchRequest.class));
		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("sign_response", response.get("type").getAsString());
		assertEquals("c2NhbGFyLXNpZ25hdHVyZQ==", response.get("signatureValue").getAsString());
		assertFalse(response.has("signatureValues"));
		verify(context.uiDisplay).confirmDssClientSignature(null, "https://signature.example.org");
	}

	@Test
	public void ignoresMessageOriginWhenConfirmingSignature() throws Exception {
		WebSocketContext context = context();
		SignatureValue signature = new SignatureValue(SignatureAlgorithm.RSA_SHA256,
				"signature".getBytes(StandardCharsets.UTF_8));
		when(context.api.sign(any(SignatureRequest.class))).thenReturn(
				new Execution<>(new SignatureResponse(signature, null, null)));

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"sessionId\":\"session-1\",\"correlationId\":\"origin\","
						+ "\"origin\":\"https://attacker.example\",\"dataToSign\":\"dW4=\",\"digestAlgo\":\"SHA256\"}")
				.getAsJsonObject());

		verify(context.uiDisplay).confirmDssClientSignature(null, "https://signature.example.org");
	}

	@Test
	public void connectionStatusRequiresServerAuthenticationAcknowledgement() throws Exception {
		WebSocketContext context = context();

		context.service.onText(context.webSocket, "{\"type\":\"association_pending\"}", true);
		assertEquals(DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL,
				context.statuses.get(context.statuses.size() - 1));

		context.service.onText(context.webSocket, "{\"type\":\"authenticated\"}", true);
		assertEquals(DssClientConnectionManager.ConnectionStatus.CONNECTED,
				context.statuses.get(context.statuses.size() - 1));
	}

	@Test
	public void rejectsSignRequestBeforeServerAuthentication() throws Exception {
		WebSocketContext context = context();
		setField(context.service, "authenticated", false);

		context.service.onText(context.webSocket,
				"{\"type\":\"sign_request\",\"correlationId\":\"unauthenticated\","
						+ "\"dataToSign\":\"dW4=\",\"digestAlgo\":\"SHA256\"}", true);

		verify(context.api, never()).sign(any(SignatureRequest.class));
		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("error", response.get("type").getAsString());
		assertEquals("client.not_authenticated", response.get("errorCode").getAsString());
	}

	@Test
	public void rejectsARequestWhileAnotherAssociationIsSigning() throws Exception {
		DssClientSigningCoordinator coordinator = new DssClientSigningCoordinator();
		WebSocketContext first = context(coordinator, "first");
		WebSocketContext second = contextWithoutSession(coordinator, "second");

		second.service.onText(second.webSocket,
				"{\"type\":\"certificate_request\",\"sessionId\":\"session-2\",\"correlationId\":\"busy\"}", true);

		JsonObject response = lastSentMessage(second.webSocket);
		assertEquals("error", response.get("type").getAsString());
		assertEquals("client.busy", response.get("errorCode").getAsString());
		verify(second.api, never()).sign(any(SignatureRequest.class));
		coordinator.release(first.service);
	}

	@Test
	public void batchErrorReturnsFailedIndex() throws Exception {
		WebSocketContext context = context();
		Execution<SignatureBatchResponse> failure = new Execution<>(BasicOperationStatus.USER_CANCEL);
		failure.setFailedIndex(1);
		when(context.api.signBatch(any(SignatureBatchRequest.class))).thenReturn(failure);

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"sessionId\":\"session-1\",\"correlationId\":\"batch-error\","
						+ "\"dataToSign\":[\"dW4=\",\"ZGV1eA==\"],\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("error", response.get("type").getAsString());
		assertEquals(1, response.get("failedIndex").getAsInt());
	}

	private WebSocketContext context() throws Exception {
		return context(new DssClientSigningCoordinator(), "device-id");
	}

	private WebSocketContext context(DssClientSigningCoordinator coordinator, String deviceId) throws Exception {
		WebSocketContext context = contextWithoutSession(coordinator, deviceId);
		GetCertificateResponse certificate = new GetCertificateResponse();
		certificate.setTokenId(new TokenId("token"));
		certificate.setKeyId("key");
		CertificateToken certificateToken = mock(CertificateToken.class);
		when(certificateToken.getEncoded()).thenReturn("certificate".getBytes(StandardCharsets.UTF_8));
		certificate.setCertificate(certificateToken);
		certificate.setCertificateChain(new CertificateToken[0]);
		certificate.setEncryptionAlgorithm(EncryptionAlgorithm.RSA);
		when(context.api.getCertificate(any(GetCertificateRequest.class))).thenReturn(new Execution<>(certificate));
		context.service.onText(context.webSocket,
				"{\"type\":\"certificate_request\",\"sessionId\":\"session-1\",\"correlationId\":\"certificate-1\"}", true);
		clearInvocations(context.webSocket, context.api, context.uiDisplay);
		return context;
	}

	private WebSocketContext contextWithoutSession(DssClientSigningCoordinator coordinator, String deviceId) throws Exception {
		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);
		StandaloneUIDisplay uiDisplay = mock(StandaloneUIDisplay.class);
		when(uiDisplay.confirmDssClientSignature(any(), any())).thenReturn(true);
		DssClientAssociation association = new DssClientAssociation("https://signature.example.org", deviceId,
				"secret", "wss://signature.example.org/ws");
		List<DssClientConnectionManager.ConnectionStatus> statuses = new ArrayList<>();
		DssClientWebSocketService service = new DssClientWebSocketService(api, association, uiDisplay,
				coordinator, statuses::add, () -> {});
		WebSocket webSocket = mock(WebSocket.class);
		when(webSocket.isOutputClosed()).thenReturn(false);
		when(webSocket.sendText(any(CharSequence.class), anyBoolean()))
				.thenReturn(CompletableFuture.completedFuture(webSocket));
		setField(service, "webSocket", webSocket);
		setField(service, "authenticated", true);
		return new WebSocketContext(service, api, uiDisplay, webSocket, statuses);
	}

	private JsonObject lastSentMessage(WebSocket webSocket) {
		org.mockito.ArgumentCaptor<CharSequence> payload = org.mockito.ArgumentCaptor.forClass(CharSequence.class);
		verify(webSocket, atLeastOnce()).sendText(payload.capture(), anyBoolean());
		return JsonParser.parseString(payload.getValue().toString()).getAsJsonObject();
	}

	private void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private record WebSocketContext(DssClientWebSocketService service, EsupDSSClientAPI api,
			StandaloneUIDisplay uiDisplay,
			WebSocket webSocket, List<DssClientConnectionManager.ConnectionStatus> statuses) {}
}
