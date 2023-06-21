/**
 * © Nowina Solutions, 2015-2015
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
package org.esupportail.esupdssclient.flow;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.Operation;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.flow.operation.*;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.esupportail.esupdssclient.view.core.UIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

class GetCertificateFlow extends AbstractCoreFlow<GetCertificateRequest, GetCertificateResponse> {

    static final Logger logger = LoggerFactory.getLogger(GetCertificateFlow.class);

    public GetCertificateFlow(final UIDisplay display, final EsupDSSClientAPI api) {
        super(display, api);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Execution<GetCertificateResponse> process(final EsupDSSClientAPI api, final GetCertificateRequest req) throws Exception {
    	SignatureTokenConnection token = null;
    	try {
    		Product defaultProduct = api.getAppConfig().getDefaultProduct();
    		while (true) {
    			final Product selectedProduct;
    			if(defaultProduct != null) {
    				selectedProduct = defaultProduct;
    				defaultProduct = null;
    			} else {
    				final Object[] params = {
    						api.getAppConfig().getApplicationName(), null, api.detectProducts(), api
    				};
    				final Operation<Product> operation = this.getOperationFactory().getOperation(UIOperation.class, "/fxml/product-selection.fxml", params);
    				final OperationResult<Product> selectProductOperationResult = operation.perform();
    				if (selectProductOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    					selectedProduct = selectProductOperationResult.getResult();
    				} else {
    					return this.handleErrorOperationResult(selectProductOperationResult);
    				}
    			}

    			final OperationResult<List<Match>> getMatchingCardAdaptersOperationResult = this.getOperationFactory()
    					.getOperation(GetMatchingProductAdaptersOperation.class, Arrays.asList(selectedProduct), api).perform();
    			if (getMatchingCardAdaptersOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    				List<Match> matchingProductAdapters = getMatchingCardAdaptersOperationResult.getResult();

    				final OperationResult<List<Match>> configureProductOperationResult = this.getOperationFactory()
    						.getOperation(ConfigureProductOperation.class, matchingProductAdapters, api).perform();
    				if (configureProductOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    					matchingProductAdapters = configureProductOperationResult.getResult();
    					final OperationResult<Map<TokenOperationResultKey, Object>> createTokenOperationResult = this.getOperationFactory()
    							.getOperation(CreateTokenOperation.class, api, matchingProductAdapters).perform();
    					if (createTokenOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    						final Map<TokenOperationResultKey, Object> map = createTokenOperationResult.getResult();
    						final TokenId tokenId = (TokenId) map.get(TokenOperationResultKey.TOKEN_ID);

    						final OperationResult<SignatureTokenConnection> getTokenConnectionOperationResult = this.getOperationFactory()
    								.getOperation(GetTokenConnectionOperation.class, api, tokenId).perform();
    						if (getTokenConnectionOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    							token = getTokenConnectionOperationResult.getResult();

    							final Product product = (Product) map.get(TokenOperationResultKey.SELECTED_PRODUCT);
    							final ProductAdapter productAdapter = (ProductAdapter) map.get(TokenOperationResultKey.SELECTED_PRODUCT_ADAPTER);
    							final OperationResult<DSSPrivateKeyEntry> selectPrivateKeyOperationResult = this.getOperationFactory()
    									.getOperation(SelectPrivateKeyOperation.class, token, api, product, productAdapter, req.getCertificateFilter()).perform();
    							if (selectPrivateKeyOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
    								final DSSPrivateKeyEntry key = selectPrivateKeyOperationResult.getResult();

    								if ((Boolean) map.get(TokenOperationResultKey.ADVANCED_CREATION)) {
    									this.getOperationFactory().getOperation(AdvancedCreationFeedbackOperation.class, api, map).perform();
    								}

    								this.getOperationFactory().getOperation(SaveProductOperation.class, productAdapter, product, api).perform();

    								final GetCertificateResponse resp = new GetCertificateResponse();
    								resp.setTokenId(tokenId);

    								final CertificateToken certificate = key.getCertificate();
    								resp.setCertificate(certificate);
    								resp.setKeyId(certificate.getDSSIdAsString());
    								resp.setEncryptionAlgorithm(certificate.getSignatureAlgorithm().getEncryptionAlgorithm());

    								final CertificateToken[] certificateChain = key.getCertificateChain();
    								if (certificateChain != null) {
    									resp.setCertificateChain(certificateChain);
    								}

    								if (productAdapter.canReturnSuportedDigestAlgorithms(product)) {
    									resp.setSupportedDigests(productAdapter.getSupportedDigestAlgorithms(product));
    									resp.setPreferredDigest(productAdapter.getPreferredDigestAlgorithm(product));
    								}

    								if (api.getAppConfig().isEnablePopUps() && api.getAppConfig().isEnableInformativePopUps()) {
    									this.getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml", new Object[] {
    											"certificates.flow.finished"
    									}).perform();
    								}
    								return new Execution<GetCertificateResponse>(resp);
    							} else if (selectPrivateKeyOperationResult.getStatus().equals(CoreOperationStatus.BACK)) {
    								continue;
    							} else {
    								return this.handleErrorOperationResult(selectPrivateKeyOperationResult);
    							}
    						} else {
    							return this.handleErrorOperationResult(getTokenConnectionOperationResult);
    						}
    					} else {
    						return this.handleErrorOperationResult(createTokenOperationResult);
    					}
    				} else {
    					return this.handleErrorOperationResult(configureProductOperationResult);
    				}
    			} else {
    				return this.handleErrorOperationResult(getMatchingCardAdaptersOperationResult);
    			}
    		}
    	} catch (final Exception e) {
    		logger.error("Flow error", e);
    		throw this.handleException(e);
    	} finally {
    		if (token != null) {
    			if (req.isCloseToken()) {
    				try {
    					token.close();
    				} catch (final Exception e) {
    					logger.error("Exception when closing token", e);
    				}
    			}
    		}
    	}
    }
}
