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
package org.esupportail.esupdssclient.systray;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import org.esupportail.esupdssclient.api.SystrayMenuItem;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link SystrayMenuInitializer} using
 * <a href="https://github.com/dorkbox/SystemTray">SystemTray from Dorkbox</a>.
 * 
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class DorkboxSystrayMenuInitializer implements SystrayMenuInitializer {

	private static final Logger logger = LoggerFactory.getLogger(DorkboxSystrayMenuInitializer.class.getName());
	private static final String DORKBOX_TRAY_TYPE_PROPERTY = "esupdssclient.dorkbox.trayType";
	private static final String DORKBOX_TRAY_NAME_PROPERTY = "esupdssclient.dorkbox.trayName";
	private static final String DEFAULT_DORKBOX_TRAY_NAME = "org.esupportail.esupdssclient";
	private static final String APPINDICATOR_EXTENSION = "appindicatorsupport@rgcjonas.gmail.com";
	private static final String UBUNTU_APPINDICATOR_EXTENSION = "ubuntu-appindicators@ubuntu.com";

	public DorkboxSystrayMenuInitializer() {
		super();
	}

	@Override
	public boolean init(final String tooltip, final URL trayIconURL, final OperationFactory operationFactory,
		final SystrayMenuItem exitMenuItem, final SystrayMenuItem... systrayMenuItems) {
		logger.info("Starting dorkbox");
		if (!configureTrayType()) {
			return false;
		}
		final String trayName = getTrayName();
		logger.info("Using Dorkbox tray name '{}'.", trayName);
		SystemTray.DEBUG = true;
		final SystemTray systemTray = SystemTray.get(trayName);
		if (systemTray == null) {
			logger.warn("System tray is currently not supported.");
			return false;
		}
		logger.info("Dorkbox selected {} tray type.", systemTray.getType());

		systemTray.setImage(trayIconURL);

		final Menu menu = systemTray.getMenu();
		for(final SystrayMenuItem systrayMenuItem : systrayMenuItems) {
			menu.add(new MenuItem(systrayMenuItem.getLabel(),
					(e) -> systrayMenuItem.getFutureOperationInvocation().call(operationFactory)));
		}
		
		menu.add(new MenuItem(exitMenuItem.getLabel(),
				(e) -> exitMenuItem.getFutureOperationInvocation().call(operationFactory)));
		return true;
	}

	private String getTrayName() {
		final String configuredTrayName = System.getProperty(DORKBOX_TRAY_NAME_PROPERTY, DEFAULT_DORKBOX_TRAY_NAME);
		if (configuredTrayName == null || configuredTrayName.trim().isEmpty()) {
			logger.warn("Invalid dorkbox_tray_name '{}'. Falling back to '{}'.", configuredTrayName,
					DEFAULT_DORKBOX_TRAY_NAME);
			return DEFAULT_DORKBOX_TRAY_NAME;
		}
		return configuredTrayName.trim();
	}

	private boolean configureTrayType() {
		final String configuredTrayType = System.getProperty(DORKBOX_TRAY_TYPE_PROPERTY, "auto");
		final SystemTray.TrayType trayType = parseTrayType(configuredTrayType);
		if (trayType == null) {
			return false;
		}
		SystemTray.FORCE_TRAY_TYPE = trayType;
		if (trayType == SystemTray.TrayType.AutoDetect) {
			logger.info("Dorkbox tray type auto-detection is enabled.");
		} else {
			logger.info("Forcing Dorkbox {} tray type.", trayType);
		}
		return true;
	}

	private SystemTray.TrayType parseTrayType(final String configuredTrayType) {
		if (configuredTrayType == null) {
			return SystemTray.TrayType.AutoDetect;
		}
		switch (configuredTrayType.trim().toLowerCase(Locale.ROOT)) {
		case "auto":
		case "autodetect":
			if (isGnomeWaylandSession()) {
				return selectGnomeWaylandTrayType();
			}
			return SystemTray.TrayType.AutoDetect;
		case "gtk":
		case "gtkstatusicon":
			return SystemTray.TrayType.Gtk;
		case "appindicator":
		case "app_indicator":
			return SystemTray.TrayType.AppIndicator;
		case "swing":
			return SystemTray.TrayType.Swing;
		case "awt":
			return SystemTray.TrayType.Awt;
		default:
			logger.warn("Invalid dorkbox_tray_type '{}'. Falling back to 'auto'.", configuredTrayType);
			return SystemTray.TrayType.AutoDetect;
		}
	}

	private boolean isGnomeWaylandSession() {
		final String currentDesktop = System.getenv("XDG_CURRENT_DESKTOP");
		final String desktopSession = System.getenv("DESKTOP_SESSION");
		final String sessionType = System.getenv("XDG_SESSION_TYPE");
		return (containsIgnoreCase(currentDesktop, "gnome") || containsIgnoreCase(desktopSession, "gnome"))
				&& "wayland".equalsIgnoreCase(sessionType);
	}

	private boolean containsIgnoreCase(final String value, final String expectedValue) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(expectedValue);
	}

	private SystemTray.TrayType selectGnomeWaylandTrayType() {
		if (isAppIndicatorExtensionEnabled()) {
			logger.info("GNOME Wayland session with AppIndicator extension detected. Using Dorkbox AppIndicator tray type.");
			return SystemTray.TrayType.AppIndicator;
		}
		logger.warn("GNOME Wayland session without AppIndicator extension detected. System tray is not available.");
		logger.warn("Install and enable the GNOME AppIndicator and KStatusNotifierItem extension, or disable the systray menu.");
		return null;
	}

	private boolean isAppIndicatorExtensionEnabled() {
		final String enabledExtensions = getEnabledGnomeExtensions();
		return enabledExtensions.contains(APPINDICATOR_EXTENSION)
				|| enabledExtensions.contains(UBUNTU_APPINDICATOR_EXTENSION);
	}

	private String getEnabledGnomeExtensions() {
		final ProcessBuilder processBuilder = new ProcessBuilder("gsettings", "get", "org.gnome.shell",
				"enabled-extensions");
		try {
			final Process process = processBuilder.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
					StandardCharsets.UTF_8))) {
				final String enabledExtensions = reader.readLine();
				if (!process.waitFor(1, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					logger.warn("Timeout while reading GNOME enabled extensions with gsettings.");
					return "";
				}
				final int exitCode = process.exitValue();
				if (exitCode != 0 || enabledExtensions == null) {
					logger.warn("Unable to read GNOME enabled extensions with gsettings.");
					return "";
				}
				return enabledExtensions;
			}
		} catch (final IOException e) {
			logger.warn("Unable to execute gsettings to detect GNOME AppIndicator extension.", e);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted while detecting GNOME AppIndicator extension.", e);
		}
		return "";
	}

}
