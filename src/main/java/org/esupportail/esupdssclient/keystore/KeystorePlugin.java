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

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.plugin.InitializationMessage;
import org.esupportail.esupdssclient.api.plugin.SignaturePlugin;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Plugin that registers Keystore implementation to Esup-DSS-Client.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class KeystorePlugin implements SignaturePlugin {

	public KeystorePlugin() {
		super();
	}

	@Override
	public List<InitializationMessage> init(String pluginId, EsupDSSClientAPI api) {
		api.registerProductAdapter(new KeystoreProductAdapter(api.getAppConfig().getApiHome()));
		return Collections.emptyList();
	}

}
