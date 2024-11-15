package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.internal.ResponseHttpFields;
import org.eclipse.jetty.util.Callback;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RequestProcessorTest {

	private Response response;
	private RequestProcessor rp;
	private EsupDSSClientAPI api;
	private AppConfig appConfig;
	private Request request;
	private Properties props;
	private Callback callback;

	@SuppressWarnings("rawtypes")
	@Before
	public void init() {
		api = mock(EsupDSSClientAPI.class);
		request = mock(Request.class);
		callback = mock(Callback.class);
		response = mock(Response.class);
		when(response.getHeaders()).thenReturn(new ResponseHttpFields());
		when(request.getHeaders()).thenReturn(new ResponseHttpFields());
		rp = new RequestProcessor();
		appConfig = new AppConfig();
		when(api.getAppConfig()).thenReturn(appConfig);
		rp.setConfig(api);
		HttpURI mockHttpURI = mock(HttpURI.class);
		when(mockHttpURI.getCanonicalPath()).thenReturn("/");
		when(request.getHttpURI()).thenReturn(mockHttpURI);
		Context mockContext = mock(Context.class);
		when(request.getContext()).thenReturn(mockContext);
		when(mockContext.getPathInContext(anyString())).thenReturn("/");
		ConnectionMetaData mockConnectionMetaData = mock(ConnectionMetaData.class);
		when(mockConnectionMetaData.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 9795));
		when(request.getConnectionMetaData()).thenReturn(mockConnectionMetaData);
		when(Request.getPathInContext(request)).thenReturn("/");
		props = new Properties();
	}
	
	@Test
	public void testCorsOriginProvided() throws Exception {
		final String allowedOrigin = "https://esup-portail.org";
		final String allowedOriginProp = String.format("cors_allowed_origin=%s", allowedOrigin);
		props.load(new StringReader(allowedOriginProp));
		appConfig.loadFromProperties(props);
		rp.handle(request, response, callback);
		assertEquals(allowedOrigin, response.getHeaders().get("Access-Control-Allow-Origin"));
		
	}

	@Test
	public void testCorsOriginNotProvided() throws Exception {
		appConfig.loadFromProperties(new Properties());
		rp.handle(request, response, callback);
		assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
	}
	
}
