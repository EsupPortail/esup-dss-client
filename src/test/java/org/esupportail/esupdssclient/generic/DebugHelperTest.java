package org.esupportail.esupdssclient.generic;

import org.apache.commons.io.FileUtils;
import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Feedback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DebugHelperTest {

	private DebugHelper dh;
	private File dummyApiHome;
	private EsupDSSClientAPI esupDSSClientAPI;
	private static final Logger logger = LoggerFactory.getLogger(DebugHelperTest.class); 

	@Before
	public void setup() {
		Properties properties = new Properties();
		properties.setProperty("os.version", "27");
		properties.setProperty("os.name", "Fedora Linux");
		properties.setProperty("java.vendor", "Oracle");
		properties.setProperty("os.arch", "x86_64");

		AppConfig appConfig = mock(AppConfig.class);
		dummyApiHome = Paths.get("tmp", "dummy_api_home").toFile();
		assertTrue(dummyApiHome.mkdirs());
		assertTrue(dummyApiHome.isDirectory());
		when(appConfig.getApiHome()).thenReturn(dummyApiHome);
		when(appConfig.isEnablePopUps()).thenReturn(true);
		dh = spy(new DebugHelper());
		when(dh.getConfig()).thenReturn(appConfig);
		when(dh.getProperties()).thenReturn(properties);
		esupDSSClientAPI = mock(EsupDSSClientAPI.class);
		when(esupDSSClientAPI.getAppConfig()).thenReturn(appConfig);
		
	}

	@After
	public void cleanup() throws IOException {
		if (dummyApiHome.exists()) {
			FileUtils.forceDelete(Paths.get("tmp").toFile());
		}
	}

	@Test
	public void testCollectDebugData() {

		Feedback feedback = dh.collectDebugData(new EsupDSSClientException("Dummy exception"));
		assertEquals("LINUX", feedback.getInfo().getOs().name());
		assertEquals(EsupDSSClientException.class, feedback.getException().getClass());
	}

	@Test
	public void testBuildDebugFileWhenLogFound() throws IOException {
		Path apiLogPath = Paths.get(dummyApiHome.getAbsolutePath(), "esupdssclient.log");
		File apiLog = apiLogPath.toFile();
		apiLog.createNewFile();
		assertTrue(apiLog.exists());
		String dummyLogContent = "Dummy log content";
		Files.write(apiLogPath, dummyLogContent.getBytes());
		dh.buildDebugFile();
		Path apiDebugPath = Paths.get(dummyApiHome.getAbsolutePath(), dh.getDebugFileName());
		assertTrue(apiDebugPath.toFile().exists());
	}

	@Test
	public void testBuildDebugFileWhenLogNotFound() throws IOException {
		dh.buildDebugFile();
		Path apiDebugPath = Paths.get(dummyApiHome.getAbsolutePath(), dh.getDebugFileName());
		assertTrue(apiDebugPath.toFile().exists());
	}

	@Test
	public void testAppendFeedbackData() throws JAXBException, IOException {
		Path apiDebugPath = Paths.get(dummyApiHome.getAbsolutePath(), dh.getDebugFileName());
		Feedback feedback = new Feedback();
		feedback.setApiVersion("99");
		FileUtils.write(apiDebugPath.toFile(),
				String.format("esup test log content\n"), "utf-8");
		File readyFile = dh.appendFeedbackData(apiDebugPath, feedback);
		File expectedFile = new File(
				this.getClass().getClassLoader().getResource("DebugHelperTest.txt").getPath());
		assertTrue(FileUtils.contentEqualsIgnoreEOL(expectedFile, readyFile, "utf-8"));
	}

	@Test
	public void testProcessError() throws IOException, JAXBException {
		doNothing().when(dh).showDebugFileInExplorer(any(Path.class), any(Feedback.class));
		dh.processError(new EsupDSSClientException("Dummy Exception"));
		verify(dh, times(1)).collectDebugData(any(Throwable.class));
		verify(dh, times(1)).buildDebugFile();
		verify(dh, times(1)).appendFeedbackData(any(Path.class), any(Feedback.class));
		verify(dh, times(1)).showDebugFileInExplorer(any(Path.class), any(Feedback.class));
	}
	
}
