/**
 * © Nowina Solutions, 2015-2016
 *
 * Concédée sous licence EUPL, version 1.1 ou – dès leur approbation par la Commission européenne - versions ultérieures de l’EUPL (la «Licence»).
 * Vous ne pouvez utiliser la présente œuvre que conformément à la Licence.
 * Vous pouvez obtenir une copie de la Licence à l’adresse suivante:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Sauf obligation légale ou contractuelle écrite, le logiciel distribué sous la Licence est distribué «en l’état»,
 * SANS GARANTIES OU CONDITIONS QUELLES QU’ELLES SOIENT, expresses ou implicites.
 * Consultez la Licence pour les autorisations et les restrictions linguistiques spécifiques relevant de la Licence.
 */
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
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.FutureOperationInvocation;
import org.esupportail.esupdssclient.api.flow.NoOpFutureOperationInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Product adapter for {@link OpenSC}.
 *
 * @author David Lemaignent (david.lemaignent@univ-rouen.fr)
 */
public class OpenSCProductAdapter implements ProductAdapter {

	private static final Logger logger = LoggerFactory.getLogger(OpenSCProductAdapter.class);

	public OpenSCProductAdapter() {
		super();
	}

	@Override
	public SignatureTokenConnection connect(EsupDSSClientAPI api, Product product, PasswordInputCallback callback) {
		return new OpenSCTokenProxy(callback);
	}

	@Override
	public boolean accept(Product product) {
		return (product instanceof OpenSC);
	}

	@Override
	public String getLabel(EsupDSSClientAPI api, Product product, PasswordInputCallback callback) {
		return product.getLabel();
	}

	@Override
	public String getLabel(EsupDSSClientAPI api, Product product, PasswordInputCallback callback, MessageDisplayCallback messageCallback) {
		throw new IllegalStateException("This product adapter does not support message display callback.");
	}

	@Override
	public boolean supportMessageDisplayCallback(Product product) {
		return false;
	}

	@Override
	public SignatureTokenConnection connect(EsupDSSClientAPI api, Product product, PasswordInputCallback callback, MessageDisplayCallback messageCallback) {
		throw new IllegalStateException("This product adapter does not support message display callback.");
	}

	@Override
	public boolean canReturnIdentityInfo(Product product) {
		return false;
	}

	@Override
	public GetIdentityInfoResponse getIdentityInfo(SignatureTokenConnection token) {
		throw new IllegalStateException("This product adapter cannot return identity information.");
	}

	@Override
	public boolean supportCertificateFilter(Product product) {
		return true;
	}

	@Override
	public List<DSSPrivateKeyEntry> getKeys(SignatureTokenConnection token, CertificateFilter certificateFilter) {
		throw new IllegalStateException("This product adapter does not support certificate filter.");
	}

	@Override
	public boolean canReturnSuportedDigestAlgorithms(Product product) {
		return false;
	}

	@Override
	public List<DigestAlgorithm> getSupportedDigestAlgorithms(Product product) {
		throw new IllegalStateException("This product adapter cannot return list of supported digest algorithms.");
	}

	@Override
	public DigestAlgorithm getPreferredDigestAlgorithm(Product product) {
		throw new IllegalStateException("This product adapter cannot return list of supported digest algorithms.");
	}

	@Override
	public FutureOperationInvocation<Product> getConfigurationOperation(EsupDSSClientAPI api, Product product) {
		return new NoOpFutureOperationInvocation<Product>(product);
	}

	@Override
	public FutureOperationInvocation<Boolean> getSaveOperation(EsupDSSClientAPI api, Product product) {
		return new NoOpFutureOperationInvocation<Boolean>(true);
	}

	@Override
	public SystrayMenuItem getExtensionSystrayMenuItem() {
		return null;
	}

	@Override
	public List<Product> detectProducts() {
		final List<Product> products = new ArrayList<>();
		String version = null;
		boolean enabled = false;
		OpenSCSignatureToken token = new OpenSCSignatureToken(null);
		try {
			byte[] output = token.launchProcess("opensc-tool -i");
			String outputStr = new String(output).trim();
			// opensc-tool -i renvoie plusieurs lignes, la première contient la version
			// OpenSC 0.26.1 [gcc  14.2.0]
			version = outputStr.split("\n")[0].trim();
			enabled = true;
			logger.info("OpenSC detected via opensc-tool: {}", version);
		} catch (Exception e) {
			logger.warn("OpenSC not detected: opensc-tool failed. Error: {}", e.getMessage());
		}
		products.add(new OpenSC(version, enabled));
		return products;
	}

	static class OpenSCTokenProxy implements SignatureTokenConnection {

		private SignatureTokenConnection keyConnection;
		private SignatureTokenConnection authenticatedConnection;
		private KeyStore.PasswordProtection authenticatedPassword;
		private final PasswordInputCallback callback;
		private final Function<KeyStore.PasswordProtection, SignatureTokenConnection> tokenFactory;

		public OpenSCTokenProxy(PasswordInputCallback callback) {
			this(callback, OpenSCSignatureToken::new);
		}

		OpenSCTokenProxy(PasswordInputCallback callback,
				Function<KeyStore.PasswordProtection, SignatureTokenConnection> tokenFactory) {
			this.callback = callback;
			this.tokenFactory = tokenFactory;
		}

		private void initSignatureTokenConnection() {
			if (keyConnection == null) {
				keyConnection = tokenFactory.apply(null);
			}
		}

		private void initSignatureTokenConnectionPassword() {
			if (authenticatedConnection != null) {
				return;
			}
			char[] password = callback.getPassword();
			KeyStore.PasswordProtection passwordProtection = null;
			try {
				passwordProtection = new KeyStore.PasswordProtection(password);
				authenticatedConnection = tokenFactory.apply(passwordProtection);
				authenticatedPassword = passwordProtection;
			} catch (RuntimeException e) {
				destroyPassword(passwordProtection);
				throw e;
			} finally {
				if (password != null) {
					Arrays.fill(password, '\0');
				}
			}
		}

		@Override
		public void close() {
			SignatureTokenConnection keys = keyConnection;
			SignatureTokenConnection authenticated = authenticatedConnection;
			KeyStore.PasswordProtection password = authenticatedPassword;
			keyConnection = null;
			authenticatedConnection = null;
			authenticatedPassword = null;
			try {
				if (keys != null) {
					keys.close();
				}
			} finally {
				try {
					if (authenticated != null && authenticated != keys) {
						authenticated.close();
					}
				} finally {
					destroyPassword(password);
				}
			}
		}

		private void destroyPassword(KeyStore.PasswordProtection password) {
			if (password != null) {
				try {
					password.destroy();
				} catch (Exception e) {
					logger.warn("Unable to clear OpenSC password protection", e);
				}
			}
		}

		@Override
		public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
			initSignatureTokenConnection();
			return keyConnection.getKeys();
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, DSSPrivateKeyEntry keyEntry)
				throws DSSException {
			initSignatureTokenConnectionPassword();
			return authenticatedConnection.sign(toBeSigned, digestAlgorithm, keyEntry);
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			initSignatureTokenConnectionPassword();
			return authenticatedConnection.sign(toBeSigned, signatureAlgorithm, dssPrivateKeyEntry);
		}

		@Override
		public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			initSignatureTokenConnectionPassword();
			return authenticatedConnection.signDigest(digest, dssPrivateKeyEntry);
		}

		@Override
		public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			initSignatureTokenConnectionPassword();
			return authenticatedConnection.signDigest(digest, signatureAlgorithm, dssPrivateKeyEntry);
		}
	}
}
