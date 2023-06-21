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

import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.flow.operation.*;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.esupportail.esupdssclient.view.core.UIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class SignatureFlow extends AbstractCoreFlow<SignatureRequest, SignatureResponse> {

	private static final Logger logger = LoggerFactory.getLogger(SignatureFlow.class.getName());

	public SignatureFlow(UIDisplay display, EsupDSSClientAPI api) {
		super(display, api);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected Execution<SignatureResponse> process(EsupDSSClientAPI api, SignatureRequest req) throws Exception {
		if ((req.getToBeSigned() == null) || (req.getToBeSigned().getBytes() == null)) {
			throw new EsupDSSClientException("ToBeSigned is null");
		}

		if ((req.getDigestAlgorithm() == null)) {
			throw new EsupDSSClientException("Digest algorithm expected");
		}

		SignatureTokenConnection token = null;
		try {
			final OperationResult<Map<TokenOperationResultKey, Object>> getTokenOperationResult =
					getOperationFactory().getOperation(GetTokenOperation.class, api, req.getTokenId()).perform();
			if (getTokenOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				final Map<TokenOperationResultKey, Object> map = getTokenOperationResult.getResult();
				final TokenId tokenId = (TokenId) map.get(TokenOperationResultKey.TOKEN_ID);

				final OperationResult<SignatureTokenConnection> getTokenConnectionOperationResult =
						getOperationFactory().getOperation(GetTokenConnectionOperation.class, api, tokenId).perform();
				if (getTokenConnectionOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
					token = getTokenConnectionOperationResult.getResult();
					logger.info("Token " + token);
					
					final Product product = (Product) map.get(TokenOperationResultKey.SELECTED_PRODUCT);
					final ProductAdapter productAdapter = (ProductAdapter) map.get(TokenOperationResultKey.SELECTED_PRODUCT_ADAPTER);
					final OperationResult<DSSPrivateKeyEntry> selectPrivateKeyOperationResult =
							getOperationFactory().getOperation(
									SelectPrivateKeyOperation.class, token, api, product, productAdapter, null, req.getKeyId()).perform();
					if (selectPrivateKeyOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
						final DSSPrivateKeyEntry key = selectPrivateKeyOperationResult.getResult();

						logger.info("Key " + key + " " + key.getCertificate().getCertificate().getSubjectX500Principal() + " from " + key.getCertificate().getCertificate().getIssuerX500Principal());
						final OperationResult<SignatureValue> signOperationResult = getOperationFactory().getOperation(
								SignOperation.class, token, req.getToBeSigned(), req.getDigestAlgorithm(), key).perform();
						if(signOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
							final SignatureValue value = signOperationResult.getResult();
							logger.info("Signature performed " + value);

							if ((Boolean) map.get(TokenOperationResultKey.ADVANCED_CREATION)) {
								getOperationFactory().getOperation(AdvancedCreationFeedbackOperation.class,
										api, map).perform();
							}
							
							if(api.getAppConfig().isEnablePopUps() && api.getAppConfig().isEnableInformativePopUps()) {
								getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml",
									"signature.flow.finished", api.getAppConfig().getApplicationName()).perform();
							}
							
							return new Execution<SignatureResponse>(new SignatureResponse(value, key.getCertificate(), key.getCertificateChain()));
						} else {
							return handleErrorOperationResult(signOperationResult);
						}
					} else {
						if(api.getAppConfig().isEnablePopUps()) {
							getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml",
								"signature.flow.no.key.selected", api.getAppConfig().getApplicationName()).perform();
						}
						return handleErrorOperationResult(selectPrivateKeyOperationResult);
					}
				} else {
					if(api.getAppConfig().isEnablePopUps()) {
						getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml",
							"signature.flow.bad.token", api.getAppConfig().getApplicationName()).perform();
					}
					return handleErrorOperationResult(getTokenConnectionOperationResult);
				}
			} else {
				return handleErrorOperationResult(getTokenOperationResult);
			}
		} catch (Exception e) {
			logger.error("Flow error", e);
			throw handleException(e);
		} finally {
			if(token != null) {
				try {
					token.close();
				} catch(final Exception e) {
					logger.error("Exception when closing token", e);
				}
			}
		}
	}
}
