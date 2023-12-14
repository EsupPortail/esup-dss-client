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

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.esupportail.esupdssclient.EsupDSSClientLauncher;
import org.esupportail.esupdssclient.EsupDSSClientPreLoader;
import org.esupportail.esupdssclient.flow.StageHelper;
import org.esupportail.esupdssclient.view.core.AbstractUIOperationController;

import java.awt.*;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ResourceBundle;

public class AboutController extends AbstractUIOperationController<Void> implements Initializable {

	@FXML
	private Button ok;

	@FXML
	private Label applicationVersion;

	@FXML
	private Hyperlink hyperlink;

	@FXML
	private ImageView logo;

	@FXML
	private Label dbVersion;

	@FXML
	private Label dbFile;

	@FXML
	private Label footer;

	private ResourceBundle resources;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		ok.setOnAction(e -> signalEnd(null));
		this.resources = resources;
	}

	@Override
	public void init(Object... params) {
		final String applicationName = (String) params[0];
		StageHelper.getInstance().setTitle("Esup-DSS-Client - " + resources.getString("about.header") + " " + applicationName);
		InputStream inputStream = EsupDSSClientPreLoader.class.getResourceAsStream("/images/logo.jpg");
		if(inputStream != null) {
			this.logo.setImage(new Image(inputStream));
		}
        this.applicationVersion.setText(EsupDSSClientLauncher.getProperties().getProperty("display_version"));
		this.hyperlink.setOnAction(e -> {
			try {
				Desktop.getDesktop().browse(new URI(hyperlink.getText()));
			} catch (IOException | URISyntaxException e1) {
				e1.printStackTrace();
			}
		});
	}

}
