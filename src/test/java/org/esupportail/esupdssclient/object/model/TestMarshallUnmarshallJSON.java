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
package org.esupportail.esupdssclient.object.model;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.JKSSignatureToken;
import org.apache.commons.codec.binary.Base64;
import org.esupportail.esupdssclient.api.EsupDSSClientRequest;
import org.esupportail.esupdssclient.api.JREVendor;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.json.ByteArrayTypeAdapter;
import org.esupportail.esupdssclient.json.CertificateTypeAdapter;
import org.esupportail.esupdssclient.json.LocalDateAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore.PasswordProtection;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * JUnit test class for JSON marshalling/unmarshalling.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class TestMarshallUnmarshallJSON {

	private static final Gson customGson = new GsonBuilder()
			.registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayTypeAdapter())
			.registerTypeAdapter(CertificateToken.class, new CertificateTypeAdapter()).create();

	@SuppressWarnings("serial")
	private static <T> TypeToken<Execution<T>> buildTokenType(Class<T> clas) {
		TypeToken<Execution<T>> where = new TypeToken<Execution<T>>() {
		}.where(new TypeParameter<T>() {
		}, clas);
		return where;
	}

	public TestMarshallUnmarshallJSON() {
		super();
	}

	private void setCommonRequestFields(final ApiRequest request) {
		request.setExternalId("externalId");
		request.setNonce("nonce");
		request.setRequestSeal("requestSeal");
		request.setUserLocale("userLocale");
	}

	private void assertCommonRequestFields(final EsupDSSClientRequest request) {
		Assert.assertEquals("externalId", request.getExternalId());
		Assert.assertEquals("nonce", request.getNonce());
		Assert.assertEquals("requestSeal", request.getRequestSeal());
		Assert.assertEquals("userLocale", request.getUserLocale());
	}

	private void setFeedback(final org.esupportail.esupdssclient.api.Execution<?> execution) {
		final org.esupportail.esupdssclient.api.Feedback feedback = new org.esupportail.esupdssclient.api.Feedback();

		feedback.setApiVersion("apiVersion");

		final org.esupportail.esupdssclient.api.EnvironmentInfo environmentInfo = new org.esupportail.esupdssclient.api.EnvironmentInfo();
		environmentInfo.setArch(org.esupportail.esupdssclient.api.Arch.AMD64);
		environmentInfo.setJreVersion(org.esupportail.esupdssclient.api.JREVendor.OPENJDK);
		environmentInfo.setOs(org.esupportail.esupdssclient.api.OS.LINUX);
		environmentInfo.setOsArch("osArch");
		environmentInfo.setOsName("osName");
		environmentInfo.setOsVersion("osVersion");
		feedback.setInfo(environmentInfo);

		feedback.setFeedbackStatus(org.esupportail.esupdssclient.api.FeedbackStatus.SUCCESS);

		feedback.setStacktrace("stackTrace");

		feedback.setUserComment("userComment");

		execution.setFeedback(feedback);
	}

	private void assertSuccessExecution(final Execution<?> execution) {
		Assert.assertNull(execution.getError());
		Assert.assertNull(execution.getErrorMessage());
		Assert.assertTrue(execution.isSuccess());
	}

	private void assertFeedback(final Execution<?> execution) {
		Assert.assertNotNull(execution.getFeedback());

		Assert.assertEquals("apiVersion", execution.getFeedback().getapiVersion());

		Assert.assertNotNull(execution.getFeedback().getInfo());
		Assert.assertEquals(Arch.AMD64, execution.getFeedback().getInfo().getArch());
		Assert.assertEquals(JREVendor.OPENJDK.name(), execution.getFeedback().getInfo().getJreVersion());
		Assert.assertEquals(OS.LINUX, execution.getFeedback().getInfo().getOs());
		Assert.assertEquals("osArch", execution.getFeedback().getInfo().getOsArch());
		Assert.assertEquals("osName", execution.getFeedback().getInfo().getOsName());
		Assert.assertEquals("osVersion", execution.getFeedback().getInfo().getOsVersion());

		Assert.assertEquals(FeedbackStatus.SUCCESS, execution.getFeedback().getFeedbackStatus());

		Assert.assertEquals("stackTrace", execution.getFeedback().getStacktrace());

		Assert.assertEquals("userComment", execution.getFeedback().getUserComment());
	}

	@Test
	public void testGetCertificateRequest() {
		final CertificateFilter certFilter = new CertificateFilter();
		certFilter.setPurpose(Purpose.AUTHENTICATION);
		final GetCertificateRequest getCertificateRequest = new GetCertificateRequest(certFilter);
		setCommonRequestFields(getCertificateRequest);
		final String json = customGson.toJson(getCertificateRequest);

		final org.esupportail.esupdssclient.api.GetCertificateRequest getCertificateRequestAPI = customGson.fromJson(json,
				org.esupportail.esupdssclient.api.GetCertificateRequest.class);
		Assert.assertNotNull(getCertificateRequestAPI);
		assertCommonRequestFields(getCertificateRequestAPI);
		Assert.assertNotNull(getCertificateRequestAPI.getCertificateFilter());
		Assert.assertNull(getCertificateRequestAPI.getCertificateFilter().getCertificateSHA1());
		Assert.assertEquals(org.esupportail.esupdssclient.api.Purpose.AUTHENTICATION,
				getCertificateRequestAPI.getCertificateFilter().getPurpose());
	}

	@Test
	public void testGetCertificateResponse() {

		try (JKSSignatureToken sigToken = new JKSSignatureToken(this.getClass().getResourceAsStream("/keystore.jks"),
				new PasswordProtection("password".toCharArray()))) {
			final CertificateToken certificate = sigToken.getKeys().get(0).getCertificate();
			final org.esupportail.esupdssclient.api.TokenId tokenId = new org.esupportail.esupdssclient.api.TokenId();
			tokenId.setId("tokenId");
			final org.esupportail.esupdssclient.api.GetCertificateResponse getCertificateResponse = new org.esupportail.esupdssclient.api.GetCertificateResponse();
			getCertificateResponse.setCertificate(certificate);
			getCertificateResponse
					.setCertificateChain(new CertificateToken[] { certificate, certificate, certificate });
			getCertificateResponse.setEncryptionAlgorithm(EncryptionAlgorithm.RSA);
			getCertificateResponse.setKeyId("keyId");
			getCertificateResponse.setPreferredDigest(DigestAlgorithm.SHA256);
			getCertificateResponse.setSupportedDigests(
					Arrays.asList(DigestAlgorithm.SHA1, DigestAlgorithm.SHA256, DigestAlgorithm.SHA512));
			getCertificateResponse.setTokenId(tokenId);
			final org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.GetCertificateResponse> respAPI = new org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.GetCertificateResponse>(
					getCertificateResponse);
			setFeedback(respAPI);
			final String json = customGson.toJson(respAPI);

			final Execution<GetCertificateResponse> resp = customGson.fromJson(json,
					buildTokenType(GetCertificateResponse.class).getType());
			assertSuccessExecution(resp);
			assertFeedback(resp);
			Assert.assertNotNull(resp.getResponse());
			final String certificateInBase64 = Base64.encodeBase64String(certificate.getEncoded());
			Assert.assertEquals(certificateInBase64, resp.getResponse().getCertificate());
			Assert.assertArrayEquals(new String[] { certificateInBase64, certificateInBase64, certificateInBase64 },
					resp.getResponse().getCertificateChain());
			Assert.assertEquals("RSA", resp.getResponse().getEncryptionAlgorithm());
			Assert.assertEquals("keyId", resp.getResponse().getKeyId());
			Assert.assertEquals("SHA256", resp.getResponse().getPreferredDigest());
			Assert.assertEquals(Arrays.asList("SHA1", "SHA256", "SHA512"), resp.getResponse().getSupportedDigests());
			Assert.assertNotNull(resp.getResponse().getTokenId());
			Assert.assertEquals("tokenId", resp.getResponse().getTokenId().getId());
		}
	}

	@Test
	public void testSignatureRequest() {
		final TokenId tokenId = new TokenId();
		tokenId.setId("tokenId");
		final ToBeSigned tbs = new ToBeSigned();
		tbs.setBytes("to be signed".getBytes(StandardCharsets.UTF_8));
		final SignatureRequest signatureRequest = new SignatureRequest();
		setCommonRequestFields(signatureRequest);
		signatureRequest.setDigestAlgorithm("SHA1");
		signatureRequest.setKeyId("keyId");
		signatureRequest.setToBeSigned(tbs);
		signatureRequest.setTokenId(tokenId);
		final String json = customGson.toJson(signatureRequest);

		final org.esupportail.esupdssclient.api.SignatureRequest signatureRequestAPI = customGson.fromJson(json,
				org.esupportail.esupdssclient.api.SignatureRequest.class);
		Assert.assertNotNull(signatureRequestAPI);
		assertCommonRequestFields(signatureRequestAPI);
		Assert.assertEquals(DigestAlgorithm.SHA1, signatureRequestAPI.getDigestAlgorithm());
		Assert.assertEquals("keyId", signatureRequestAPI.getKeyId());
		Assert.assertNotNull(signatureRequestAPI.getToBeSigned());
		Assert.assertEquals("to be signed",
				new String(signatureRequestAPI.getToBeSigned().getBytes(), StandardCharsets.UTF_8));
		Assert.assertNotNull(signatureRequestAPI.getTokenId());
		Assert.assertEquals("tokenId", signatureRequestAPI.getTokenId().getId());
	}

	@Test
	public void testSignatureResponse() {
		try (JKSSignatureToken sigToken = new JKSSignatureToken(this.getClass().getResourceAsStream("/keystore.jks"),
				new PasswordProtection("password".toCharArray()))) {
			final CertificateToken certificate = sigToken.getKeys().get(0).getCertificate();
			final org.esupportail.esupdssclient.api.SignatureResponse signatureResponse = new org.esupportail.esupdssclient.api.SignatureResponse(
					new SignatureValue(SignatureAlgorithm.RSA_SHA256, "to be signed".getBytes(StandardCharsets.UTF_8)),
					certificate, new CertificateToken[] { certificate, certificate, certificate });
			final org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.SignatureResponse> respAPI = new org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.SignatureResponse>(
					signatureResponse);
			setFeedback(respAPI);
			final String json = customGson.toJson(respAPI);

			final Execution<SignatureResponse> resp = customGson.fromJson(json,
					buildTokenType(SignatureResponse.class).getType());
			assertSuccessExecution(resp);
			assertFeedback(resp);
			Assert.assertNotNull(resp.getResponse());
			final String certificateInBase64 = Base64.encodeBase64String(certificate.getEncoded());
			Assert.assertEquals(certificateInBase64, resp.getResponse().getCertificate());
			Assert.assertArrayEquals(new String[] { certificateInBase64, certificateInBase64, certificateInBase64 },
					resp.getResponse().getCertificateChain());
			Assert.assertEquals("RSA_SHA256", resp.getResponse().getSignatureAlgorithm());
			Assert.assertEquals("dG8gYmUgc2lnbmVk", resp.getResponse().getSignatureValue());
		}
	}

	@Test
	public void testGetIdentityInfoRequest() {
		final GetIdentityInfoRequest getIdentityInfoRequest = new GetIdentityInfoRequest();
		setCommonRequestFields(getIdentityInfoRequest);
		final String json = customGson.toJson(getIdentityInfoRequest);

		final org.esupportail.esupdssclient.api.GetIdentityInfoRequest getIdentityInfoRequestAPI = customGson.fromJson(json,
				org.esupportail.esupdssclient.api.GetIdentityInfoRequest.class);
		Assert.assertNotNull(getIdentityInfoRequestAPI);
		assertCommonRequestFields(getIdentityInfoRequestAPI);
	}

	@Test
	public void testGetIdentityInfoResponse() {
		try (JKSSignatureToken sigToken = new JKSSignatureToken(this.getClass().getResourceAsStream("/keystore.jks"),
				new PasswordProtection("password".toCharArray()))) {
			final CertificateToken certificate = sigToken.getKeys().get(0).getCertificate();
			final org.esupportail.esupdssclient.api.GetIdentityInfoResponse getIdentityInfoResponse = new org.esupportail.esupdssclient.api.GetIdentityInfoResponse();
			getIdentityInfoResponse.setAddress("address");
			getIdentityInfoResponse.setCardDeliveryAuthority("cardDeliveryAuthority");
			getIdentityInfoResponse.setCardNumber("cardNumber");
			getIdentityInfoResponse.setCardValidityDateBegin(LocalDate.now());
			getIdentityInfoResponse.setCardValidityDateEnd(LocalDate.now().plusDays(1));
			getIdentityInfoResponse.setChipNumber("chipNumber");
			getIdentityInfoResponse.setCity("city");
			getIdentityInfoResponse.setDateOfBirth(LocalDate.now().minusDays(1));
			getIdentityInfoResponse.setFirstName("firstName");
			getIdentityInfoResponse.setGender(org.esupportail.esupdssclient.api.GetIdentityInfoResponse.Gender.MALE);
			getIdentityInfoResponse.setLastName("lastName");
			getIdentityInfoResponse.setMiddleName("middleName");
			getIdentityInfoResponse.setNationality("nationality");
			getIdentityInfoResponse.setNationalNumber("nationalNumber");
			getIdentityInfoResponse.setNobleCondition("nobleCondition");
			getIdentityInfoResponse.setPhoto("photo".getBytes(StandardCharsets.UTF_8));
			getIdentityInfoResponse.setPhotoMimeType("photoMimeType");
			getIdentityInfoResponse.setPlaceOfBirth("placeOfBirth");
			getIdentityInfoResponse.setPostalCode("postalCode");
			final Map<String, org.esupportail.esupdssclient.api.IdentityInfoSignatureData> signatureData = new HashMap<String, org.esupportail.esupdssclient.api.IdentityInfoSignatureData>();
			final SignatureValue signatureValue = new SignatureValue(SignatureAlgorithm.RSA_SHA512,
					"signatureValue".getBytes(StandardCharsets.UTF_8));
			final org.esupportail.esupdssclient.api.IdentityInfoSignatureData identityInfoSignatureData = new org.esupportail.esupdssclient.api.IdentityInfoSignatureData(
					"rawData".getBytes(StandardCharsets.UTF_8), signatureValue,
					new CertificateToken[] { certificate, certificate, certificate });
			signatureData.put("key", identityInfoSignatureData);
			getIdentityInfoResponse.setSignatureData(signatureData);
			getIdentityInfoResponse.setSpecialStatus("specialStatus");
			final org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.GetIdentityInfoResponse> respAPI = new org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.GetIdentityInfoResponse>(
					getIdentityInfoResponse);
			setFeedback(respAPI);
			final String json = customGson.toJson(respAPI);

			final Execution<GetIdentityInfoResponse> resp = customGson.fromJson(json,
					buildTokenType(GetIdentityInfoResponse.class).getType());
			assertSuccessExecution(resp);
			assertFeedback(resp);
			Assert.assertNotNull(resp.getResponse());
			final String certificateInBase64 = Base64.encodeBase64String(certificate.getEncoded());
			Assert.assertEquals("address", resp.getResponse().getAddress());
			Assert.assertEquals("cardDeliveryAuthority", resp.getResponse().getCardDeliveryAuthority());
			Assert.assertEquals("cardNumber", resp.getResponse().getCardNumber());
			Assert.assertEquals(LocalDate.now(), resp.getResponse().getCardValidityDateBegin());
			Assert.assertEquals(LocalDate.now().plusDays(1), resp.getResponse().getCardValidityDateEnd());
			Assert.assertEquals("chipNumber", resp.getResponse().getChipNumber());
			Assert.assertEquals("city", resp.getResponse().getCity());
			Assert.assertEquals(LocalDate.now().minusDays(1), resp.getResponse().getDateOfBirth());
			Assert.assertEquals("firstName", resp.getResponse().getFirstName());
			Assert.assertEquals(GetIdentityInfoResponse.Gender.MALE, resp.getResponse().getGender());
			Assert.assertEquals("lastName", resp.getResponse().getLastName());
			Assert.assertEquals("middleName", resp.getResponse().getMiddleName());
			Assert.assertEquals("nationality", resp.getResponse().getNationality());
			Assert.assertEquals("nationalNumber", resp.getResponse().getNationalNumber());
			Assert.assertEquals("nobleCondition", resp.getResponse().getNobleCondition());
			Assert.assertEquals("cGhvdG8=", resp.getResponse().getPhoto());
			Assert.assertEquals("photoMimeType", resp.getResponse().getPhotoMimeType());
			Assert.assertEquals("placeOfBirth", resp.getResponse().getPlaceOfBirth());
			Assert.assertEquals("postalCode", resp.getResponse().getPostalCode());
			Assert.assertNotNull(resp.getResponse().getSignatureData());
			Assert.assertEquals(1, resp.getResponse().getSignatureData().size());
			Assert.assertEquals("key", resp.getResponse().getSignatureData().keySet().iterator().next());
			Assert.assertNotNull(resp.getResponse().getSignatureData().get("key"));
			Assert.assertEquals("cmF3RGF0YQ==", resp.getResponse().getSignatureData().get("key").getRawData());
			Assert.assertArrayEquals(new String[] { certificateInBase64, certificateInBase64, certificateInBase64 },
					resp.getResponse().getSignatureData().get("key").getCertificateChain());
			Assert.assertEquals("SHA512withRSA",
					resp.getResponse().getSignatureData().get("key").getSignatureValue().getAlgorithm().getJCEId());
			Assert.assertEquals("signatureValue",
					new String(resp.getResponse().getSignatureData().get("key").getSignatureValue().getValue(), StandardCharsets.UTF_8));
			Assert.assertEquals("specialStatus", resp.getResponse().getSpecialStatus());
		}
	}

	@Test
	public void testAuthenticateRequest() {
		final ToBeSigned tbs = new ToBeSigned();
		tbs.setBytes("to be signed".getBytes(StandardCharsets.UTF_8));
		final AuthenticateRequest authenticateRequest = new AuthenticateRequest();
		setCommonRequestFields(authenticateRequest);
		authenticateRequest.setChallenge(tbs);
		final String json = customGson.toJson(authenticateRequest);

		final org.esupportail.esupdssclient.api.AuthenticateRequest authenticateRequestAPI = customGson.fromJson(json,
				org.esupportail.esupdssclient.api.AuthenticateRequest.class);
		Assert.assertNotNull(authenticateRequestAPI);
		assertCommonRequestFields(authenticateRequestAPI);
		Assert.assertNotNull(authenticateRequestAPI.getChallenge());
		Assert.assertEquals("to be signed",
				new String(authenticateRequestAPI.getChallenge().getBytes(), StandardCharsets.UTF_8));
	}

	@Test
	public void testAuthenticateResponse() {
		try (JKSSignatureToken sigToken = new JKSSignatureToken(this.getClass().getResourceAsStream("/keystore.jks"),
				new PasswordProtection("password".toCharArray()))) {
			final CertificateToken certificate = sigToken.getKeys().get(0).getCertificate();

			final org.esupportail.esupdssclient.api.AuthenticateResponse authenticateResponse = new org.esupportail.esupdssclient.api.AuthenticateResponse(
					"keyId", certificate, new CertificateToken[] { certificate, certificate, certificate },
					new SignatureValue(SignatureAlgorithm.RSA_SHA256, "to be signed".getBytes(StandardCharsets.UTF_8)));
			final org.esupportail.esupdssclient.api.Execution<org.esupportail.esupdssclient.api.AuthenticateResponse> respAPI = new org.esupportail.esupdssclient.api.Execution<>(
					authenticateResponse);
			setFeedback(respAPI);

			final String json = customGson.toJson(respAPI);
			final Execution<AuthenticateResponse> resp = customGson.fromJson(json, buildTokenType(AuthenticateResponse.class).getType());
			assertSuccessExecution(resp);
			assertFeedback(resp);
			Assert.assertNotNull(resp.getResponse());
			final String certificateInBase64 = Base64.encodeBase64String(certificate.getEncoded());
			Assert.assertEquals("keyId", resp.getResponse().getKeyId());
			Assert.assertEquals(certificateInBase64, resp.getResponse().getCertificate());
			Assert.assertArrayEquals(new String[] { certificateInBase64, certificateInBase64, certificateInBase64 },
					resp.getResponse().getCertificateChain());
			Assert.assertNotNull(resp.getResponse().getSignatureValue());
			Assert.assertEquals("SHA256withRSA", resp.getResponse().getSignatureValue().getAlgorithm().getJCEId());
			Assert.assertEquals("to be signed", new String(resp.getResponse().getSignatureValue().getValue(), StandardCharsets.UTF_8));
		}
	}

	@Test
	public void testException() {
		final org.esupportail.esupdssclient.api.Execution<?> respAPI = new org.esupportail.esupdssclient.api.Execution<Void>(
				BasicOperationStatus.EXCEPTION);
		setFeedback(respAPI);
		final String json = customGson.toJson(respAPI);

		final Execution<Void> resp = customGson.fromJson(json, buildTokenType(Void.class).getType());
		Assert.assertFalse(resp.isSuccess());
		Assert.assertNull(resp.getResponse());
		Assert.assertEquals(BasicOperationStatus.EXCEPTION.getCode(), resp.getError());
		Assert.assertEquals(BasicOperationStatus.EXCEPTION.getLabel(), resp.getErrorMessage());
		assertFeedback(resp);
	}
}
