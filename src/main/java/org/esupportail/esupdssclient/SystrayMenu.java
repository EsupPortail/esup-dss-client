/**
 * © Nowina Solutions, 2015-2017
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

import javafx.application.Platform;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.SystrayMenuItem;
import org.esupportail.esupdssclient.api.flow.FutureOperationInvocation;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.systray.SystrayMenuInitializer;
import org.esupportail.esupdssclient.view.core.NonBlockingUIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SystrayMenu {

	private static final Logger logger = LoggerFactory.getLogger(SystrayMenu.class.getName());

	public SystrayMenu(OperationFactory operationFactory, EsupDSSClientAPI api, UserPreferences prefs) {
		logger.info("Starting systray menu");
		final ResourceBundle resources = ResourceBundle.getBundle("bundles/api");

		final List<SystrayMenuItem> extensionSystrayMenuItems = api.getExtensionSystrayMenuItems();
		final SystrayMenuItem[] systrayMenuItems = new SystrayMenuItem[extensionSystrayMenuItems.size() + 2];

		systrayMenuItems[0] = createAboutSystrayMenuItem(operationFactory, api, resources);
		systrayMenuItems[1] = createPreferencesSystrayMenuItem(operationFactory, api, prefs, resources);

		int i = 2;
		for(final SystrayMenuItem systrayMenuItem : extensionSystrayMenuItems) {
			systrayMenuItems[i++] = systrayMenuItem;
		}

		final SystrayMenuItem exitMenuItem = createExitSystrayMenuItem(resources);

		final String tooltip = api.getAppConfig().getApplicationName();
		final URL trayIconURL = this.getClass().getResource("/tray-icon.png");
		try {
			switch(api.getEnvironmentInfo().getOs()) {
			case WINDOWS:
			case MACOSX:
				// Use reflection to avoid wrong initialization issues
				Class.forName("org.esupportail.esupdssclient.systray.AWTSystrayMenuInitializer")
					.asSubclass(SystrayMenuInitializer.class).getDeclaredConstructor().newInstance()
					.init(tooltip, trayIconURL, operationFactory, exitMenuItem, systrayMenuItems);
				break;
			case LINUX:
				// Use reflection to avoid wrong initialization issues
				Class.forName("org.esupportail.esupdssclient.systray.DorkboxSystrayMenuInitializer")
					.asSubclass(SystrayMenuInitializer.class).getDeclaredConstructor().newInstance()
					.init(tooltip, trayIconURL, operationFactory, exitMenuItem, systrayMenuItems);
				break;
			case NOT_RECOGNIZED:
				logger.warn("System tray is currently not supported for NOT_RECOGNIZED OS.");
				break;
			default:
				throw new IllegalArgumentException("Unhandled value: " + api.getEnvironmentInfo().getOs());
			}
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException e) {
			logger.error("Cannot initialize systray menu", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	private SystrayMenuItem createAboutSystrayMenuItem(final OperationFactory operationFactory, final EsupDSSClientAPI api,
			final ResourceBundle resources) {
		return new SystrayMenuItem() {
			@Override
			public String getLabel() {
				return resources.getString("systray.menu.about");
			}
			
			@Override
			public FutureOperationInvocation<Void> getFutureOperationInvocation() {
				return new FutureOperationInvocation<Void>() {
					@Override
					public OperationResult<Void> call(OperationFactory operationFactory) {
						return operationFactory.getOperation(NonBlockingUIOperation.class, "/fxml/about.fxml",
								api.getAppConfig().getApplicationName(), api.getAppConfig().getApplicationVersion(),
								resources).perform();
					}
				};
			}
		};
	}

	private SystrayMenuItem createPreferencesSystrayMenuItem(final OperationFactory operationFactory,
															 final EsupDSSClientAPI api, final UserPreferences prefs, final ResourceBundle resources) {
		return new SystrayMenuItem() {
			@Override
			public String getLabel() {
				return resources.getString("systray.menu.preferences");
			}
			
			@Override
			public FutureOperationInvocation<Void> getFutureOperationInvocation() {
				return new FutureOperationInvocation<Void>() {
					@Override
					public OperationResult<Void> call(OperationFactory operationFactory) {
						final GlobalConfigurer proxyConfigurer = new GlobalConfigurer(api.getAppConfig(), prefs);

						return operationFactory.getOperation(NonBlockingUIOperation.class, "/fxml/preferences.fxml",
								proxyConfigurer, prefs, !api.getAppConfig().isUserPreferencesEditable()).perform();
					}
				};
			}
		};
	}

	private SystrayMenuItem createExitSystrayMenuItem(final ResourceBundle resources) {
		return new SystrayMenuItem() {
			@Override
			public String getLabel() {
				return resources.getString("systray.menu.exit");
			}
			
			@Override
			public FutureOperationInvocation<Void> getFutureOperationInvocation() {
				return new FutureOperationInvocation<Void>() {
					@Override
					public OperationResult<Void> call(OperationFactory operationFactory) {
						logger.info("Exiting...");
						Platform.exit();
						System.exit(0);
						return new OperationResult<Void>((Void) null);
					}
				};
			}
		};
	}
}
