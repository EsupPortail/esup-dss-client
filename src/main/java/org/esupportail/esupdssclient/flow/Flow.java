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

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Execution;
import org.esupportail.esupdssclient.api.Feedback;
import org.esupportail.esupdssclient.api.flow.Operation;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.esupportail.esupdssclient.view.core.UIOperation;

/**
 * A flow is a sequence of {@link Operation}.
 * 
 * @author David Naramski
 */
public abstract class Flow<I, O> {

	private UIDisplay display;

	private OperationFactory operationFactory;

	private EsupDSSClientAPI api;

	public Flow(UIDisplay display, EsupDSSClientAPI api) {
		if (display == null) {
			throw new IllegalArgumentException("display cannot be null");
		}
		this.display = display;
		this.api = api;
	}

	public final void setOperationFactory(final OperationFactory operationFactory) {
		this.operationFactory = operationFactory;
	}

	protected final OperationFactory getOperationFactory() {
		return operationFactory;
	}

	public final Execution<O> execute(EsupDSSClientAPI api, I input) throws Exception {
		try {
			final Execution<O> out = process(api, input);
			return out;
		} finally {
			display.close(true);
		}
	}

	protected abstract Execution<O> process(EsupDSSClientAPI api, I input) throws Exception;

	protected final UIDisplay getDisplay() {
		return display;
	}

	@SuppressWarnings("unchecked")
	protected Exception handleException(final Exception e) {
		if (api.getAppConfig().isEnablePopUps()) {
			if (api.getAppConfig().isEnableIncidentReport()) {
				final Feedback feedback = new Feedback(e);
				getOperationFactory().getOperation(UIOperation.class, "/fxml/provide-feedback.fxml",
						new Object[] { feedback, api.getAppConfig().getServerUrl(),
								api.getAppConfig().getApplicationVersion(), api.getAppConfig().getApplicationName(),
								api.getAppConfig() })
						.perform();
			} else {
				getOperationFactory()
						.getOperation(UIOperation.class, "/fxml/message.fxml",
								new Object[] { "exception.failure.message", api.getAppConfig().getApplicationName() })
						.perform();
			}
		}
		return e;
	}
}
