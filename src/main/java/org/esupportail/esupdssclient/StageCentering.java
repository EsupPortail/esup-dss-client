package org.esupportail.esupdssclient;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.PointerInfo;

/**
 * Centers a stage on the screen containing the mouse pointer, keeping it inside the visible area.
 */
public final class StageCentering {

	private static final Logger logger = LoggerFactory.getLogger(StageCentering.class);

	private StageCentering() {
	}

	public static void centerOnPointerScreen(Stage stage) {
		try {
			Rectangle2D area = findScreen().getVisualBounds();
			double w = stage.getWidth();
			double h = stage.getHeight();
			if (Double.isNaN(w) || Double.isNaN(h)) {
				return;
			}
			double x = area.getMinX() + (area.getWidth() - w) / 2;
			double y = area.getMinY() + (area.getHeight() - h) / 2;
			stage.setX(Math.max(area.getMinX(), Math.min(x, area.getMaxX() - w)));
			stage.setY(Math.max(area.getMinY(), Math.min(y, area.getMaxY() - h)));
		} catch (Exception e) {
			logger.warn("Unable to center stage, using default position", e);
		}
	}

	private static Screen findScreen() {
		if (!GraphicsEnvironment.isHeadless()) {
			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			if (pointerInfo != null) {
				// AWT reports physical pixels whereas JavaFX uses scaled units: try both
				double px = pointerInfo.getLocation().getX();
				double py = pointerInfo.getLocation().getY();
				for (Screen screen : Screen.getScreens()) {
					Rectangle2D b = screen.getBounds();
					if (b.contains(px / screen.getOutputScaleX(), py / screen.getOutputScaleY()) || b.contains(px, py)) {
						return screen;
					}
				}
			}
		}
		return Screen.getPrimary();
	}
}
