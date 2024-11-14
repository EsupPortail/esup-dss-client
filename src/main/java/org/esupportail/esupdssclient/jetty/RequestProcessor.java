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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.europa.esig.dss.model.x509.CertificateToken;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.esupportail.esupdssclient.ConfigurationException;
import org.esupportail.esupdssclient.TechnicalException;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Execution;
import org.esupportail.esupdssclient.api.Feedback;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.plugin.HttpPlugin;
import org.esupportail.esupdssclient.api.plugin.HttpResponse;
import org.esupportail.esupdssclient.api.plugin.HttpStatus;
import org.esupportail.esupdssclient.json.ByteArrayTypeAdapter;
import org.esupportail.esupdssclient.json.CertificateTypeAdapter;
import org.esupportail.esupdssclient.json.LocalDateAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class RequestProcessor extends Handler.Abstract {

	private static final Logger logger = LoggerFactory.getLogger(RequestProcessor.class.getName());

	private static final Gson customGson = new GsonBuilder()
			.registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayTypeAdapter())
			.registerTypeAdapter(CertificateToken.class, new CertificateTypeAdapter()).create();

	private static final String UTF8 = "UTF-8";

	private static final String NEXUJS_TEMPLATE = "nexu.ftl.js";

	private EsupDSSClientAPI api;

	private String apiHostname;

	private final Template template;

	private Callback callback;

	public RequestProcessor() {
		try {
			Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
			cfg.setClassForTemplateLoading(getClass(), "/");
			this.template = cfg.getTemplate(NEXUJS_TEMPLATE, UTF8);
		} catch (IOException e) {
			logger.error("Cannot find template for JS", e);
			throw new ConfigurationException("Cannot find template for JS");
		}
	}

	public void setConfig(EsupDSSClientAPI api) {
		this.api = api;
	}
	
	public void setApiHostname(String apiHostname) {
		this.apiHostname = apiHostname;
	}

	public void writeResponse(Response response, String text) throws IOException {
		response.write(true, ByteBuffer.wrap(text.getBytes()), callback);
		callback.succeeded();
	}

	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {
		this.callback = callback;
		String target = Request.getPathInContext(request);
		String remoteAddr = Request.getRemoteAddr(request);
		if (!"0:0:0:0:0:0:0:1".equals(remoteAddr) && !"127.0.0.1".equals(remoteAddr)) {
			logger.warn("Cannot accept request from " + remoteAddr);
			response.setStatus(HttpStatus.ERROR.getHttpCode());
			writeResponse(response, "Please connect from localhost");
			callback.failed(new Throwable("Please connect from localhost"));
			return false;
		}

		if(api.getAppConfig().isCorsAllowAllOrigins()) {
			response.getHeaders().add("Access-Control-Allow-Origin", "*");
		} else {
			if(api.getAppConfig().getCorsAllowedOrigins().contains(request.getHeaders().get("Origin"))) {
				response.getHeaders().add("Access-Control-Allow-Origin", request.getHeaders().get("Origin"));
			} else {
				// No match ==> use first value returned by iterator and log a warning
				logger.warn(request.getHeaders().get("Origin") + " does not match any value in corsAllowedOrigins: "
						+ api.getAppConfig().getCorsAllowedOrigins());
				response.getHeaders().add("Access-Control-Allow-Origin", api.getAppConfig().getCorsAllowedOrigins().iterator().next());
			}
		}
		response.getHeaders().add("Vary", "Origin");
		response.getHeaders().add("Access-Control-Allow-Methods", "OPTIONS, GET, POST");
		response.getHeaders().add("Access-Control-Allow-Headers", "Content-Type");

		if ("OPTIONS".equals(request.getMethod())) {
			response.setStatus(HttpStatus.OK.getHttpCode());
			callback.succeeded();
			return true;
		}

		logger.info("Request " + target);

		try {
			if ("/favicon.ico".equals(target)) {
				favIcon(response);
			} else if ("/nexu.js".equals(target)) {
				nexuJs(request, response);
			} else if ("/".equals(target) || "/nexu-info".equals(target)) {
				apiInfo(response);
			} else {
				httpPlugin(target, request, response);
			}
			return true;
		} catch(Exception e) {
			logger.error("Cannot process request", e);
			try {
				response.setStatus(HttpStatus.ERROR.getHttpCode());
								final Execution<?> execution = new Execution<>(BasicOperationStatus.EXCEPTION);
				final Feedback feedback = new Feedback(e);
				feedback.setApiVersion(api.getAppConfig().getApplicationVersion());
				feedback.setInfo(api.getEnvironmentInfo());
				execution.setFeedback(feedback);
				writeResponse(response, customGson.toJson(execution));
			} catch (IOException e2) {
				logger.error("Cannot write error !?", e2);
			}
		}
		return false;
	}

	private void httpPlugin(String target, Request request, Response response) throws Exception {
		int index = target.indexOf("/", 1);
		String pluginId = target.substring(target.charAt(0) == '/' ? 1 : 0, index);

		logger.info("Process request " + target + " pluginId: " + pluginId);
		HttpPlugin httpPlugin = api.getHttpPlugin(pluginId);

		HttpResponse resp = httpPlugin.process(api, new DelegatedHttpServerRequest(request, '/' + pluginId));
		if (resp == null || resp.getContent() == null) {
			throw new TechnicalException("Plugin responded null");
		} else {
			response.setStatus(resp.getHttpStatus().getHttpCode());
			writeResponse(response, resp.getContent());
		}
	}

	private void apiInfo(Response response) throws IOException {
		response.getHeaders().add("pragma", "no-cache");
		response.getHeaders().add("expires", -1);
		writeResponse(response, "{ \"version\": \"" + api.getAppConfig().getApplicationVersion() + "\"}");
	}

	private void favIcon(Response response) throws IOException {
		InputStream in = this.getClass().getResourceAsStream("/tray-icon.png");
		response.write(true, ByteBuffer.wrap(in.readAllBytes()), callback);
		in.close();
	}

	private void nexuJs(Request request, Response response) throws IOException {
		final StringWriter writer = new StringWriter();
		final Map<String, String> model = new HashMap<>();
		model.put("scheme", request.getHttpURI().getScheme());
		model.put("api_hostname", apiHostname);
		model.put("api_port", Integer.toString(Request.getLocalPort(request)));
		try {
			template.process(model, writer);
		} catch (Exception e) {
			logger.error("Cannot process template", e);
			throw new TechnicalException("Cannot process template", e);
		}
		writeResponse(response, writer.toString());
	}

}
