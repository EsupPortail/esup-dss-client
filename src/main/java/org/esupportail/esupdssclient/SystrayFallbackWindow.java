/**
 * © Nowina Solutions, 2015-2017
 *
 * Concédée sous licence EUPL, version 1.1 ou – dès leur approbation par la Commission européenne - versions ultérieures de l’EUPL (la «Licence»).
 * Vous ne pouvez utiliser la présente œuvre que conformément à la Licence.
 * Vous pouvez obtenir une copie de la Licence à l’adresse suivante:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Sauf obligation légale ou contractuelle écrite, le logiciel distribué sous la Licence est distribué «en l’état»,
 * SANS GARANTIES OU CONDITIONS QUELLES QU’ELLES SOIENT, expresses ou implicites.
 * Consultez la Licence pour les autorisations et les restrictions linguistiques spécifiques relevant de la Licence.
 */
package org.esupportail.esupdssclient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.SystrayMenuItem;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.view.core.NonBlockingUIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

public class SystrayFallbackWindow {

	private static final Logger logger = LoggerFactory.getLogger(SystrayFallbackWindow.class.getName());
	private static final String PROJECT_URL = "https://www.esup-portail.org/wiki/display/SIGN/Esup-DSS-Client";

	private final ResourceBundle resources = ResourceBundle.getBundle("bundles/api");

	public void show(final Stage stage, final EsupDSSClientAPI api, final OperationFactory operationFactory,
			final UserPreferences prefs) {
		final AppConfig config = api.getAppConfig();
		stage.setTitle(config.getApplicationName());
		stage.setMinWidth(560);
		stage.setMinHeight(360);
		stage.setOnCloseRequest(event -> exitApplication());

		final Scene scene = new Scene(createRoot(stage, api, operationFactory, prefs), 640, 480);
		scene.getStylesheets().add(getClass().getResource("/styles/esupdssclient.css").toString());
		stage.setScene(scene);
		stage.show();
		stage.toFront();
	}

	private BorderPane createRoot(final Stage stage, final EsupDSSClientAPI api,
			final OperationFactory operationFactory, final UserPreferences prefs) {
		final AppConfig config = api.getAppConfig();
		final BorderPane root = new BorderPane();
		root.getStyleClass().add("fallback-root");

		final VBox content = new VBox();
		content.getStyleClass().add("fallback-content");
		content.setAlignment(Pos.TOP_CENTER);
		content.setSpacing(10);

		final ImageView logo = createLogo();
		final Label title = new Label(config.getApplicationName());
		title.getStyleClass().add("fallback-title");

		final Label subtitle = new Label(resources.getString("systray.fallback.subtitle"));
		subtitle.getStyleClass().add("fallback-subtitle");
		subtitle.setWrapText(true);

		final Label message = new Label(resources.getString("systray.fallback.message"));
		message.getStyleClass().add("fallback-message");
		message.setWrapText(true);

		final Label version = new Label(resources.getString("about.appVersion") + " " + getDisplayVersion(config));
		version.getStyleClass().add("fallback-version");

		final Hyperlink hyperlink = new Hyperlink(PROJECT_URL);
		hyperlink.getStyleClass().add("fallback-link");
		hyperlink.setOnAction(e -> openProjectPage());

		content.getChildren().addAll(logo, title, subtitle, message, version, hyperlink);
		root.setCenter(content);

		final VBox bottomBox = new VBox();
		bottomBox.setSpacing(10);

		final HBox actionButtons = new HBox();
		actionButtons.setSpacing(10);
		actionButtons.setAlignment(Pos.CENTER);

		final Button preferences = new Button(resources.getString("systray.menu.preferences"));
		preferences.getStyleClass().add("btn-info");
		preferences.setOnAction(e -> {
			final GlobalConfigurer proxyConfigurer = new GlobalConfigurer(api.getAppConfig(), prefs);
			operationFactory.getOperation(NonBlockingUIOperation.class, "/fxml/preferences.fxml",
					proxyConfigurer, prefs, !api.getAppConfig().isUserPreferencesEditable()).perform();
		});
		actionButtons.getChildren().add(preferences);

		final List<SystrayMenuItem> extensions = api.getExtensionSystrayMenuItems();
		for (final SystrayMenuItem item : extensions) {
			final Button btn = new Button(item.getLabel());
			btn.getStyleClass().add("btn-info");
			btn.setOnAction(e -> item.getFutureOperationInvocation().call(operationFactory));
			actionButtons.getChildren().add(btn);
		}

		final Button reduce = new Button(resources.getString("button.reduce"));
		reduce.getStyleClass().add("btn-secondary");
		reduce.setOnAction(e -> stage.setIconified(true));

		final Button quit = new Button(resources.getString("button.quit"));
		quit.getStyleClass().add("btn-danger");
		quit.setOnAction(e -> exitApplication());

		final HBox systemButtons = new HBox(reduce, quit);
		systemButtons.setSpacing(10);
		systemButtons.getStyleClass().add("fallback-buttons");
		systemButtons.setAlignment(Pos.CENTER_RIGHT);

		bottomBox.getChildren().addAll(actionButtons, systemButtons);
		root.setBottom(bottomBox);
		BorderPane.setMargin(bottomBox, new Insets(0, 24, 24, 24));
		return root;
	}

	private ImageView createLogo() {
		final ImageView logo = new ImageView();
		try (InputStream inputStream = EsupDSSClientPreLoader.class.getResourceAsStream("/images/logo.png")) {
			if (inputStream != null) {
				logo.setImage(new Image(inputStream));
			}
		} catch (final IOException e) {
			logger.warn("Unable to load fallback window logo.", e);
		}
		logo.setFitWidth(300);
		logo.setPreserveRatio(true);
		VBox.setVgrow(logo, Priority.NEVER);
		return logo;
	}

	private String getDisplayVersion(final AppConfig config) {
		final Properties properties = EsupDSSClientLauncher.getProperties();
		final String displayVersion = properties.getProperty("display_version");
		if (displayVersion != null && !displayVersion.trim().isEmpty()) {
			return displayVersion.trim();
		}
		return config.getApplicationVersion();
	}

	private void openProjectPage() {
		if (!Desktop.isDesktopSupported()) {
			logger.warn("Desktop API is not supported. Cannot open project page.");
			return;
		}
		try {
			Desktop.getDesktop().browse(new URI(PROJECT_URL));
		} catch (final IOException | URISyntaxException e) {
			logger.warn("Unable to open project page.", e);
		}
	}

	private void exitApplication() {
		logger.info("Exiting from systray fallback window...");
		Platform.exit();
		System.exit(0);
	}

}
