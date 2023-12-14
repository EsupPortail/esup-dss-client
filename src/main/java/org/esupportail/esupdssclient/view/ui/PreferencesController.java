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
package org.esupportail.esupdssclient.view.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.apache.commons.lang.StringUtils;
import org.esupportail.esupdssclient.EsupDSSClientApplication;
import org.esupportail.esupdssclient.EsupDSSClientLauncher;
import org.esupportail.esupdssclient.GlobalConfigurer;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.EnvironmentInfo;
import org.esupportail.esupdssclient.api.OS;
import org.esupportail.esupdssclient.flow.StageHelper;
import org.esupportail.esupdssclient.view.core.AbstractUIOperationController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;

public class PreferencesController extends AbstractUIOperationController<Void> implements Initializable {

	@FXML
	private Button ok;

	@FXML
	private Button cancel;

	@FXML
	private Button reset;

	@FXML
	private Label title;

	@FXML
	private Label useSystemProxyLabel;
	
	@FXML
	private CheckBox useSystemProxy;

	@FXML
	private TextField driver;

	@FXML
	private TextField certId;

	@FXML
	private TextField proxyServer;

	@FXML
	private TextField proxyPort;

	@FXML
	private CheckBox proxyAuthentication;

	@FXML
	private TextField proxyUsername;
	
	@FXML
	private CheckBox useHttps;

	@FXML
	private PasswordField proxyPassword;
	
	private UserPreferences userPreferences;
	
	private BooleanProperty readOnly;

	private Properties props;
	
	private static final boolean isWindows;
	
	static {
		isWindows = EnvironmentInfo.buildFromSystemProperties(System.getProperties()).getOs().equals(OS.WINDOWS);
	}
	
	private void init(final GlobalConfigurer proxyConfigurer) {
		StageHelper.getInstance().setTitle("Esup-DSS-Client - " + title.getText());
		if(isWindows) {
			useSystemProxy.setSelected(proxyConfigurer.isUseSystemProxy());
		} else {
			useSystemProxy.setVisible(false);
			useSystemProxy.setManaged(false);
			useSystemProxyLabel.setVisible(false);
			useSystemProxyLabel.setManaged(false);
		}
		driver.setText(proxyConfigurer.getDriver());
		certId.setText(proxyConfigurer.getCertId());
		useHttps.setSelected(proxyConfigurer.isProxyUseHttps());
		proxyServer.setText(proxyConfigurer.getProxyServer());
		final Integer proxyPortInt = proxyConfigurer.getProxyPort();
		proxyPort.setText((proxyPortInt != null) ? proxyPortInt.toString() : "");
		proxyAuthentication.setSelected(proxyConfigurer.isProxyAuthentication());
		proxyUsername.setText(proxyConfigurer.getProxyUsername());
		proxyPassword.setText(proxyConfigurer.getProxyPassword());
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		props =  loadPropertiesFromClasspath();
		readOnly = new SimpleBooleanProperty(false);
		ok.disableProperty().bind(readOnly);
		reset.disableProperty().bind(readOnly);
		useSystemProxy.disableProperty().bind(readOnly);
		
		proxyServer.disableProperty().bind(
				readOnly.or(
						useSystemProxy.selectedProperty()));
		
		proxyPort.disableProperty().bind(
				proxyServer.textProperty().length().lessThanOrEqualTo(0).or(
						proxyServer.disabledProperty()));
		
		proxyAuthentication.disableProperty().bind(
				readOnly.or(
						proxyServer.textProperty().length().lessThanOrEqualTo(0).and(
								useSystemProxy.selectedProperty().not())));
		
		useHttps.disableProperty().bind(
				readOnly.or(
						proxyServer.textProperty().length().lessThanOrEqualTo(0).and(
								useSystemProxy.selectedProperty().not())));
		
		proxyUsername.disableProperty().bind(proxyAuthentication.disabledProperty().or(
						proxyAuthentication.selectedProperty().not()));
		
		proxyPassword.disableProperty().bind(proxyAuthentication.disabledProperty().or(
				proxyAuthentication.selectedProperty().not()));
		
		ok.setOnAction((evt) -> {
			final Integer port;
			try {
				if(proxyPort.isDisabled()) {
					port = null;
				} else {
					port = Integer.parseInt(proxyPort.getText());
				}
			} catch(NumberFormatException e) {
				proxyPort.setTooltip(new Tooltip(resources.getString("preferences.controller.invalid.port")));
				proxyPort.setStyle("-fx-text-box-border: red; -fx-focus-color: red;");
	    		return;
			}
			userPreferences.setDriver(driver.getText());
			if(StringUtils.isNotBlank(userPreferences.getDriver())) {
				EsupDSSClientLauncher.getProperties().setProperty("opensc_command_module", userPreferences.getDriver());
			} else {
				String module = "";
				if(StringUtils.isNotBlank(props.getProperty("opensc_command_module"))) module = props.getProperty("opensc_command_module");
				EsupDSSClientLauncher.getProperties().setProperty("opensc_command_module", module);
			}
			userPreferences.setCertId(certId.getText());
			if(StringUtils.isNotBlank(userPreferences.getCertId())) {
				EsupDSSClientLauncher.getProperties().setProperty("opensc_command_cert_id", userPreferences.getCertId());
			} else {
				String certId = "";
				if(StringUtils.isNotBlank(props.getProperty("opensc_command_cert_id"))) certId = props.getProperty("opensc_command_cert_id");
				EsupDSSClientLauncher.getProperties().setProperty("opensc_command_cert_id", certId);
			}
			userPreferences.setUseSystemProxy(useSystemProxy.isDisabled() ? null : useSystemProxy.isSelected());
			userPreferences.setProxyServer(proxyServer.isDisabled() ? null : proxyServer.getText());
			userPreferences.setProxyPort(port);
			userPreferences.setProxyAuthentication(proxyAuthentication.isDisabled() ? null : proxyAuthentication.isSelected());
			userPreferences.setProxyUsername(proxyUsername.isDisabled() ? null : proxyUsername.getText());
			userPreferences.setProxyPassword(proxyPassword.isDisabled() ? null : proxyPassword.getText());
			userPreferences.setProxyUseHttps(useHttps.isDisabled() ? null : useHttps.isSelected());
			
			EsupDSSClientLauncher.getGlobalConfigurer().updateValues(EsupDSSClientLauncher.getConfig(), userPreferences);

			signalEnd(null);
		});
		cancel.setOnAction((e) -> {
			signalEnd(null);
		});
		reset.setOnAction((e) -> {
			userPreferences.clear();
			EsupDSSClientLauncher.getGlobalConfigurer().updateValues(EsupDSSClientLauncher.getConfig(), userPreferences);
			signalEnd(null);
		});
	}

	@Override
	public void init(Object... params) {
		final GlobalConfigurer proxyConfigurer = (GlobalConfigurer) params[0];
		init(proxyConfigurer);
		this.userPreferences = (UserPreferences) params[1];
		this.readOnly.set((boolean) params[2]);
	}

	private Properties loadPropertiesFromClasspath() {
		Properties props = new Properties();
		InputStream configFile = EsupDSSClientApplication.class.getClassLoader().getResourceAsStream("application.properties");
		if (configFile != null) {
            try {
                props.load(configFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
		return props;
	}
}
