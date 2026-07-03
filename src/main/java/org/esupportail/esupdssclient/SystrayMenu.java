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
import org.esupportail.esupdssclient.api.OS;
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
import java.util.Locale;
import java.util.ResourceBundle;

public class SystrayMenu {

	private static final Logger logger = LoggerFactory.getLogger(SystrayMenu.class.getName());
	private static final String AWT_INITIALIZER_CLASS = "org.esupportail.esupdssclient.systray.AWTSystrayMenuInitializer";
	private static final String DORKBOX_INITIALIZER_CLASS = "org.esupportail.esupdssclient.systray.DorkboxSystrayMenuInitializer";
	private static final String DORKBOX_TRAY_TYPE_PROPERTY = "esupdssclient.dorkbox.trayType";
	private static final String DORKBOX_TRAY_NAME_PROPERTY = "esupdssclient.dorkbox.trayName";

	private enum SystrayBackend {
		AUTO, AWT, DORKBOX, DISABLED
	}

	private final boolean initialized;

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
		System.setProperty(DORKBOX_TRAY_TYPE_PROPERTY, api.getAppConfig().getDorkboxTrayType());
		System.setProperty(DORKBOX_TRAY_NAME_PROPERTY, api.getAppConfig().getDorkboxTrayName());
		this.initialized = initializeSystrayMenu(api.getAppConfig().getSystrayBackend(), api.getEnvironmentInfo().getOs(), tooltip,
				trayIconURL, operationFactory, exitMenuItem, systrayMenuItems);
	}

	public boolean isInitialized() {
		return initialized;
	}

	private boolean initializeSystrayMenu(final String configuredBackend, final OS os, final String tooltip,
			final URL trayIconURL, final OperationFactory operationFactory, final SystrayMenuItem exitMenuItem,
			final SystrayMenuItem... systrayMenuItems) {
		final SystrayBackend backend = parseSystrayBackend(configuredBackend);
		if (backend == SystrayBackend.DISABLED) {
			logger.info("Systray menu backend is disabled by configuration.");
			return false;
		}

		logger.info("Initializing systray menu with backend '{}' for OS '{}'", backend, os);
		final boolean initialized;
		switch (backend) {
		case AUTO:
			initialized = initializeAutoSystrayMenu(os, tooltip, trayIconURL, operationFactory, exitMenuItem,
					systrayMenuItems);
			break;
		case AWT:
			initialized = initializeWithBackend("AWT", AWT_INITIALIZER_CLASS, tooltip, trayIconURL, operationFactory,
					exitMenuItem, systrayMenuItems);
			break;
		case DORKBOX:
			initialized = initializeWithBackend("Dorkbox", DORKBOX_INITIALIZER_CLASS, tooltip, trayIconURL,
					operationFactory, exitMenuItem, systrayMenuItems);
			break;
		default:
			initialized = false;
			break;
		}

		if (!initialized) {
			logger.warn("Systray menu could not be initialized. Application will continue without systray menu.");
		}
		return initialized;
	}

	private SystrayBackend parseSystrayBackend(final String configuredBackend) {
		if (configuredBackend == null) {
			return SystrayBackend.AUTO;
		}
		try {
			return SystrayBackend.valueOf(configuredBackend.trim().toUpperCase(Locale.ROOT));
		} catch (final IllegalArgumentException e) {
			logger.warn("Invalid systray backend '{}'. Falling back to 'auto'.", configuredBackend);
			return SystrayBackend.AUTO;
		}
	}

	private boolean initializeAutoSystrayMenu(final OS os, final String tooltip, final URL trayIconURL,
			final OperationFactory operationFactory, final SystrayMenuItem exitMenuItem,
			final SystrayMenuItem... systrayMenuItems) {
		switch (os) {
		case WINDOWS:
		case MACOSX:
			return initializeWithBackend("AWT", AWT_INITIALIZER_CLASS, tooltip, trayIconURL, operationFactory,
					exitMenuItem, systrayMenuItems);
		case LINUX:
			if (initializeWithBackend("Dorkbox", DORKBOX_INITIALIZER_CLASS, tooltip, trayIconURL, operationFactory,
					exitMenuItem, systrayMenuItems)) {
				return true;
			}
			logger.warn("Dorkbox systray initialization failed. Trying AWT fallback.");
			return initializeWithBackend("AWT", AWT_INITIALIZER_CLASS, tooltip, trayIconURL, operationFactory,
					exitMenuItem, systrayMenuItems);
		case NOT_RECOGNIZED:
			logger.warn("System tray is currently not supported for NOT_RECOGNIZED OS.");
			return false;
		default:
			logger.warn("Unhandled OS for systray menu: {}", os);
			return false;
		}
	}

	private boolean initializeWithBackend(final String backendName, final String initializerClassName,
			final String tooltip, final URL trayIconURL, final OperationFactory operationFactory,
			final SystrayMenuItem exitMenuItem, final SystrayMenuItem... systrayMenuItems) {
		try {
			// Use reflection to avoid wrong initialization issues on platforms where a backend is not used.
			final SystrayMenuInitializer initializer = Class.forName(initializerClassName)
					.asSubclass(SystrayMenuInitializer.class).getDeclaredConstructor().newInstance();
			final boolean initialized = initializer.init(tooltip, trayIconURL, operationFactory, exitMenuItem,
					systrayMenuItems);
			if (initialized) {
				logger.info("Systray menu initialized with {} backend.", backendName);
			} else {
				logger.warn("{} systray backend reported that system tray is not available.", backendName);
			}
			return initialized;
		} catch (final Throwable e) {
			final Throwable cause = (e instanceof InvocationTargetException
					&& ((InvocationTargetException) e).getTargetException() != null)
					? ((InvocationTargetException) e).getTargetException()
					: e;
			logger.error("Cannot initialize systray menu with {} backend", backendName, cause);
			return false;
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
