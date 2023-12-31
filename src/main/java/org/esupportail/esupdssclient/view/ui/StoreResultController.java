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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.apache.commons.lang.StringEscapeUtils;
import org.esupportail.esupdssclient.flow.StageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class StoreResultController extends AbstractFeedbackUIOperationController implements Initializable {

	private static final Logger logger = LoggerFactory.getLogger(StoreResultController.class.getName());

	@FXML
	private Button store;

	@FXML
	private Button forget;

	@FXML
	private Label message;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		store.setOnAction(e -> {
			logger.info("Store for ? parameters: "
					+ getFeedback().getSelectedAPI() + " - " + getFeedback().getApiParameter());
			signalEnd(getFeedback());
		});
		forget.setOnAction(e -> signalEnd(null));
	}

	@Override
	protected void doInit(Object... params) {
		Platform.runLater(() -> {
			StageHelper.getInstance().setTitle(getApplicationName(), "store.configuration.title");
			message.setText(StringEscapeUtils.unescapeJava(MessageFormat.format(
					ResourceBundle.getBundle("bundles/api").getString("store.configuration.header"),
					getApplicationName())));
		}
		);
	}
}
