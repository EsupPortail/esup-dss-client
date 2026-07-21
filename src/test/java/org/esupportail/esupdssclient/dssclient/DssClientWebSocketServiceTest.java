package org.esupportail.esupdssclient.dssclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import org.esupportail.esupdssclient.StandaloneUIDisplay;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
				"{\"type\":\"sign_request\",\"correlationId\":\"batch-1\","
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
				"{\"type\":\"sign_request\",\"correlationId\":\"scalar-1\","
						+ "\"dataToSign\":\"dW4=\",\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		verify(context.api, times(1)).sign(any(SignatureRequest.class));
		verify(context.api, never()).signBatch(any(SignatureBatchRequest.class));
		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("sign_response", response.get("type").getAsString());
		assertEquals("c2NhbGFyLXNpZ25hdHVyZQ==", response.get("signatureValue").getAsString());
		assertFalse(response.has("signatureValues"));
	}

	@Test
	public void batchErrorReturnsFailedIndex() throws Exception {
		WebSocketContext context = context();
		Execution<SignatureBatchResponse> failure = new Execution<>(BasicOperationStatus.USER_CANCEL);
		failure.setFailedIndex(1);
		when(context.api.signBatch(any(SignatureBatchRequest.class))).thenReturn(failure);

		context.service.handleSignRequest(JsonParser.parseString(
				"{\"type\":\"sign_request\",\"correlationId\":\"batch-error\","
						+ "\"dataToSign\":[\"dW4=\",\"ZGV1eA==\"],\"digestAlgo\":\"SHA256\"}").getAsJsonObject());

		JsonObject response = lastSentMessage(context.webSocket);
		assertEquals("error", response.get("type").getAsString());
		assertEquals(1, response.get("failedIndex").getAsInt());
	}

	private WebSocketContext context() throws Exception {
		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);
		StandaloneUIDisplay uiDisplay = mock(StandaloneUIDisplay.class);
		when(uiDisplay.confirmDssClientSignature(any(), any())).thenReturn(true);
		DssClientWebSocketService service = new DssClientWebSocketService(api, mock(UserPreferences.class), uiDisplay);
		GetCertificateResponse certificate = new GetCertificateResponse();
		certificate.setTokenId(new TokenId("token"));
		certificate.setKeyId("key");
		setField(service, "lastCertificate", certificate);
		WebSocket webSocket = mock(WebSocket.class);
		when(webSocket.isOutputClosed()).thenReturn(false);
		when(webSocket.sendText(any(CharSequence.class), anyBoolean()))
				.thenReturn(CompletableFuture.completedFuture(webSocket));
		setField(service, "webSocket", webSocket);
		return new WebSocketContext(service, api, webSocket);
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
			WebSocket webSocket) {}
}
