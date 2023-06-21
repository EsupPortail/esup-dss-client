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
package org.esupportail.esupdssclient.flow.operation;

import eu.europa.esig.dss.token.MSCAPISignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.generic.ConnectionInfo;
import org.esupportail.esupdssclient.generic.SCInfo;
import org.esupportail.esupdssclient.view.core.UIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This {@link CompositeOperation} allows to create a {@link TokenId}.
 *
 * <p>Expected parameters:
 * <ol>
 * <li>{@link EsupDSSClientAPI}</li>
 * <li>List of {@link Match}</li>
 * </ol>
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class CreateTokenOperation extends AbstractCompositeOperation<Map<TokenOperationResultKey, Object>> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateTokenOperation.class.getName());

    private EsupDSSClientAPI api;
    private List<Match> matchingProductAdapters;

    public CreateTokenOperation() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setParams(final Object... params) {
        try {
            this.api = (EsupDSSClientAPI) params[0];
            this.matchingProductAdapters = (List<Match>) params[1];
        } catch(final ArrayIndexOutOfBoundsException | ClassCastException e) {
            throw new IllegalArgumentException("Expected parameters: API, List of Match");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult<Map<TokenOperationResultKey, Object>> perform() {
        LOG.info(this.matchingProductAdapters.size() + " matching product adapters");

        if (!this.matchingProductAdapters.isEmpty()) {
            return this.createTokenAuto();
        } else {
            boolean advanced = false;
            if (this.api.getAppConfig().isAdvancedModeAvailable() && this.api.getAppConfig().isEnablePopUps()) {
                LOG.info("Advanced mode available");
                final OperationResult<Object> result =
                        this.operationFactory.getOperation(UIOperation.class, "/fxml/unsupported-product.fxml",
                                new Object[]{this.api.getAppConfig().getApplicationName()}).perform();
                if(result.getStatus().equals(BasicOperationStatus.SUCCESS)) {
                    advanced = true;
                }
            }

            if (advanced) {
                LOG.info("Advanced mode");
                return this.createTokenAdvanced();
            } else {
                if(this.api.getAppConfig().isEnablePopUps()) {
                    this.operationFactory.getOperation(UIOperation.class, "/fxml/message.fxml",
                            "unsuported.product.message", this.api.getAppConfig().getApplicationName()).perform();
                }
                return new OperationResult<Map<TokenOperationResultKey, Object>>(CoreOperationStatus.UNSUPPORTED_PRODUCT);
            }
        }
    }

    private OperationResult<Map<TokenOperationResultKey, Object>> createTokenAuto() {
        final Match match = this.matchingProductAdapters.get(0);
        final Product supportedProduct = match.getProduct();
        final ProductAdapter adapter = match.getAdapter();

        final SignatureTokenConnection connect;
        if(adapter.supportMessageDisplayCallback(supportedProduct)) {
            connect = adapter.connect(this.api, supportedProduct, this.display.getPasswordInputCallback(),
                    this.display.getMessageDisplayCallback());
        } else {
            connect = adapter.connect(this.api, supportedProduct, this.display.getPasswordInputCallback());
        }
        if (connect == null) {
            LOG.error("No connect returned");
            return new OperationResult<Map<TokenOperationResultKey, Object>>(CoreOperationStatus.NO_TOKEN);
        }
        final TokenId tokenId = this.api.registerTokenConnection(connect);
        if (tokenId == null) {
            LOG.error("Received null TokenId after registration");
            return new OperationResult<Map<TokenOperationResultKey, Object>>(CoreOperationStatus.NO_TOKEN_ID);
        }
        final Map<TokenOperationResultKey, Object> map = new HashMap<TokenOperationResultKey, Object>();
        map.put(TokenOperationResultKey.TOKEN_ID, tokenId);
        map.put(TokenOperationResultKey.ADVANCED_CREATION, false);
        map.put(TokenOperationResultKey.SELECTED_PRODUCT, supportedProduct);
        map.put(TokenOperationResultKey.SELECTED_PRODUCT_ADAPTER, adapter);
        return new OperationResult<Map<TokenOperationResultKey, Object>>(map);
    }

    private OperationResult<Map<TokenOperationResultKey, Object>> createTokenAdvanced() {
        LOG.info("Advanced mode selected");
        @SuppressWarnings("unchecked")
        final OperationResult<Object> result =
        this.operationFactory.getOperation(UIOperation.class, "/fxml/api-selection.fxml",
                new Object[]{this.api.getAppConfig().getApplicationName()}).perform();
        if(result.getStatus().equals(BasicOperationStatus.USER_CANCEL)) {
            return new OperationResult<Map<TokenOperationResultKey, Object>>(BasicOperationStatus.USER_CANCEL);
        }
        final Map<TokenOperationResultKey, Object> map = new HashMap<TokenOperationResultKey, Object>();
        map.put(TokenOperationResultKey.ADVANCED_CREATION, true);
        map.put(TokenOperationResultKey.SELECTED_API, result.getResult());
        final TokenId tokenId;
        switch ((ScAPI) result.getResult()) {
            case MSCAPI:
                tokenId = this.api.registerTokenConnection(new MSCAPISignatureToken());
                break;
            default:
                return new OperationResult<Map<TokenOperationResultKey, Object>>(CoreOperationStatus.UNSUPPORTED_PRODUCT);
        }
        map.put(TokenOperationResultKey.TOKEN_ID, tokenId);

        final ConnectionInfo connectionInfo = new ConnectionInfo();
        connectionInfo.setApiParam((String) map.get(TokenOperationResultKey.SELECTED_API_PARAMS));
        connectionInfo.setSelectedApi((ScAPI) map.get(TokenOperationResultKey.SELECTED_API));
        connectionInfo.setEnv(this.api.getEnvironmentInfo());
        final SCInfo info = new SCInfo();
        info.getInfos().add(connectionInfo);
        return new OperationResult<Map<TokenOperationResultKey,Object>>(map);
    }
}
