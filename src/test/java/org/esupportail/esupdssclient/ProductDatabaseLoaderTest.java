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
package org.esupportail.esupdssclient;

import org.esupportail.esupdssclient.generic.SCDatabase;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import java.io.File;

public class ProductDatabaseLoaderTest {

	@Test
	public void test1() throws Exception {

		SCDatabase db = ProductDatabaseLoader.load(SCDatabase.class, new File("src/test/resources/db.xml"));

		JAXBContext ctx = JAXBContext.newInstance(SCDatabase.class);
		ctx.createMarshaller().marshal(db, System.out);

		ProductDatabaseLoader.saveAs(db, new File("target/db.xml"));

	}

}
