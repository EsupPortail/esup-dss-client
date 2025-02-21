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
package org.esupportail.esupdssclient.keystore;


import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.*;
import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.ProductDatabaseLoader;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.FutureOperationInvocation;
import org.esupportail.esupdssclient.api.flow.NoOpFutureOperationInvocation;
import org.esupportail.esupdssclient.view.core.NonBlockingUIOperation;
import org.esupportail.esupdssclient.view.core.UIOperation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore.PasswordProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Product adapter for {@link ConfiguredKeystore}.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class KeystoreProductAdapter implements ProductAdapter {

	private final File apiHome;
	
	public KeystoreProductAdapter(final File apiHome) {
		super();
		this.apiHome = apiHome;
	}

	@Override
	public boolean accept(Product product) {
		return (product instanceof ConfiguredKeystore) || (product instanceof NewKeystore);
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
	public SignatureTokenConnection connect(EsupDSSClientAPI api, Product product, PasswordInputCallback callback) {
		if (product instanceof NewKeystore) {
			throw new IllegalArgumentException("Given product was not configured!");
		}
		final ConfiguredKeystore configuredKeystore = (ConfiguredKeystore) product;
		return new KeystoreTokenProxy(configuredKeystore, callback);
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
		return false;
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
	@SuppressWarnings("unchecked")
	public FutureOperationInvocation<Product> getConfigurationOperation(EsupDSSClientAPI api, Product product) {
		if (product instanceof NewKeystore) {
			return UIOperation.getFutureOperationInvocation(UIOperation.class, "/fxml/configure-keystore.fxml", api.getAppConfig().getApplicationName());
		} else {
			return new NoOpFutureOperationInvocation<Product>(product);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public FutureOperationInvocation<Boolean> getSaveOperation(EsupDSSClientAPI api, Product product) {
		if (product instanceof NewKeystore) {
			throw new IllegalArgumentException("Given product was not configured!");
		} else {
			final ConfiguredKeystore keystore = (ConfiguredKeystore) product;
			if(keystore.isToBeSaved()) {
				return UIOperation.getFutureOperationInvocation(UIOperation.class, "/fxml/save-keystore.fxml",
					api.getAppConfig().getApplicationName(), this, keystore);
			} else {
				return new NoOpFutureOperationInvocation<Boolean>(true);
			}
		}
	}

	@Override
	public SystrayMenuItem getExtensionSystrayMenuItem() {
		return new SystrayMenuItem() {
			@Override
			public String getLabel() {
				return ResourceBundle.getBundle("bundles/api").getString("systray.menu.manage.keystores");
			}
			
			@Override
			public FutureOperationInvocation<Void> getFutureOperationInvocation() {
				return UIOperation.getFutureOperationInvocation(NonBlockingUIOperation.class, "/fxml/manage-keystores.fxml",
						getDatabase());
			}
		};
	}
	
	@Override
	public List<Product> detectProducts() {
		final List<Product> products = new ArrayList<>();
		products.addAll(getDatabase().getKeystores());
		products.add(new NewKeystore());
		return products;
	}

	private KeystoreDatabase getDatabase() {
		return ProductDatabaseLoader.load(KeystoreDatabase.class, new File(apiHome, "keystore-database.xml"));
	}
	
	public void saveKeystore(final ConfiguredKeystore keystore) {
		getDatabase().add(keystore);
	}
	
	private static class KeystoreTokenProxy implements SignatureTokenConnection {

		private SignatureTokenConnection proxied;
		private final ConfiguredKeystore configuredKeystore;
		private final PasswordInputCallback callback;
				
		public KeystoreTokenProxy(ConfiguredKeystore configuredKeystore, PasswordInputCallback callback) {
			super();
			this.configuredKeystore = configuredKeystore;
			this.callback = callback;
		}

		private void initSignatureTokenConnection() {
			if(proxied != null) {
				return;
			}
			try {
				switch(configuredKeystore.getType()) {
				case PKCS12:
					proxied = new Pkcs12SignatureToken(new URL(configuredKeystore.getUrl()).openStream(),
							new PasswordProtection(callback.getPassword()));
					break;
				case JKS:
					proxied = new JKSSignatureToken(new URL(configuredKeystore.getUrl()).openStream(),
							new PasswordProtection(callback.getPassword()));
					break;
				default:
					throw new IllegalStateException("Unhandled keystore type: " + configuredKeystore.getType());
				}
			} catch (MalformedURLException e) {
				throw new EsupDSSClientException(e);
			} catch (IOException e) {
				throw new EsupDSSClientException(e);
			}
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
			initSignatureTokenConnection();
			return proxied.sign(toBeSigned, digestAlgorithm, keyEntry);
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			initSignatureTokenConnection();
			return proxied.sign(toBeSigned, signatureAlgorithm, dssPrivateKeyEntry);
		}

		@Override
		public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			throw new IllegalStateException("This product adapter cannot return list of supported digest algorithms.");
		}

		@Override
		public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
			return null;
		}
	}
}
