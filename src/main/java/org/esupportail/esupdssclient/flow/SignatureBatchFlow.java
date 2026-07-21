package org.esupportail.esupdssclient.flow;

import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SignatureBatchFlow extends AbstractCoreFlow<SignatureBatchRequest, SignatureBatchResponse> {

	private static final Logger logger = LoggerFactory.getLogger(SignatureBatchFlow.class);

	SignatureBatchFlow(UIDisplay display, EsupDSSClientAPI api) {
		super(display, api);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected Execution<SignatureBatchResponse> process(EsupDSSClientAPI api, SignatureBatchRequest req) throws Exception {
		validate(req);

		SignatureTokenConnection token = null;
		try {
			OperationResult<Map<TokenOperationResultKey, Object>> getTokenResult = getOperationFactory()
					.getOperation(GetTokenOperation.class, api, req.getTokenId()).perform();
			if (!getTokenResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				return handleErrorOperationResult(getTokenResult);
			}

			Map<TokenOperationResultKey, Object> tokenData = getTokenResult.getResult();
			TokenId tokenId = (TokenId) tokenData.get(TokenOperationResultKey.TOKEN_ID);
			OperationResult<SignatureTokenConnection> connectionResult = getOperationFactory()
					.getOperation(GetTokenConnectionOperation.class, api, tokenId).perform();
			if (!connectionResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				return handleErrorOperationResult(connectionResult);
			}
			token = connectionResult.getResult();

			Product product = (Product) tokenData.get(TokenOperationResultKey.SELECTED_PRODUCT);
			ProductAdapter productAdapter = (ProductAdapter) tokenData.get(TokenOperationResultKey.SELECTED_PRODUCT_ADAPTER);
			OperationResult<DSSPrivateKeyEntry> keyResult = getOperationFactory().getOperation(
					SelectPrivateKeyOperation.class, token, api, product, productAdapter, null, req.getKeyId()).perform();
			if (!keyResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				if (api.getAppConfig().isEnablePopUps()) {
					getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml",
							"signature.flow.no.key.selected", api.getAppConfig().getApplicationName()).perform();
				}
				return handleErrorOperationResult(keyResult);
			}

			DSSPrivateKeyEntry key = keyResult.getResult();
			List<SignatureValue> signatureValues = new ArrayList<>(req.getValuesToSign().size());
			for (int index = 0; index < req.getValuesToSign().size(); index++) {
				OperationResult<SignatureValue> signResult;
				try {
					signResult = getOperationFactory().getOperation(SignOperation.class, token,
							req.getValuesToSign().get(index), req.getDigestAlgorithm(), key).perform();
				} catch (Exception e) {
					return failedExecution(index, e);
				}
				if (!signResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
					if (signResult.getStatus().equals(BasicOperationStatus.EXCEPTION)) {
						return failedExecution(index, signResult.getException());
					}
					Execution<SignatureBatchResponse> failure = new Execution<>(signResult.getStatus());
					failure.setFailedIndex(index);
					return failure;
				}
				signatureValues.add(signResult.getResult());
				try {
					req.notifyProgress(index + 1, req.getValuesToSign().size());
				} catch (RuntimeException e) {
					logger.warn("Unable to publish batch signature progress at index {}", index, e);
				}
			}

			if (Boolean.TRUE.equals(tokenData.get(TokenOperationResultKey.ADVANCED_CREATION))) {
				getOperationFactory().getOperation(AdvancedCreationFeedbackOperation.class, api, tokenData).perform();
			}
			if (api.getAppConfig().isEnablePopUps() && api.getAppConfig().isEnableInformativePopUps()) {
				getOperationFactory().getOperation(UIOperation.class, "/fxml/message.fxml",
						"signature.flow.finished", api.getAppConfig().getApplicationName()).perform();
			}

			return new Execution<>(new SignatureBatchResponse(signatureValues, key.getCertificate(),
					key.getCertificateChain()));
		} catch (Exception e) {
			logger.error("Batch signature flow error", e);
			throw handleException(e);
		} finally {
			if (token != null) {
				try {
					token.close();
				} catch (Exception e) {
					logger.error("Exception when closing batch signature token", e);
				}
			}
		}
	}

	private Execution<SignatureBatchResponse> failedExecution(int index, Exception cause) {
		SignatureBatchException batchException = new SignatureBatchException(index, cause);
		logger.error("Batch signature failed at index {}", index, cause);
		handleException(batchException);
		Execution<SignatureBatchResponse> failure = new Execution<>(BasicOperationStatus.EXCEPTION);
		failure.setFailedIndex(index);
		failure.setFeedback(new Feedback(batchException));
		return failure;
	}

	private void validate(SignatureBatchRequest req) {
		if (req == null || req.getValuesToSign() == null || req.getValuesToSign().isEmpty()) {
			throw new EsupDSSClientException("At least one ToBeSigned is expected");
		}
		for (int index = 0; index < req.getValuesToSign().size(); index++) {
			ToBeSigned value = req.getValuesToSign().get(index);
			if (value == null || value.getBytes() == null) {
				throw new SignatureBatchException(index, new EsupDSSClientException("ToBeSigned is null"));
			}
		}
		if (req.getDigestAlgorithm() == null) {
			throw new EsupDSSClientException("Digest algorithm expected");
		}
	}
}
