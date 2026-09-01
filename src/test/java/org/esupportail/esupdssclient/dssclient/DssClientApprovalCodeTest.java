package org.esupportail.esupdssclient.dssclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DssClientApprovalCodeTest {

	@Test
	public void generatesStableSixDigitCode() {
		String code = DssClientApprovalCode.calculate("device", "secret");

		assertEquals(code, DssClientApprovalCode.calculate("device", "secret"));
		assertEquals("165732", code);
		assertTrue(code.matches("\\d{6}"));
	}
}
