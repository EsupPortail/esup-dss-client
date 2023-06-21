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

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Feedback;
import org.esupportail.esupdssclient.api.FeedbackStatus;
import org.esupportail.esupdssclient.api.ScAPI;
import org.esupportail.esupdssclient.api.flow.OperationResult;

import java.util.Map;

/**
 * This {@link CompositeOperation} allows to provide some feedback in case of advanced creation.
 *
 * Expected parameters:
 * <ol>
 * <li>{@link EsupDSSClientAPI}</li>
 * <li>{@link Map} whose keys are {@link TokenOperationResultKey} and values are {@link Object}.</li>
 * </ol>
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class AdvancedCreationFeedbackOperation extends AbstractCompositeOperation<Void> {

    private EsupDSSClientAPI api;
    private Map<TokenOperationResultKey, Object> map;

    public AdvancedCreationFeedbackOperation() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setParams(final Object... params) {
        try {
            this.api = (EsupDSSClientAPI) params[0];
            this.map = (Map<TokenOperationResultKey, Object>) params[1];
        } catch(final ClassCastException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Expected parameters: API, Map");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult<Void> perform() {
        if(this.api.getAppConfig().isEnablePopUps()) {
            final Feedback feedback = new Feedback();
            feedback.setFeedbackStatus(FeedbackStatus.SUCCESS);
            feedback.setApiParameter((String) this.map.get(TokenOperationResultKey.SELECTED_API_PARAMS));
            feedback.setSelectedAPI((ScAPI) this.map.get(TokenOperationResultKey.SELECTED_API));
        }
        return new OperationResult<Void>((Void) null);
    }

}
