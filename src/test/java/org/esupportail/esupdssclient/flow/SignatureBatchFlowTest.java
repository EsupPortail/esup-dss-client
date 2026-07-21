package org.esupportail.esupdssclient.flow;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.esupportail.esupdssclient.CancelledOperationException;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.Operation;
import org.esupportail.esupdssclient.flow.operation.BasicOperationFactory;
import org.esupportail.esupdssclient.flow.operation.GetTokenConnectionOperation;
import org.esupportail.esupdssclient.flow.operation.SelectPrivateKeyOperation;
import org.esupportail.esupdssclient.flow.operation.SignOperation;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignatureBatchFlowTest {

	@Test
	public void signsInOrderWithOneConnectionAndOneKeySelection() throws Exception {
		CountingToken token = new CountingToken(testKey());
		TestContext context = context(token);
		SignatureBatchRequest request = request("un", "deux", "trois");
		List<String> progress = new ArrayList<>();
		request.setProgressListener((completed, total) -> progress.add(completed + "/" + total));

		Execution<SignatureBatchResponse> execution = context.flow.process(context.api, request);

		assertTrue(execution.isSuccess());
		assertEquals(3, execution.getResponse().getSignatureValues().size());
		assertArrayEquals(bytes("un"), execution.getResponse().getSignatureValues().get(0).getValue());
		assertArrayEquals(bytes("deux"), execution.getResponse().getSignatureValues().get(1).getValue());
		assertArrayEquals(bytes("trois"), execution.getResponse().getSignatureValues().get(2).getValue());
		assertEquals(1, context.operationFactory.count(GetTokenConnectionOperation.class));
		assertEquals(1, context.operationFactory.count(SelectPrivateKeyOperation.class));
		assertEquals(3, context.operationFactory.count(SignOperation.class));
		assertEquals(1, token.getKeysCalls);
		assertEquals(1, token.closeCalls);
		assertEquals(List.of("1/3", "2/3", "3/3"), progress);
	}

	@Test
	public void closesOnceAndReportsIndexOnCancellation() throws Exception {
		CountingToken token = new CountingToken(testKey());
		token.cancelIndex = 1;
		TestContext context = context(token);

		Execution<SignatureBatchResponse> execution = context.flow.process(context.api, request("un", "deux", "trois"));

		assertFalse(execution.isSuccess());
		assertEquals(BasicOperationStatus.USER_CANCEL.getCode(), execution.getError());
		assertEquals(Integer.valueOf(1), execution.getFailedIndex());
		assertEquals(2, token.signCalls);
		assertEquals(1, token.closeCalls);
	}

	@Test
	public void closesOnceAndReportsIndexOnError() throws Exception {
		CountingToken token = new CountingToken(testKey());
		token.errorIndex = 1;
		TestContext context = context(token);

		Execution<SignatureBatchResponse> execution = context.flow.process(context.api, request("un", "deux", "trois"));

		assertFalse(execution.isSuccess());
		assertEquals(BasicOperationStatus.EXCEPTION.getCode(), execution.getError());
		assertEquals(Integer.valueOf(1), execution.getFailedIndex());
		assertTrue(execution.getFeedback().getException() instanceof SignatureBatchException);
		assertEquals(2, token.signCalls);
		assertEquals(1, token.closeCalls);
	}

	private TestContext context(CountingToken token) {
		UIDisplay display = mock(UIDisplay.class);
		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);
		AppConfig config = mock(AppConfig.class);
		when(api.getAppConfig()).thenReturn(config);
		when(api.getTokenConnection(TOKEN_ID)).thenReturn(token);
		CountingOperationFactory operationFactory = new CountingOperationFactory();
		operationFactory.setDisplay(display);
		SignatureBatchFlow flow = new SignatureBatchFlow(display, api);
		flow.setOperationFactory(operationFactory);
		return new TestContext(api, flow, operationFactory);
	}

	private SignatureBatchRequest request(String... values) {
		SignatureBatchRequest request = new SignatureBatchRequest();
		request.setTokenId(TOKEN_ID);
		request.setDigestAlgorithm(DigestAlgorithm.SHA256);
		List<ToBeSigned> valuesToSign = new ArrayList<>();
		for (String value : values) {
			valuesToSign.add(new ToBeSigned(bytes(value)));
		}
		request.setValuesToSign(valuesToSign);
		return request;
	}

	private DSSPrivateKeyEntry testKey() {
		CertificateToken certificate = DSSUtils.loadCertificate(
				getClass().getResourceAsStream("/dummy-cert.pem"));
		return new DSSPrivateKeyEntry() {
			@Override
			public CertificateToken getCertificate() {
				return certificate;
			}

			@Override
			public CertificateToken[] getCertificateChain() {
				return new CertificateToken[] { certificate };
			}

			@Override
			public EncryptionAlgorithm getEncryptionAlgorithm() throws DSSException {
				return EncryptionAlgorithm.RSA;
			}
		};
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static final TokenId TOKEN_ID = new TokenId("batch-token");

	private record TestContext(EsupDSSClientAPI api, SignatureBatchFlow flow,
			CountingOperationFactory operationFactory) {}

	private static class CountingOperationFactory extends BasicOperationFactory {

		private final Map<Class<?>, Integer> counts = new HashMap<>();

		@Override
		public <R, T extends Operation<R>> Operation<R> getOperation(Class<T> clazz, Object... params) {
			counts.merge(clazz, 1, Integer::sum);
			return super.getOperation(clazz, params);
		}

		int count(Class<?> operationClass) {
			return counts.getOrDefault(operationClass, 0);
		}
	}

	private static class CountingToken implements SignatureTokenConnection {

		private final DSSPrivateKeyEntry key;
		private int getKeysCalls;
		private int signCalls;
		private int closeCalls;
		private int cancelIndex = -1;
		private int errorIndex = -1;

		private CountingToken(DSSPrivateKeyEntry key) {
			this.key = key;
		}

		@Override
		public void close() {
			closeCalls++;
		}

		@Override
		public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
			getKeysCalls++;
			return new ArrayList<>(List.of(key));
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm,
				DSSPrivateKeyEntry keyEntry) throws DSSException {
			int currentIndex = signCalls++;
			if (currentIndex == cancelIndex) {
				throw new CancelledOperationException();
			}
			if (currentIndex == errorIndex) {
				throw new DSSException("signature failure");
			}
			return new SignatureValue(SignatureAlgorithm.RSA_SHA256, toBeSigned.getBytes());
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm,
				DSSPrivateKeyEntry keyEntry) throws DSSException {
			return sign(toBeSigned, signatureAlgorithm.getDigestAlgorithm(), keyEntry);
		}

		@Override
		public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry keyEntry) throws DSSException {
			throw new UnsupportedOperationException();
		}

		@Override
		public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm,
				DSSPrivateKeyEntry keyEntry) throws DSSException {
			throw new UnsupportedOperationException();
		}
	}
}
