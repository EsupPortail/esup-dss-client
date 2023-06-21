/**
 * © Nowina Solutions, 2018-2018
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
package org.esupportail.esupdssclient.api;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfigurationManagerTest {

	public static final Logger logger = LoggerFactory.getLogger(ConfigurationManagerTest.class);

	private String appName = "EsupDSSClient";
	private String companyName = "DUMMY";
	private ConfigurationManager cm = null;

	@Before
	public void setup() {
		cm = spy(ConfigurationManager.class);
		when(cm.getWindowsAppDataPath()).thenReturn(Paths.get("TestFS", "Windows").toString());
		when(cm.getCompanyName()).thenReturn(companyName);
		when(cm.getUserHome()).thenReturn(Paths.get("TestFS", "Generic").toString());
	}

	@After
	public void cleanup() throws IOException {
		FileUtils.forceDelete(Paths.get("TestFS").toFile());
		cm = null;
	}

	@Test
	public void testCorrectConfigMethodIsCalled() {
		try {
			when(cm.getOs()).thenReturn("Linux");
			cm.manageConfiguration(appName);
			verify(cm, times(1)).manageCommonConfiguration(appName);
			when(cm.getOs()).thenReturn("Windows 10");
			cm.manageConfiguration(appName);
			verify(cm, times(1)).manageWindowsConfiguration(appName);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	@Test
	public void testWindowsConfigFolderIsCorrectlyCreated() {

		File configFolder = null;

		Path companyFolderPath = cm.getWindowsConfigPath();
		Path configFolderPath = Paths.get(companyFolderPath.toString(), appName);
		try {
			configFolder = cm.createWindowsConfigurationFolder(appName);
			assertEquals(0, configFolderPath.compareTo(configFolder.toPath()));
		} finally {
			File companyFolder = companyFolderPath.toFile();
			if (configFolder.exists()) {
				configFolder.delete();
			}
			if (companyFolder.exists()) {
				companyFolder.delete();
			}
		}
	}

	@Test
	public void testCommonConfigFolderIsCorrectlyCreated() {
		File configFolder = null;
		try {
			configFolder = cm.manageCommonConfiguration(appName);
			assertEquals(0, configFolder.compareTo(cm.getCommonConfigPath(appName).toFile()));
		} finally {
			if (configFolder.exists()) {
				configFolder.delete();
			}
		}
	}

	@Test
	public void testConfigFolderMoving() throws IOException {
		File sourceFolder = Paths.get(cm.getUserHome(), "source_folder").toFile();
		File destFolder = Paths.get(cm.getUserHome(), "dest_folder").toFile();
		File backupFolder = null;
		try {
			sourceFolder.mkdirs();
			File configFile1 = Paths.get(sourceFolder.getAbsolutePath(), "config_file_1").toFile();
			configFile1.createNewFile();
			File configFile2 = Paths.get(sourceFolder.getAbsolutePath(), "config_file_2").toFile();
			configFile2.createNewFile();
			destFolder.mkdirs();
			backupFolder = cm.moveExistingConfigFolder(sourceFolder, destFolder);
			assertTrue(Paths.get(destFolder.getAbsolutePath(), configFile1.getName()).toFile().exists());
			assertTrue(Paths.get(destFolder.getAbsolutePath(), configFile2.getName()).toFile().exists());
			assertFalse(Paths.get(sourceFolder.getAbsolutePath(), configFile1.getName()).toFile().exists());
			assertFalse(Paths.get(sourceFolder.getAbsolutePath(), configFile2.getName()).toFile().exists());
		} finally {
			if (sourceFolder.exists()) {
				FileUtils.forceDelete(sourceFolder);
			}
			if (destFolder.exists()) {
				FileUtils.forceDelete(destFolder);
			}
			if (backupFolder.exists()) {
				FileUtils.forceDelete(backupFolder);
			}
		}
	}

	@Test
	public void testLinuxFirstLaunch() throws IOException {
		when(cm.getOs()).thenReturn("Linux");
		assertFalse(cm.getCommonConfigPath(appName).toFile().exists());
		AppConfig config = spy(AppConfig.class);
		when(config.getApplicationName()).thenReturn("EsupDSSClient");
		when(config.getConfigurationManager()).thenReturn(cm);
		File configApiHome = config.getApiHome();
		verify(cm, times(1)).manageCommonConfiguration(appName);
		verify(cm, never()).manageWindowsConfiguration(appName);
		assertTrue(configApiHome.exists());
		assertEquals(0, configApiHome.compareTo(cm.getCommonConfigPath(appName).toFile()));

	}

	@Test
	public void testLinuxCMNotCalledOnSubseqLaunches() throws IOException {
		// Launch 1st time
		when(cm.getOs()).thenReturn("Linux");
		AppConfig config = spy(AppConfig.class);
		when(config.getApplicationName()).thenReturn("EsupDSSClient");
		when(config.getConfigurationManager()).thenReturn(cm);
		File configApiHome = config.getApiHome();
		configApiHome = config.getApiHome();
		// Verify ConfigurationManager is not called on subsequent launches
		assertTrue(configApiHome.exists());
		assertEquals(0, configApiHome.compareTo(cm.getCommonConfigPath(appName).toFile()));
		verify(cm, times(1)).manageConfiguration(appName); // cm should have been called only once (the first time)

	}

	@Test
	public void testWinExistingGenericConfig() throws IOException {
		when(cm.getOs()).thenReturn("Windows");
		File genericConfigFolder = cm.getCommonConfigPath(appName).toFile();
		 genericConfigFolder.mkdirs();
		File existingConfigFile = Paths.get(genericConfigFolder.getPath(), "existing_config_file").toFile();
		assertTrue(existingConfigFile.createNewFile());
		AppConfig config = spy(AppConfig.class);
		when(config.getApplicationName()).thenReturn("EsupDSSClient");
		when(config.getConfigurationManager()).thenReturn(cm);
		File configApiHome = config.getApiHome();
		verify(cm, times(1)).moveExistingConfigFolder(any(File.class), any(File.class));
		assertEquals(0, configApiHome.compareTo(Paths.get(cm.getWindowsConfigPath().toString(), appName).toFile()));
		assertTrue(cm.getConfigBackupPath(genericConfigFolder).toFile().exists());
		assertFalse(genericConfigFolder.exists());
		assertTrue(Paths.get(cm.getConfigBackupPath(genericConfigFolder).toString(), existingConfigFile.getName())
				.toFile().exists());
		assertTrue(Paths.get(configApiHome.getAbsolutePath(), existingConfigFile.getName()).toFile().exists());
	}
}
