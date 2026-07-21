package org.esupportail.esupdssclient.opensc;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.PasswordInputCallback;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.junit.Test;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class OpenSCTokenProxyTest {

	@Test
	public void requestsPinOnceAndReusesAuthenticatedConnectionUntilClose() {
		AtomicInteger pinRequests = new AtomicInteger();
		PasswordInputCallback callback = () -> {
			pinRequests.incrementAndGet();
			return "1234".toCharArray();
		};
		FakeToken keyConnection = new FakeToken();
		FakeToken authenticatedConnection = new FakeToken();
		AtomicReference<KeyStore.PasswordProtection> passwordProtection = new AtomicReference<>();
		AtomicInteger keyConnectionsCreated = new AtomicInteger();
		AtomicInteger authenticatedConnectionsCreated = new AtomicInteger();
		OpenSCProductAdapter.OpenSCTokenProxy proxy = new OpenSCProductAdapter.OpenSCTokenProxy(callback, password -> {
			if (password == null) {
				keyConnectionsCreated.incrementAndGet();
				return keyConnection;
			}
			authenticatedConnectionsCreated.incrementAndGet();
			passwordProtection.set(password);
			return authenticatedConnection;
		});
		DSSPrivateKeyEntry key = mock(DSSPrivateKeyEntry.class);

		proxy.getKeys();
		proxy.sign(new ToBeSigned(new byte[] { 1 }), DigestAlgorithm.SHA256, key);
		proxy.sign(new ToBeSigned(new byte[] { 2 }), DigestAlgorithm.SHA256, key);

		assertEquals(1, pinRequests.get());
		assertEquals(1, keyConnection.getKeysCalls);
		assertEquals(2, authenticatedConnection.signCalls);
		assertEquals(1, keyConnectionsCreated.get());
		assertEquals(1, authenticatedConnectionsCreated.get());

		KeyStore.PasswordProtection firstPasswordProtection = passwordProtection.get();
		proxy.close();

		assertEquals(1, keyConnection.closeCalls);
		assertEquals(1, authenticatedConnection.closeCalls);
		assertThrows(IllegalStateException.class, firstPasswordProtection::getPassword);

		proxy.getKeys();
		proxy.sign(new ToBeSigned(new byte[] { 3 }), DigestAlgorithm.SHA256, key);

		assertEquals(2, pinRequests.get());
		assertEquals(2, keyConnectionsCreated.get());
		assertEquals(2, authenticatedConnectionsCreated.get());
		proxy.close();
	}

	private static class FakeToken implements SignatureTokenConnection {

		private int getKeysCalls;
		private int signCalls;
		private int closeCalls;

		@Override
		public void close() {
			closeCalls++;
		}

		@Override
		public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
			getKeysCalls++;
			return Collections.emptyList();
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm,
				DSSPrivateKeyEntry keyEntry) throws DSSException {
			signCalls++;
			return new SignatureValue(SignatureAlgorithm.RSA_SHA256, toBeSigned.getBytes());
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm,
				DSSPrivateKeyEntry keyEntry) throws DSSException {
			signCalls++;
			return new SignatureValue(signatureAlgorithm, toBeSigned.getBytes());
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
