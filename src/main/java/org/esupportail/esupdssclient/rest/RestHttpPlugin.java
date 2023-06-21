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
package org.esupportail.esupdssclient.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.plugin.*;
import org.esupportail.esupdssclient.json.ByteArrayTypeAdapter;
import org.esupportail.esupdssclient.json.CertificateTypeAdapter;
import org.esupportail.esupdssclient.json.LocalDateAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Default implementation of HttpPlugin for Esup-DSS-Client.
 *
 * @author David Naramski
 */
public class RestHttpPlugin implements HttpPlugin {

	private static final Logger logger = LoggerFactory.getLogger(RestHttpPlugin.class.getName());

	private static final Gson customGson = new GsonBuilder()
			.registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayTypeAdapter())
			.registerTypeAdapter(CertificateToken.class, new CertificateTypeAdapter()).create();

	@Override
	public List<InitializationMessage> init(String pluginId, EsupDSSClientAPI api) {
		return Collections.emptyList();
	}

	@Override
	public HttpResponse process(EsupDSSClientAPI api, HttpRequest req) throws Exception {

		final String target = req.getTarget();
		logger.info("PathInfo " + target);

		final String payload = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
		logger.info("Payload '" + payload + "'");

		switch(target) {
		case "/sign":
			return signRequest(api, req, payload);
		case "/certificates":
			return getCertificates(api, req, payload);
		case "/identityInfo":
			return getIdentityInfo(api, payload);
		case "/authenticate":
			return authenticate(api, req, payload);
		default:
			throw new RuntimeException("Target not recognized " + target);
		}
	}

	protected <T> Execution<T> returnNullIfValid(EsupDSSClientRequest request) {
		return null;
	}
	
	private HttpResponse signRequest(EsupDSSClientAPI api, HttpRequest req, String payload) {
		logger.info("Signature");
		final SignatureRequest r;
		if (StringUtils.isEmpty(payload)) {
			r = new SignatureRequest();

			String data = req.getParameter("dataToSign");
			if (data != null) {
				logger.info("Data to sign " + data);
				ToBeSigned tbs = new ToBeSigned();
				tbs.setBytes(DatatypeConverter.parseBase64Binary(data));
				r.setToBeSigned(tbs);
			}

			String digestAlgo = req.getParameter("digestAlgo");
			if (digestAlgo != null) {
				logger.info("digestAlgo " + digestAlgo);
				r.setDigestAlgorithm(DigestAlgorithm.forName(digestAlgo, DigestAlgorithm.SHA256));
			}

			String tokenIdString = req.getParameter("tokenId");
			if (tokenIdString != null) {
				TokenId tokenId = new TokenId(tokenIdString);
				r.setTokenId(tokenId);
			}

			String keyId = req.getParameter("keyId");
			if (keyId != null) {
				r.setKeyId(keyId);
			}
		} else {
			r = customGson.fromJson(payload, SignatureRequest.class);
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.sign(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse getCertificates(EsupDSSClientAPI api, HttpRequest req, String payload) {
		logger.info("API call certificates");
		final GetCertificateRequest r;
		if (StringUtils.isEmpty(payload)) {
			r = new GetCertificateRequest();

			final String certificatePurpose = req.getParameter("certificatePurpose");
			if (certificatePurpose != null) {
				logger.info("Certificate purpose " + certificatePurpose);
				final Purpose purpose = Enum.valueOf(Purpose.class, certificatePurpose);
				final CertificateFilter certificateFilter = new CertificateFilter();
				certificateFilter.setPurpose(purpose);
				r.setCertificateFilter(certificateFilter);
			}else {
				final String nonRepudiation = req.getParameter("nonRepudiation");
				if(isNotBlank(nonRepudiation)) {
					final CertificateFilter certificateFilter = new CertificateFilter();
					certificateFilter.setNonRepudiationBit(Boolean.parseBoolean(nonRepudiation));
					r.setCertificateFilter(certificateFilter);
				}
			}
			
		} else {
			r = customGson.fromJson(payload, GetCertificateRequest.class);
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.getCertificate(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse getIdentityInfo(EsupDSSClientAPI api, String payload) {
		logger.info("API call get identity info");
		final GetIdentityInfoRequest r;
		if (StringUtils.isEmpty(payload)) {
			r = new GetIdentityInfoRequest();
		} else {
			r = customGson.fromJson(payload, GetIdentityInfoRequest.class);
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.getIdentityInfo(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse authenticate(EsupDSSClientAPI api, HttpRequest req, String payload) {
		logger.info("Authenticate");
		final AuthenticateRequest r;
		if (StringUtils.isEmpty(payload)) {
			r = new AuthenticateRequest();

			final String data = req.getParameter("challenge");
			if (data != null) {
				logger.info("Challenge " + data);
				final ToBeSigned tbs = new ToBeSigned();
				tbs.setBytes(DatatypeConverter.parseBase64Binary(data));
				r.setChallenge(tbs);
			}
		} else {
			r = customGson.fromJson(payload, AuthenticateRequest.class);
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.authenticate(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse checkRequestValidity(final EsupDSSClientAPI api, final EsupDSSClientRequest request) {
		final Execution<Object> verification = returnNullIfValid(request);
		if(verification != null) {
			final Feedback feedback;
			if(verification.getFeedback() == null) {
				feedback = new Feedback();
				feedback.setFeedbackStatus(FeedbackStatus.SIGNATURE_VERIFICATION_FAILED);
				verification.setFeedback(feedback);
			} else {
				feedback = verification.getFeedback();
			}
			feedback.setInfo(api.getEnvironmentInfo());
			feedback.setApiVersion(api.getAppConfig().getApplicationVersion());
			return toHttpResponse(verification);
		} else {
			return null;
		}
	}
	
	private HttpResponse toHttpResponse(final Execution<?> respObj) {
		if (respObj.isSuccess()) {
			return new HttpResponse(customGson.toJson(respObj), "application/json;charset=UTF-8", HttpStatus.OK);
		} else {
			return new HttpResponse(customGson.toJson(respObj), "application/json;charset=UTF-8", HttpStatus.ERROR);
		}
	}
}
