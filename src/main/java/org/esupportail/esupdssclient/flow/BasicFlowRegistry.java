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
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicFlowRegistry implements FlowRegistry {

	private static Logger logger = LoggerFactory.getLogger(BasicFlowRegistry.class.getName());

	@Override
	@SuppressWarnings("unchecked")
	public Flow<?, ?> getFlow(String code, UIDisplay display, EsupDSSClientAPI api) {
		switch (code) {
		case CERTIFICATE_FLOW:
			return new GetCertificateFlow(display, api);
		case SIGNATURE_FLOW:
			return new SignatureFlow(display, api);
		default:
			logger.warn("Unknown flow code " + code);
			throw new NullPointerException("Flow not recognized/not implemented in this version.");
		}
	}

}
