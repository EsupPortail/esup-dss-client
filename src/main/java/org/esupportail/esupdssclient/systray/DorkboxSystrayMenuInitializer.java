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

import java.net.URL;

/**
 * Implementation of {@link SystrayMenuInitializer} using
 * <a href="https://github.com/dorkbox/SystemTray">SystemTray from Dorkbox</a>.
 * 
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class DorkboxSystrayMenuInitializer implements SystrayMenuInitializer {

	private static final Logger logger = LoggerFactory.getLogger(DorkboxSystrayMenuInitializer.class.getName());

	public DorkboxSystrayMenuInitializer() {
		super();
	}

	@Override
	public void init(final String tooltip, final URL trayIconURL, final OperationFactory operationFactory,
		final SystrayMenuItem exitMenuItem, final SystrayMenuItem... systrayMenuItems) {
		logger.info("Starting dorkbox");
		final SystemTray systemTray = SystemTray.get("esupdssclient");
		if (systemTray == null) {
			logger.warn("System tray is currently not supported.");
			return;
		}

		systemTray.setImage(trayIconURL);

		final Menu menu = systemTray.getMenu();
		for(final SystrayMenuItem systrayMenuItem : systrayMenuItems) {
			menu.add(new MenuItem(systrayMenuItem.getLabel(),
					(e) -> systrayMenuItem.getFutureOperationInvocation().call(operationFactory)));
		}
		
		menu.add(new MenuItem(exitMenuItem.getLabel(),
				(e) -> exitMenuItem.getFutureOperationInvocation().call(operationFactory)));
	}

}
