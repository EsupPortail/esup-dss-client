package org.esupportail.esupdssclient.dssclient;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.AppConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DssClientSetupDialog {

	private static boolean showing;

	private final UserPreferences preferences;
	private final AppConfig appConfig;
	private final DssClientWebSocketService webSocketService;
	private final DssClientPromptWindow promptWindow = new DssClientPromptWindow();

	public DssClientSetupDialog(UserPreferences preferences, AppConfig appConfig, DssClientWebSocketService webSocketService) {
		this.preferences = preferences;
		this.appConfig = appConfig;
		this.webSocketService = webSocketService;
	}

	public void show() {
		Platform.runLater(() -> {
			if (showing) {
				return;
			}
			showing = true;
			if (preferences.hasDssClientCredential()) {
				showAssociatedClientDialog();
			} else {
				showPairingDialog();
			}
		});
	}

	private void showPairingDialog() {
		TextInputDialog dialog = new TextInputDialog();
		dialog.setTitle("Esup-DSS-Client");
		dialog.setHeaderText("Association a esup-signature");
		dialog.setGraphic(null);
		dialog.setContentText("Collez l'URL d'association fournie dans votre profil esup-signature :");
		Optional<String> value = dialog.showAndWait();
		Optional<String> pairingUrl = value.filter(text -> !text.isBlank());
		if (pairingUrl.isPresent()) {
			pair(pairingUrl.get());
		} else {
			showing = false;
		}
	}

	private void showAssociatedClientDialog() {
		ButtonType dissociate = new ButtonType("Dissocier", ButtonBar.ButtonData.LEFT);
		Alert dialog = new Alert(Alert.AlertType.NONE,
				"Ce poste est actuellement associe a esup-signature.", dissociate, ButtonType.CLOSE);
		dialog.setTitle("Esup-DSS-Client");
		dialog.setHeaderText("Association a esup-signature");
		dialog.setGraphic(null);
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == dissociate) {
			webSocketService.stop();
			preferences.clearDssClientCredential();
		}
		showing = false;
	}

	private void pair(String pairingUrl) {
		CompletableFuture.runAsync(() -> {
			try {
				DssClientPairingService pairingService = new DssClientPairingService(preferences, appConfig.getApplicationVersion());
				DssClientPairingService.PairingResponse response = pairingService.pair(pairingUrl);
				promptWindow.showInformation("Client associe", "Ce poste est maintenant associe a esup-signature.\nIdentifiant : " + response.getDeviceId());
				webSocketService.connect();
			} catch (Exception e) {
				promptWindow.showInformation("Association impossible", e.getMessage());
			} finally {
				Platform.runLater(() -> showing = false);
			}
		});
	}
}
