package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RequestProcessorTest {

	private Map<String, String> parameters;
	private Response response;
	private RequestProcessor rp;
	private EsupDSSClientAPI api;
	private AppConfig appConfig;
	private Request request;
	private Properties props;
	private Callback callback;

	@SuppressWarnings("rawtypes")
	@Before
	public void init() throws IOException {
		parameters = new HashMap<>();
		response = mock(Response.class);
		doAnswer((Answer) invocation -> {
			String key = (String) invocation.getArguments()[0];
			String value = (String) invocation.getArguments()[1];
			parameters.put(key, value);
			return null;
		}).when(response).getHeaders().add(anyString(), anyString());
		rp = new RequestProcessor();
		api = mock(EsupDSSClientAPI.class);
		appConfig = new AppConfig();
		when(api.getAppConfig()).thenReturn(appConfig);
		rp.setConfig(api);
		request = mock(Request.class);
		callback = mock(Callback.class);
		when(Request.getRemoteAddr(request)).thenReturn("127.0.0.1");
		when(Response.getOriginalResponse(response)).thenReturn(new Response.Wrapper(request, response));
		props = new Properties();
	}
	
	@Test
	public void testCorsOriginProvided() throws Exception {
		final String allowedOrigin = "https://esup-portail.org";
		final String allowedOriginProp = String.format("cors_allowed_origin=%s", allowedOrigin);
		props.load(new StringReader(allowedOriginProp));
		appConfig.loadFromProperties(props);
		rp.handle(request, response, callback);
		assertEquals(allowedOrigin, parameters.get("Access-Control-Allow-Origin"));
		
	}

	@Test
	public void testCorsOriginNotProvided() throws Exception {
		appConfig.loadFromProperties(new Properties());
		rp.handle(request, response, callback);
		assertEquals("*", parameters.get("Access-Control-Allow-Origin"));
	}
	
}
