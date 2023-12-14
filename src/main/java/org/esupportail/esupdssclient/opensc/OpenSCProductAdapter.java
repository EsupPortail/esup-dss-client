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
import eu.europa.esig.dss.enumerations.MaskGenerationFunction;
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

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Product adapter for {@link OpenSC}.
 *
 * @author David Lemaignent (david.lemaignent@univ-rouen.fr)
 */
public class OpenSCProductAdapter implements ProductAdapter {

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
		products.add(new OpenSC());
		return products;
	}

	private static class OpenSCTokenProxy implements SignatureTokenConnection {

		private SignatureTokenConnection proxied;
		private final PasswordInputCallback callback;

		public OpenSCTokenProxy(PasswordInputCallback callback) {
			super();
			this.callback = callback;
        }

		private void initSignatureTokenConnection() {
			proxied = new OpenSCSignatureToken(null);
		}

		private void initSignatureTokenConnectionPassword() {
			proxied = new OpenSCSignatureToken(new KeyStore.PasswordProtection(callback.getPassword()));
		}

		@Override
		public void close() {
			final SignatureTokenConnection stc = proxied;
			// Always nullify proxied even in case of exception when calling close()
			proxied = null;
			if(stc != null) {
				stc.close();
			}
		}

		@Override
		public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
			initSignatureTokenConnection();
			return proxied.getKeys();
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, DSSPrivateKeyEntry keyEntry)
				throws DSSException {
			initSignatureTokenConnectionPassword();
			return proxied.sign(toBeSigned, digestAlgorithm, keyEntry);
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, MaskGenerationFunction mgf, DSSPrivateKeyEntry keyEntry) throws DSSException {
			initSignatureTokenConnectionPassword();
			return proxied.sign(toBeSigned, digestAlgorithm, mgf, keyEntry);
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			initSignatureTokenConnectionPassword();
			return proxied.sign(toBeSigned, signatureAlgorithm, dssPrivateKeyEntry);
		}

		@Override
		public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			return null;
		}

		@Override
		public SignatureValue signDigest(Digest digest, MaskGenerationFunction maskGenerationFunction, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			return null;
		}

		@Override
		public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			return null;
		}
	}
}
