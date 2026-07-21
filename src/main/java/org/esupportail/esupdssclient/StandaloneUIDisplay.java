/**
 * © Nowina Solutions, 2015-2016
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

import eu.europa.esig.dss.token.PasswordInputCallback;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.esupportail.esupdssclient.api.EsupDSSClientPasswordInputCallback;
import org.esupportail.esupdssclient.api.MessageDisplayCallback;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.flow.StageHelper;
import org.esupportail.esupdssclient.view.core.ExtensionFilter;
import org.esupportail.esupdssclient.view.core.NonBlockingUIOperation;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.esupportail.esupdssclient.view.core.UIOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.util.ResourceBundle;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of {@link UIDisplay} used for standalone mode.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class StandaloneUIDisplay implements UIDisplay {

	private static final Logger logger = LoggerFactory.getLogger(StandaloneUIDisplay.class.getName());
	private static final double DSS_CLIENT_SIGNING_WIDTH = 700;
	private static final double DSS_CLIENT_SIGNING_HEIGHT = 500;

	private Stage blockingStage;
	private Stage nonBlockingStage;
	private BorderPane blockingContainer;
	private BorderPane nonBlockingContainer;
	private UIOperation<?> currentBlockingOperation;
	private OperationFactory operationFactory;
	private volatile boolean dssClientSigningSessionActive;
	private volatile DssClientSigningStep dssClientSigningStep;
	
	public StandaloneUIDisplay() {
		this.blockingContainer = new BorderPane();
		this.nonBlockingContainer = new BorderPane();
		this.blockingStage = createStage(true, null, blockingContainer);
		this.nonBlockingStage = createStage(false, null, nonBlockingContainer);
	}

	private void display(Parent panel, boolean blockingOperation) {
		logger.info("Display " + panel + " in display " + this + " from Thread " + Thread.currentThread().getName());
		Platform.runLater(() -> {
			boolean useBlockingWindow = blockingOperation || dssClientSigningSessionActive;
			Stage stage = useBlockingWindow ? blockingStage : nonBlockingStage;
			BorderPane container = useBlockingWindow ? blockingContainer : nonBlockingContainer;
			logger.info("Display " + panel + " in display " + this + " from Thread " + Thread.currentThread().getName());
			logger.info("Loading ui " + panel + " in Stage " + stage);
			if (dssClientSigningSessionActive) {
				configureDssClientPrimaryAction(panel);
			}
			container.setCenter(panel);
			stage.setTitle(StageHelper.getInstance().getTitle());
			configureDefaultCloseRequest(stage, useBlockingWindow);
			if (!dssClientSigningSessionActive) {
				stage.sizeToScene();
			}
			stage.show();
			stage.toFront();
			StageHelper.getInstance().setTitle("", null);
		});
	}

	private Stage createStage(final boolean blockingStage, String title, BorderPane container) {
		final Stage newStage = new Stage();
		newStage.setTitle(title);
		newStage.setAlwaysOnTop(true);
		Scene scene = new Scene(container);
		scene.getStylesheets().add(this.getClass().getResource("/styles/esupdssclient.css").toString());
		newStage.setScene(scene);
		configureDefaultCloseRequest(newStage, blockingStage);
		newStage.setOnShown(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				centerOnPointerScreen(newStage);
			}
		});
		return newStage;
	}

	private void centerOnPointerScreen(Stage stage) {
		Screen currentScreen = Screen.getPrimary();
		PointerInfo pointerInfo = MouseInfo.getPointerInfo();
		int mouseX = (int) pointerInfo.getLocation().getX();
		int mouseY = (int) pointerInfo.getLocation().getY();
		for (Screen screen : Screen.getScreens()) {
			Rectangle2D bounds = screen.getBounds();
			if (bounds.contains(mouseX, mouseY)) {
				currentScreen = screen;
			}
		}
		Rectangle2D screenBounds = currentScreen.getVisualBounds();
		stage.setX(((screenBounds.getWidth() - stage.getWidth()) / 2) + screenBounds.getMinX());
		stage.setY((screenBounds.getHeight() - stage.getHeight()) / 2);
	}

	private void configureDefaultCloseRequest(Stage stage, boolean blockingStage) {
		stage.setOnCloseRequest((e) -> {
			logger.info("Closing stage " + stage + " from " + Thread.currentThread().getName());
			stage.hide();
			e.consume();

			if (blockingStage && (currentBlockingOperation != null)) {
				currentBlockingOperation.signalUserCancel();
			}
		});
	}

	@Override
	public void close(final boolean blockingOperation) {
		if (dssClientSigningSessionActive) {
			logger.info("Keep DSS client signing window open while its session is active");
			return;
		}
		Platform.runLater(() -> {
			Stage oldStage = (blockingOperation) ? blockingStage : nonBlockingStage;
			logger.info("Hide stage " + oldStage);
			oldStage.hide();
		});
	}

	public <T> void displayAndWaitUIOperation(final UIOperation<T> operation) {
		display(operation.getRoot(), true);
		waitForUser(operation);
	}

	/**
	 * Starts a WSS signing session. All subsequent DSS UI operations reuse the
	 * blocking stage until {@link #finishDssClientSigningSession()} is called.
	 */
	public void startDssClientSigningSession() {
		dssClientSigningSessionActive = true;
		Platform.runLater(() -> {
			blockingStage.setWidth(DSS_CLIENT_SIGNING_WIDTH);
			blockingStage.setHeight(DSS_CLIENT_SIGNING_HEIGHT);
			blockingStage.setResizable(false);
		});
		setDssClientSigningStep(DssClientSigningStep.CERTIFICATE);
	}

	public void setDssClientSigningStep(DssClientSigningStep step) {
		dssClientSigningStep = step;
		if (!dssClientSigningSessionActive) {
			return;
		}
		Platform.runLater(() -> {
			if (dssClientSigningSessionActive) {
				blockingContainer.setTop(createDssClientStepper(dssClientSigningStep));
			}
		});
	}

	public void finishDssClientSigningSession() {
		dssClientSigningSessionActive = false;
		dssClientSigningStep = null;
		Platform.runLater(() -> {
			blockingContainer.setTop(null);
			blockingContainer.setCenter(null);
			blockingStage.hide();
			blockingStage.setResizable(true);
		});
	}

	private HBox createDssClientStepper(DssClientSigningStep currentStep) {
		HBox stepper = new HBox(8);
		stepper.setPadding(new Insets(12, 18, 8, 18));
		stepper.setAlignment(Pos.CENTER_LEFT);
		List<String> steps = List.of("1. Certificat", "2. Controle", "3. Signature");
		for (int index = 0; index < steps.size(); index++) {
			Label label = new Label(steps.get(index));
			if (index + 1 == currentStep.getOrder()) {
				label.setStyle("-fx-font-weight: bold; -fx-text-fill: #0d6efd;");
			} else if (index + 1 < currentStep.getOrder()) {
				label.setStyle("-fx-text-fill: #198754;");
			} else {
				label.setStyle("-fx-text-fill: #6c757d;");
			}
			stepper.getChildren().add(label);
		}
		return stepper;
	}

	private void configureDssClientPrimaryAction(Parent panel) {
		if (!(panel instanceof BorderPane borderPane) || borderPane.getBottom() == null) {
			return;
		}
		Button primaryAction = findPrimaryAction(borderPane.getBottom());
		if (primaryAction == null) {
			return;
		}
		primaryAction.setText(dssClientSigningStep.getButtonLabel());
		primaryAction.getStyleClass().removeAll("btn-default", "btn-secondary", "btn-primary", "btn-danger", "btn-info");
		if (!primaryAction.getStyleClass().contains("btn-success")) {
			primaryAction.getStyleClass().add("btn-success");
		}
		if (primaryAction.getParent() instanceof HBox actions) {
			actions.setAlignment(Pos.CENTER_RIGHT);
		}
	}

	private Button findPrimaryAction(Node node) {
		if (node instanceof Button button && isDssClientPrimaryAction(button.getId())) {
			return button;
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				Button button = findPrimaryAction(child);
				if (button != null) {
					return button;
				}
			}
		}
		return null;
	}

	private boolean isDssClientPrimaryAction(String buttonId) {
		return "select".equals(buttonId) || "ok".equals(buttonId) || "store".equals(buttonId);
	}

	private <T> void waitForUser(UIOperation<T> operation) {
		try {
			logger.info("Wait on Thread " + Thread.currentThread().getName());
			currentBlockingOperation = operation;
			operation.waitEnd();
			currentBlockingOperation = null;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private final class FlowPasswordCallback implements EsupDSSClientPasswordInputCallback {
		
		private String passwordPrompt;
		
		public FlowPasswordCallback() {
			this.passwordPrompt = null;
		}
		
		@Override
		public char[] getPassword() {
			logger.info("Request password");
			@SuppressWarnings("unchecked")
			final OperationResult<Object> passwordResult = StandaloneUIDisplay.this.operationFactory.getOperation(
					UIOperation.class, "/fxml/password-input.fxml", passwordPrompt, EsupDSSClientLauncher.getConfig().getApplicationName()).perform();
			if(passwordResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				return (char[]) passwordResult.getResult();
			} else if(passwordResult.getStatus().equals(BasicOperationStatus.USER_CANCEL)) {
				throw new CancelledOperationException();
			} else if(passwordResult.getStatus().equals(BasicOperationStatus.EXCEPTION)) {
				final Exception e = passwordResult.getException();
				if(e instanceof RuntimeException) {
					// Throw exception as is
					throw (RuntimeException) e;
				} else {
					// Wrap in a runtime exception
					throw new EsupDSSClientException(e);
				}
			} else {
				throw new IllegalArgumentException("Not managed operation status: " + passwordResult.getStatus().getCode());
			}
		}

		@Override
		public void setPasswordPrompt(String passwordPrompt) {
			this.passwordPrompt = passwordPrompt;
		}
	}

	@Override
	public PasswordInputCallback getPasswordInputCallback() {
		return new FlowPasswordCallback();
	}
	
	private final class FlowMessageDisplayCallback implements MessageDisplayCallback {
		@Override
		public void display(Message message) {
			if(Message.INPUT_PINPAD.equals(message)) {
				StandaloneUIDisplay.this.operationFactory.getOperation(
						NonBlockingUIOperation.class, "/fxml/message-no-button.fxml",
						"message.display.callback." + message.name().toLowerCase().replace('_', '.'),
						EsupDSSClientLauncher.getConfig().getApplicationName()).perform();
			} else {
				StandaloneUIDisplay.this.operationFactory.getOperation(
					NonBlockingUIOperation.class, "/fxml/message.fxml",
					"message.display.callback." + message.name().toLowerCase().replace('_', '.'),
					EsupDSSClientLauncher.getConfig().getApplicationName()).perform();
			}
		}

		@Override
		public void dispose() {
			StandaloneUIDisplay.this.close(false);
		}
	}
	
	@Override
	public MessageDisplayCallback getMessageDisplayCallback() {
		return new FlowMessageDisplayCallback();
	}

	@Override
	public File displayFileChooser(ExtensionFilter... extensionFilters) {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(ResourceBundle.getBundle("bundles/api").getString("fileChooser.title.openResourceFile"));
		fileChooser.getExtensionFilters().addAll(toJavaFXExtensionFilters(extensionFilters));
		return fileChooser.showOpenDialog(blockingStage);
	}

	public boolean confirmDssClientSignature(String documentName, String origin) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean accepted = new AtomicBoolean(false);
		AtomicBoolean completed = new AtomicBoolean(false);
		Platform.runLater(() -> {
			BorderPane root = new BorderPane();
			root.setMinWidth(420);
			VBox content = new VBox(12);
			content.setPadding(new Insets(18));

			Label header = new Label("Confirmer la signature electronique");
			header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
			header.setWrapText(true);
			Label document = new Label("Nom du document : " + (documentName == null ? "" : documentName));
			document.setWrapText(true);
			Label provenance = new Label("Provenance : " + (origin == null ? "esup-signature" : origin));
			provenance.setWrapText(true);

			Button cancel = new Button("Annuler");
			Button confirm = new Button(DssClientSigningStep.CONFIRMATION.getButtonLabel());
			cancel.getStyleClass().add("btn-secondary");
			confirm.getStyleClass().add("btn-success");
			cancel.setCancelButton(true);
			confirm.setDefaultButton(true);
			cancel.setOnAction(event -> finishDssClientConfirmation(false, accepted, completed, latch));
			confirm.setOnAction(event -> finishDssClientConfirmation(true, accepted, completed, latch));

			HBox actions = new HBox(10, cancel, confirm);
			actions.getStyleClass().add("btn-container");
			actions.setAlignment(Pos.CENTER_RIGHT);
			content.getChildren().addAll(header, document, provenance);
			root.setCenter(content);
			root.setBottom(actions);

			blockingContainer.setCenter(root);
			blockingStage.setTitle("Esup-DSS-Client");
			blockingStage.setOnCloseRequest(event -> {
				event.consume();
				finishDssClientConfirmation(false, accepted, completed, latch);
			});
			if (!dssClientSigningSessionActive) {
				blockingStage.sizeToScene();
			}
			blockingStage.show();
			blockingStage.toFront();
		});
		latch.await();
		return accepted.get();
	}

	private void finishDssClientConfirmation(boolean value, AtomicBoolean accepted, AtomicBoolean completed, CountDownLatch latch) {
		if (completed.compareAndSet(false, true)) {
			accepted.set(value);
			latch.countDown();
		}
	}

	public enum DssClientSigningStep {
		CERTIFICATE(1, "Suivant"),
		CONFIRMATION(2, "Suivant"),
		SIGNATURE(3, "Terminer");

		private final int order;
		private final String buttonLabel;

		DssClientSigningStep(int order, String buttonLabel) {
			this.order = order;
			this.buttonLabel = buttonLabel;
		}

		public int getOrder() {
			return order;
		}

		public String getButtonLabel() {
			return buttonLabel;
		}
	}
	
	private FileChooser.ExtensionFilter[] toJavaFXExtensionFilters(ExtensionFilter... extensionFilters) {
		final FileChooser.ExtensionFilter[] result = new FileChooser.ExtensionFilter[extensionFilters.length];
		int i = 0;
		for(final ExtensionFilter extensionFilter : extensionFilters) {
			result[i++] = new FileChooser.ExtensionFilter(extensionFilter.getDescription(), extensionFilter.getExtensions());
		}
		return result;
	}
	
	public void setOperationFactory(final OperationFactory operationFactory) {
		this.operationFactory = operationFactory;
	}

	@Override
	public void display(NonBlockingUIOperation operation) {
		display(operation.getRoot(), false);
	}
}
