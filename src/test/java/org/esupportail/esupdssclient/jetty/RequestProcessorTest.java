package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.server.Request;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RequestProcessorTest {

	private Map<String, String> parameters;
	private HttpServletResponse response;
	private RequestProcessor rp;
	private EsupDSSClientAPI api;
	private AppConfig appConfig;
	private Request request;
	private HttpServletRequest httpRequest;
	private Properties props;

	@SuppressWarnings("rawtypes")
	@Before
	public void init() throws IOException {
		parameters = new HashMap<>();
		response = mock(HttpServletResponse.class);
		doAnswer((Answer) invocation -> {
			String key = (String) invocation.getArguments()[0];
			String value = (String) invocation.getArguments()[1];
			parameters.put(key, value);
			return null;
		}).when(response).setHeader(anyString(), anyString());
		rp = new RequestProcessor();
		api = mock(EsupDSSClientAPI.class);
		appConfig = new AppConfig();
		when(api.getAppConfig()).thenReturn(appConfig);
		rp.setConfig(api);
		request = mock(Request.class);
		httpRequest = mock(HttpServletRequest.class);
		when(httpRequest.getRemoteHost()).thenReturn("127.0.0.1");
		when(response.getWriter()).thenReturn(new PrintWriter(System.out));
		props = new Properties();
	}
	
	@Test
	public void testCorsOriginProvided() throws IOException, ServletException {
		final String allowedOrigin = "https://esup-portail.org";
		final String allowedOriginProp = String.format("cors_allowed_origin=%s", allowedOrigin);
		props.load(new StringReader(allowedOriginProp));
		appConfig.loadFromProperties(props);
		rp.handle("/", request, httpRequest, response);
		assertEquals(allowedOrigin, parameters.get("Access-Control-Allow-Origin"));
		
	}

	@Test
	public void testCorsOriginNotProvided() throws IOException, ServletException {
		appConfig.loadFromProperties(new Properties());
		rp.handle("/", request, httpRequest, response);
		assertEquals("*", parameters.get("Access-Control-Allow-Origin"));
	}
	
}
