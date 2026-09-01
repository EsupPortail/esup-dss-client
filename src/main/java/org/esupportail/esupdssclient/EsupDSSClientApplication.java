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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import org.esupportail.esupdssclient.EsupDSSClientPreLoader.PreloaderMessage;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.api.plugin.InitializationMessage;
import org.esupportail.esupdssclient.dssclient.DssClientSetupDialog;
import org.esupportail.esupdssclient.dssclient.DssClientConnectionManager;
import org.esupportail.esupdssclient.flow.BasicFlowRegistry;
import org.esupportail.esupdssclient.flow.Flow;
import org.esupportail.esupdssclient.flow.FlowRegistry;
import org.esupportail.esupdssclient.flow.operation.BasicOperationFactory;
import org.esupportail.esupdssclient.generic.SCDatabase;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class EsupDSSClientApplication extends Application {

	private static final Logger logger = LoggerFactory.getLogger(EsupDSSClientApplication.class.getName());

	private DssClientConnectionManager dssClientConnectionManager;
	
	private AppConfig getConfig() {
		return EsupDSSClientLauncher.getConfig();
	}

	private Properties getProperties() {
		return EsupDSSClientLauncher.getProperties();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Platform.setImplicitExit(false);

		final StandaloneUIDisplay uiDisplay = new StandaloneUIDisplay();
		final OperationFactory operationFactory = new BasicOperationFactory();
		((BasicOperationFactory)operationFactory).setDisplay(uiDisplay);
		uiDisplay.setOperationFactory(operationFactory);
		
		final EsupDSSClientAPI api = buildAPI(uiDisplay, operationFactory);

		final UserPreferences prefs = new UserPreferences(getConfig().getApplicationName());
		dssClientConnectionManager = new DssClientConnectionManager(api, prefs, uiDisplay);
		dssClientConnectionManager.startIfConfigured();

		boolean systrayInitialized = false;
		if(api.getAppConfig().isEnableSystrayMenu()) {
			systrayInitialized = new SystrayMenu(operationFactory, api, prefs, dssClientConnectionManager).isInitialized();
		} else {
			logger.info("Systray menu is disabled.");
		}
		if (!systrayInitialized) {
			new SystrayFallbackWindow().show(primaryStage, api, operationFactory, prefs, dssClientConnectionManager);
		}
		if (!prefs.hasDssClientCredential()) {
			new DssClientSetupDialog(prefs, api.getAppConfig(), dssClientConnectionManager).show();
		}

		logger.info("Start finished");
	}

	private EsupDSSClientAPI buildAPI(final UIDisplay uiDisplay, final OperationFactory operationFactory) throws IOException {
		File apiHome = getConfig().getApiHome();
		SCDatabase db = null;
		if (apiHome != null) {
			File store = new File(apiHome, "store.xml");
			logger.info("Load database from " + store.getAbsolutePath());
			db = ProductDatabaseLoader.load(SCDatabase.class, store);
		} else {
			db = new SCDatabase();
		}
		final APIBuilder builder = new APIBuilder();
		final EsupDSSClientAPI api = builder.build(uiDisplay, getConfig(), getFlowRegistry(), db, operationFactory);
		notifyPreloader(builder.initPlugins(api, getProperties()));
		return api;
	}

	/**
	 * Returns the {@link FlowRegistry} to use to resolve {@link Flow}s.
	 * @return The {@link FlowRegistry} to use to resolve {@link Flow}s.
	 */
	protected FlowRegistry getFlowRegistry() {
		return new BasicFlowRegistry();
	}
	
	@Override
	public void stop() throws Exception {
		logger.info("Stopping application...");
		try {
			if(dssClientConnectionManager != null) {
				dssClientConnectionManager.stop();
				dssClientConnectionManager = null;
			}
		} catch (final Exception e) {
			logger.error("Cannot stop server", e);
		}
	}

	private void notifyPreloader(final List<InitializationMessage> messages) {
		for(final InitializationMessage message : messages) {
			final AlertType alertType;
			switch(message.getMessageType()) {
			case WARNING:
				alertType = AlertType.WARNING;
				break;
			default:
				throw new IllegalArgumentException("Unknown message type: " + message.getMessageType());	
			}
			final PreloaderMessage preloaderMessage = new PreloaderMessage(alertType, message.getTitle(),
					message.getHeaderText(), message.getContentText());
			notifyPreloader(preloaderMessage);
		}
	}
}
