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
package org.esupportail.esupdssclient.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/**
 * Enumerate JRE Vendors detected by Esup-DSS-Client
 * 
 * @author David Naramski
 *
 */
@XmlType(name = "javaVendor")
@XmlEnum
public enum JREVendor {

	OPENJDK, NOT_RECOGNIZED;

	private static final Logger logger = LoggerFactory.getLogger(JREVendor.class);

	public static JREVendor forJREVersion(String jreVersion) {
		if (Integer.parseInt(jreVersion.split("\\.")[0]) >= 17) {
			return OPENJDK;
		} else {
			logger.warn("JRE not recognized " + jreVersion);
			return NOT_RECOGNIZED;
		}
	}

}
