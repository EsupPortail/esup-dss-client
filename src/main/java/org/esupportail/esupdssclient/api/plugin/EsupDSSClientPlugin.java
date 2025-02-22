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
package org.esupportail.esupdssclient.api.plugin;

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;

import java.util.List;

/**
 * Interface for a plugin
 * 
 * @author David Naramski
 *
 */
public interface EsupDSSClientPlugin {

	/**
	 * This method is called by Esup-DSS-Client directly after the instantiation.
	 * 
	 * @param pluginId
	 * @param api
	 */
	List<InitializationMessage> init(String pluginId, EsupDSSClientAPI api);

}