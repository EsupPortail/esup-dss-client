package org.esupportail.esupdssclient.dssclient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DssClientPromptWindow {

	private Stage stage;

	public void showInformation(String title, String message) {
		Platform.runLater(() -> {
			ensureStage();
			VBox root = baseRoot(title);
			Label messageLabel = new Label(message);
			messageLabel.setWrapText(true);
			Button close = new Button("Fermer");
			close.getStyleClass().add("btn-secondary");
			close.setDefaultButton(true);
			close.setOnAction(event -> stage.hide());
			stage.setOnCloseRequest(event -> {
				stage.hide();
				event.consume();
			});
			HBox actions = actions(close);
			root.getChildren().addAll(messageLabel, actions);
			refresh(root, title);
		});
	}

	public void showApprovalCode(String title, String message, String code, String details) {
		Platform.runLater(() -> {
			ensureStage();
			VBox root = baseRoot(title);
			Label messageLabel = new Label(message);
			messageLabel.setWrapText(true);
			Label codeLabel = new Label("Code de validation");
			TextField codeField = approvalCodeField(code);
			Button copy = new Button("Copier le code");
			copy.getStyleClass().add("btn-primary");
			copy.setOnAction(event -> copyToClipboard(code));
			Label detailsLabel = new Label(details);
			detailsLabel.setWrapText(true);
			Button close = new Button("Fermer");
			close.getStyleClass().add("btn-secondary");
			close.setDefaultButton(true);
			close.setOnAction(event -> stage.hide());
			stage.setOnCloseRequest(event -> {
				stage.hide();
				event.consume();
			});
			HBox codeRow = new HBox(10, codeField, copy);
			codeRow.setAlignment(Pos.CENTER_LEFT);
			root.getChildren().addAll(messageLabel, codeLabel, codeRow, detailsLabel, actions(close));
			refresh(root, title);
			codeField.requestFocus();
			codeField.selectAll();
		});
	}

	private TextField approvalCodeField(String code) {
		TextField codeField = new TextField(code);
		codeField.setEditable(false);
		codeField.setAlignment(Pos.CENTER);
		codeField.setPrefColumnCount(6);
		codeField.setMaxWidth(220);
		codeField.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #111111; "
				+ "-fx-control-inner-background: #ffffff;");
		codeField.setOnMouseClicked(event -> codeField.selectAll());
		return codeField;
	}

	private void copyToClipboard(String value) {
		ClipboardContent content = new ClipboardContent();
		content.putString(value);
		Clipboard.getSystemClipboard().setContent(content);
	}

	private void ensureStage() {
		if (stage == null) {
			stage = new Stage();
			stage.setTitle("Esup-DSS-Client");
			stage.setAlwaysOnTop(true);
		}
	}

	private VBox baseRoot(String header) {
		VBox root = new VBox(12);
		root.setPadding(new Insets(18));
		root.setMinWidth(420);
		Label headerLabel = new Label(header);
		headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
		headerLabel.setWrapText(true);
		root.getChildren().add(headerLabel);
		return root;
	}

	private HBox actions(Button... buttons) {
		HBox actions = new HBox(10, buttons);
		actions.setPadding(new Insets(8, 0, 0, 0));
		return actions;
	}

	private void refresh(VBox root, String title) {
		Scene scene = stage.getScene();
		if (scene == null) {
			scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/styles/esupdssclient.css").toExternalForm());
			stage.setScene(scene);
		} else {
			scene.setRoot(root);
		}
		stage.setTitle(title);
		stage.sizeToScene();
		stage.show();
		stage.toFront();
	}
}
