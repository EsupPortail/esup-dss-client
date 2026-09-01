package org.esupportail.esupdssclient.dssclient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.AppConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DssClientSetupDialog {

	private static final double DIALOG_WIDTH = 700;
	private static final double DIALOG_HEIGHT = 500;

	private static boolean showing;

	private final UserPreferences preferences;
	private final AppConfig appConfig;
	private final DssClientConnectionManager connectionManager;
	private final DssClientPromptWindow promptWindow = new DssClientPromptWindow();

	public DssClientSetupDialog(UserPreferences preferences, AppConfig appConfig,
			DssClientConnectionManager connectionManager) {
		this.preferences = preferences;
		this.appConfig = appConfig;
		this.connectionManager = connectionManager;
	}

	public void show() {
		Platform.runLater(() -> {
			if (showing) {
				return;
			}
			showing = true;
			showAssociationsDialog();
		});
	}

	private void showAssociationsDialog() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Esup-DSS-Client");
		dialog.setHeaderText("Associations à esup-signature");
		dialog.setGraphic(null);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		fixDialogSize(dialog);
		applyButtonStyles(dialog.getDialogPane());
		dialog.getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("btn-secondary");

		Label introduction = new Label("Ce client peut être associé simultanément à plusieurs instances esup-signature.");
		introduction.setWrapText(true);
		VBox associationRows = new VBox(10);
		ScrollPane associationList = new ScrollPane(associationRows);
		associationList.setFitToWidth(true);
		associationList.setMaxHeight(360);
		associationList.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
		Button addAssociation = new Button("Ajouter une association");
		addAssociation.getStyleClass().add("btn-primary");

		VBox content = new VBox(14, associationList, addAssociation, introduction);
		content.setPadding(new Insets(4));
		dialog.getDialogPane().setContent(content);

		Runnable refresh = () -> Platform.runLater(() -> refreshAssociationRows(associationRows));
		connectionManager.addListener(refresh);
		addAssociation.setOnAction(event -> showPairingDialog());
		dialog.setOnShown(event -> {
			refreshAssociationRows(associationRows);
		});

		try {
			dialog.showAndWait();
		} finally {
			connectionManager.removeListener(refresh);
			showing = false;
		}
	}

	private void refreshAssociationRows(VBox associationRows) {
		associationRows.getChildren().clear();
		if (preferences.getDssClientAssociations().isEmpty()) {
			Label welcome = new Label("Bienvenue dans Esup-DSS-Client");
			welcome.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #212529;");
			Label explanation = new Label("Aucune instance esup-signature n'est encore associée à ce poste. "
					+ "L'association permet à esup-signature de transmettre vos demandes de signature à cette application.");
			explanation.setWrapText(true);
			explanation.setStyle("-fx-text-fill: #343a40;");
			Label steps = new Label("1. Dans esup-signature, ouvrez votre profil puis la rubrique Esup-DSS-Client.\n\n"
					+ "2. Cliquez sur « Associer un client » et copiez le lien généré.\n\n"
					+ "3. Revenez ici, cliquez sur « Ajouter une association » puis collez ce lien.\n\n"
					+ "4. Reportez dans esup-signature le code à 6 chiffres affiché par le client.");
			steps.setWrapText(true);
			steps.setStyle("-fx-text-fill: #343a40; -fx-font-size: 14px;");
			VBox onboarding = new VBox(14, welcome, explanation, steps);
			onboarding.setPadding(new Insets(18));
			onboarding.setStyle("-fx-border-color: #ced4da; -fx-border-radius: 7; -fx-background-radius: 7; "
					+ "-fx-background-color: #f8f9fa;");
			associationRows.getChildren().add(onboarding);
			return;
		}

		for (DssClientAssociation association : preferences.getDssClientAssociations()) {
			TextField url = new TextField(association.getAssociatedUrl());
			url.setEditable(false);
			url.setMaxWidth(Double.MAX_VALUE);
			url.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #212529; "
					+ "-fx-control-inner-background: #ffffff; -fx-background-color: #ffffff;");
			url.setOnMouseClicked(event -> url.selectAll());
			DssClientConnectionManager.ConnectionStatus connectionStatus = connectionManager.getStatus(association.getDeviceId());
			Label statusIndicator = new Label("●");
			statusIndicator.setStyle("-fx-text-fill: " + statusColor(connectionStatus) + "; -fx-font-size: 16px;");
			Label status = new Label(connectionStatus.getLabel());
			status.setStyle("-fx-text-fill: #343a40; -fx-font-weight: bold;");
			HBox statusLine = new HBox(6, statusIndicator, status);
			statusLine.setAlignment(Pos.CENTER_LEFT);
			Label statusHelp = new Label(statusHelp(connectionStatus));
			statusHelp.setWrapText(true);
			statusHelp.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
			VBox description = new VBox(5, url, statusLine, statusHelp);
			if (connectionStatus == DssClientConnectionManager.ConnectionStatus.WAITING_APPROVAL) {
				Label approvalCodeLabel = new Label("Code de validation");
				TextField approvalCode = new TextField(
						DssClientApprovalCode.calculate(association.getDeviceId(), association.getSecret()));
				approvalCode.setEditable(false);
				approvalCode.setAlignment(Pos.CENTER);
				approvalCode.setPrefColumnCount(6);
				approvalCode.setMaxWidth(220);
				approvalCode.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #111111; "
						+ "-fx-control-inner-background: #ffffff;");
				approvalCode.setOnMouseClicked(event -> approvalCode.selectAll());
				description.getChildren().addAll(approvalCodeLabel, approvalCode);
			}
			HBox.setHgrow(description, Priority.ALWAYS);

			Button dissociate = new Button("Dissocier");
			dissociate.getStyleClass().add("btn-danger");
			dissociate.setDisable(connectionStatus == DssClientConnectionManager.ConnectionStatus.REVOKING);
			dissociate.setOnAction(event -> confirmDissociation(association));

			HBox row = new HBox(12, description, dissociate);
			row.setAlignment(Pos.CENTER_LEFT);
			row.setPadding(new Insets(10));
			row.setStyle("-fx-border-color: #ced4da; -fx-border-radius: 7; -fx-background-radius: 7; "
					+ "-fx-background-color: #f8f9fa;");
			associationRows.getChildren().add(row);
		}
	}

	private void confirmDissociation(DssClientAssociation association) {
		ButtonType dissociate = new ButtonType("Dissocier", ButtonBar.ButtonData.OK_DONE);
		Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
				"Dissocier ce client de l'instance suivante ?\n\n" + association.getAssociatedUrl(),
				dissociate, ButtonType.CANCEL);
		confirmation.setTitle("Esup-DSS-Client");
		confirmation.setHeaderText("Confirmer la dissociation");
		confirmation.setGraphic(null);
		applyButtonStyles(confirmation.getDialogPane());
		confirmation.getDialogPane().lookupButton(dissociate).getStyleClass().add("btn-danger");
		confirmation.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("btn-secondary");
		if (confirmation.showAndWait().filter(dissociate::equals).isPresent()) {
			connectionManager.revokeAssociation(association);
		}
	}

	private String statusColor(DssClientConnectionManager.ConnectionStatus status) {
		return switch (status) {
		case CONNECTED -> "#198754";
		case WAITING_APPROVAL -> "#d39e00";
		case CONNECTING, REVOKING -> "#0d6efd";
		case DISCONNECTED, REVOCATION_FAILED -> "#dc3545";
		};
	}

	private String statusHelp(DssClientConnectionManager.ConnectionStatus status) {
		return switch (status) {
		case CONNECTED -> "Le serveur a authentifié cette association.";
		case WAITING_APPROVAL -> "Le lien est valide ; saisissez le code côté esup-signature.";
		case CONNECTING -> "Vérification du lien avec le serveur...";
		case DISCONNECTED -> "Le lien n'a pas pu être vérifié. Le client réessaiera automatiquement.";
		case REVOKING -> "La suppression locale attend la confirmation du serveur.";
		case REVOCATION_FAILED -> "L'association est conservée localement tant que le serveur n'a pas confirmé.";
		};
	}

	private void showPairingDialog() {
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Esup-DSS-Client");
		dialog.setHeaderText("Nouvelle association à esup-signature");
		dialog.setGraphic(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		fixDialogSize(dialog);

		Label instruction = new Label(
				"Collez l'URL d'association fournie dans votre profil esup-signature :");
		instruction.setWrapText(true);
		TextField pairingUrlField = new TextField();
		pairingUrlField.setPromptText("https://signature.exemple.fr/dss-client/pair?code=...");
		pairingUrlField.setMaxWidth(Double.MAX_VALUE);
		pairingUrlField.setPrefHeight(58);
		pairingUrlField.setStyle("-fx-font-size: 30px; -fx-text-fill: #111111; "
				+ "-fx-control-inner-background: #ffffff;");
		VBox pairingContent = new VBox(22, instruction, pairingUrlField);
		pairingContent.setPadding(new Insets(12, 4, 4, 4));
		dialog.getDialogPane().setContent(pairingContent);
		applyButtonStyles(dialog.getDialogPane());
		dialog.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("btn-primary");
		dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("btn-secondary");
		dialog.setResultConverter(button -> button == ButtonType.OK ? pairingUrlField.getText() : null);
		dialog.setOnShown(event -> pairingUrlField.requestFocus());
		Optional<String> pairingUrl = dialog.showAndWait().filter(text -> !text.isBlank());
		pairingUrl.ifPresent(this::pair);
	}

	private void fixDialogSize(Dialog<?> dialog) {
		dialog.setResizable(false);
		dialog.getDialogPane().setPrefSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		dialog.getDialogPane().setMinSize(DIALOG_WIDTH, DIALOG_HEIGHT);
		dialog.getDialogPane().setMaxSize(DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	private void pair(String pairingUrl) {
		CompletableFuture.runAsync(() -> {
			try {
				DssClientPairingService pairingService = new DssClientPairingService(preferences,
						appConfig.getApplicationVersion());
				DssClientPairingService.PairingResponse response = pairingService.pair(pairingUrl);
				connectionManager.refreshConnections();
				String warning = pairingUrl.trim().toLowerCase().startsWith("http://")
						? "\n\nAttention : HTTP ne protège pas l'association contre une interception réseau."
						: "";
				promptWindow.showApprovalCode("Association à autoriser",
						"Le client a été enregistré. Revenez dans votre profil esup-signature, puis saisissez ce code :",
						response.getApprovalCode(), "Identifiant : " + response.getDeviceId() + warning);
			} catch (Exception e) {
				promptWindow.showInformation("Association impossible", e.getMessage());
			}
		});
	}

	private void applyButtonStyles(javafx.scene.control.DialogPane dialogPane) {
		dialogPane.getStylesheets().add(getClass().getResource("/styles/esupdssclient.css").toExternalForm());
	}
}
