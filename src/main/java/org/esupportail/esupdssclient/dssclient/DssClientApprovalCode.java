package org.esupportail.esupdssclient.dssclient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

final class DssClientApprovalCode {

	private DssClientApprovalCode() {
	}

	static String calculate(String deviceId, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String hex = HexFormat.of().formatHex(
					mac.doFinal(("approval|" + deviceId).getBytes(StandardCharsets.UTF_8)));
			long value = Long.parseLong(hex.substring(0, 12), 16) % 1_000_000L;
			return String.format(Locale.ROOT, "%06d", value);
		} catch (Exception e) {
			throw new IllegalStateException("Impossible de calculer le code de validation", e);
		}
	}
}
