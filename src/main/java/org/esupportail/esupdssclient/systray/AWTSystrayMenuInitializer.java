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

import org.esupportail.esupdssclient.api.SystrayMenuItem;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URL;

/**
 * Implementation of {@link SystrayMenuInitializer} using AWT.
 * 
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class AWTSystrayMenuInitializer implements SystrayMenuInitializer {

	private static final Logger logger = LoggerFactory.getLogger(AWTSystrayMenuInitializer.class.getName());
	
	public AWTSystrayMenuInitializer() {
		super();
	}
	
	@Override
	public void init(final String tooltip, final URL trayIconURL, final OperationFactory operationFactory,
			final SystrayMenuItem exitMenuItem, final SystrayMenuItem... systrayMenuItems) {
		if (SystemTray.isSupported()) {
			final PopupMenu popup = new PopupMenu();
			
			for(final SystrayMenuItem systrayMenuItem : systrayMenuItems) {
				final MenuItem mi = new MenuItem(systrayMenuItem.getLabel());
				mi.addActionListener((l) -> systrayMenuItem.getFutureOperationInvocation().call(operationFactory));
				popup.add(mi);
			}
			
			final Image image = Toolkit.getDefaultToolkit().getImage(trayIconURL);
			final TrayIcon trayIcon = new TrayIcon(image, tooltip, popup);
			trayIcon.setImageAutoSize(true);
			
			final MenuItem mi = new MenuItem(exitMenuItem.getLabel());
			mi.addActionListener((l) -> exit(operationFactory, exitMenuItem, trayIcon));
			popup.add(mi);
			
			try {
				SystemTray.getSystemTray().add(trayIcon);
			} catch (final AWTException e) {
				logger.error("Cannot add TrayIcon", e);
			}
		} else {
			logger.error("System tray is currently not supported.");
		}
	}

	private void exit(final OperationFactory operationFactory, final SystrayMenuItem exitMenuItem,
			final TrayIcon trayIcon) {
		SystemTray.getSystemTray().remove(trayIcon);
		exitMenuItem.getFutureOperationInvocation().call(operationFactory);
	}
}
