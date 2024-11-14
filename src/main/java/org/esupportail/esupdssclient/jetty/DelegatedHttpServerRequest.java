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
package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.server.Request;
import org.esupportail.esupdssclient.api.plugin.HttpRequest;

import java.io.InputStream;

public class DelegatedHttpServerRequest implements HttpRequest {

	private Request wrapped;

	private String context;

	public DelegatedHttpServerRequest(Request delegate, String context) {
		this.wrapped = delegate;
		String ctx = null;
		if (context.startsWith("/")) {
			ctx = context;
		} else {
			ctx = "/" + context;
		}
		this.context = ctx;
	}

	@Override
	public String getTarget() {
		return Request.getPathInContext(wrapped).substring(context.length());
	}

	@Override
	public InputStream getInputStream() {
		return Request.asInputStream(wrapped);
	}

	@Override
	public String getParameter(String name) {
		if(wrapped.getAttributeNameSet().contains(name)) {
			return wrapped.getAttribute(name).toString();
		} else {
			return null;
		}
    }

}
