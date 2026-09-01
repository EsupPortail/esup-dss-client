package org.esupportail.esupdssclient.dssclient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DssClientPairingServiceTest {
	private String previousAllowInsecureHttp;

	@Before
	public void disableInsecureHttpByDefault() {
		previousAllowInsecureHttp = System.getProperty(DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY);
		System.clearProperty(DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY);
	}

	@After
	public void restoreInsecureHttpProperty() {
		if (previousAllowInsecureHttp == null) {
			System.clearProperty(DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY);
		} else {
			System.setProperty(DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY, previousAllowInsecureHttp);
		}
	}

	@Test
	public void shouldExtractAssociatedUrlWithoutPairingToken() {
		String pairingUrl = "https://signature.example.org/esup-signature/dss-client/pair?token=secret";

		assertEquals("https://signature.example.org/esup-signature",
				DssClientPairingService.buildAssociatedUrl(pairingUrl));
	}

	@Test
	public void shouldExtractAssociatedUrlWithPortAndNoContextPathWhenExplicitlyEnabled() {
		System.setProperty(DssClientPairingService.ALLOW_INSECURE_HTTP_PROPERTY, "true");
		String pairingUrl = "http://localhost:8080/dss-client/pair?code=ABCDE-FGHIJ";

		assertEquals("http://localhost:8080", DssClientPairingService.buildAssociatedUrl(pairingUrl));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsHttpByDefaultIncludingLocalhost() {
		DssClientPairingService.buildAssociatedUrl(
				"http://localhost:8080/dss-client/pair?code=ABCDE-FGHIJ");
	}

	@Test
	public void acceptsWebsocketUrlFromAssociatedInstance() throws Exception {
		DssClientPairingService.validateWebsocketUrl("https://signature.example.org/esup-signature",
				"wss://signature.example.org/esup-signature/dss-client/ws");
	}

	@Test(expected = java.io.IOException.class)
	public void rejectsWebsocketUrlFromAnotherHost() throws Exception {
		DssClientPairingService.validateWebsocketUrl("https://signature.example.org/esup-signature",
				"wss://attacker.example/esup-signature/dss-client/ws");
	}

	@Test(expected = java.io.IOException.class)
	public void rejectsDowngradedWebsocketUrl() throws Exception {
		DssClientPairingService.validateWebsocketUrl("https://signature.example.org/esup-signature",
				"ws://signature.example.org/esup-signature/dss-client/ws");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonHttpPairingUrl() {
		DssClientPairingService.buildAssociatedUrl("file://signature.example.org/dss-client/pair");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsLookalikePairingPath() {
		DssClientPairingService.buildAssociatedUrl("https://signature.example.org/dss-client/pair-evil?code=abc");
	}
}
