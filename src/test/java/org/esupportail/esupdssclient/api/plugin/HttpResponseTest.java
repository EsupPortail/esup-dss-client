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

import org.junit.Assert;
import org.junit.Test;

public class HttpResponseTest {

	@Test
	public void test1() {

		HttpResponse resp = new HttpResponse("test", "application/json", HttpStatus.OK);
		Assert.assertEquals("test", resp.getContent());
		Assert.assertEquals("application/json", resp.getContentType());
		Assert.assertEquals(200, resp.getHttpStatus().getHttpCode());

	}

	@Test(expected = IllegalArgumentException.class)
	public void test2() {

		HttpResponse resp = new HttpResponse("test", "application/json", null);
		Assert.assertEquals("test", resp.getContent());
		Assert.assertEquals("application/json", resp.getContentType());
		Assert.assertEquals(200, resp.getHttpStatus().getHttpCode());

	}

}
