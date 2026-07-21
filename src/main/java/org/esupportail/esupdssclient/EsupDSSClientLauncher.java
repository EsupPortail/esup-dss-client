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
package org.esupportail.esupdssclient;

import com.sun.javafx.application.LauncherImpl;
import javafx.application.Application;
import org.apache.commons.lang3.StringUtils;
import org.esupportail.esupdssclient.api.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EsupDSSClientLauncher {

	private static final Logger logger = LoggerFactory.getLogger(EsupDSSClientLauncher.class.getName());

	private static AppConfig config;

	private static Properties props;

	private static GlobalConfigurer globalConfigurer;

	public static void main(String[] args) throws Exception {
		EsupDSSClientLauncher launcher = new EsupDSSClientLauncher();
		launcher.launch(args);
	}

	protected void launch(String[] args) throws IOException {
		props = loadProperties();
		loadAppConfig(props);

		config.initDefaultProduct(props);
		if (!SingleInstanceGuard.acquire(config.getApplicationName())) {
			return;
		}

		globalConfigurer = new GlobalConfigurer(config, new UserPreferences(config.getApplicationName()));
		if(StringUtils.isNotBlank(globalConfigurer.getDriver())) {
			props.setProperty("opensc_command_module", globalConfigurer.getDriver());
		}
		LauncherImpl.launchApplication(getApplicationClass(), EsupDSSClientPreLoader.class, args);
	}

	public static AppConfig getConfig() {
		return config;
	}

	public static Properties getProperties() {
		return props;
	}

	public static GlobalConfigurer getGlobalConfigurer() {
		return globalConfigurer;
	}

	private Properties loadProperties() throws IOException {

		Properties props = new Properties();
		loadPropertiesFromClasspath(props);
		return props;

	}

	private void loadPropertiesFromClasspath(Properties props) throws IOException {
		InputStream configFile = EsupDSSClientApplication.class.getClassLoader().getResourceAsStream("application.properties");
		if (configFile != null) {
			props.load(configFile);
		}
	}

	/**
	 * Load the properties from the properties file.
	 * 
	 * @param props
	 * @return
	 */
	public final void loadAppConfig(Properties props) {
		config = createAppConfig();
		config.loadFromProperties(props);
	}

	protected AppConfig createAppConfig() {
		return new AppConfig();
	}

	/**
	 * Returns the JavaFX {@link Application} class to launch.
	 * 
	 * @return The JavaFX {@link Application} class to launch.
	 */
	protected Class<? extends Application> getApplicationClass() {
		return EsupDSSClientApplication.class;
	}
}
