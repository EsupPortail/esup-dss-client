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

package org.esupportail.esupdssclient.generic;

import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.EsupDSSClientLauncher;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EnvironmentInfo;
import org.esupportail.esupdssclient.api.Feedback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * <p>
 * Previously, Esup-DSS-Client transmitted (only in case of user's approval) anonymous
 * usage statistics and/or debug data.
 * </p>
 * <p>
 * In order to comply with recent European legislation, Esup-DSS-Client doesn't transmit
 * any data anymore but this class simplifies ticket creation/email reporting of
 * incidents by users.
 * </p>
 * 
 * @author Landry Soules
 *
 */
public class DebugHelper {

	private static final String DEBUG_FILE_NAME = "esupdssclient.debug";
	private static final String LOG_FILE_NAME = "esupdssclient.log";
	private static final Logger logger = LoggerFactory.getLogger(DebugHelper.class);

	Feedback collectDebugData(Throwable throwable) {
		final Exception exception;
		if (throwable instanceof Exception) {
			exception = (Exception) throwable;
		} else {
			exception = new EsupDSSClientException(throwable);
		}
		final Feedback feedback = new Feedback(exception);
		feedback.setApiVersion(getConfig().getApplicationVersion());
		feedback.setInfo(EnvironmentInfo.buildFromSystemProperties(getProperties()));
		return feedback;
	}

	Path buildDebugFile() throws IOException {
		File debugFile = null;
		Path debugFilePath = null;
		Path existingLogPath = Paths.get(getConfig().getApiHome().getAbsolutePath(), LOG_FILE_NAME);
		if (existingLogPath.toFile().exists()) {
			debugFilePath = Files.copy(existingLogPath,
					Paths.get(getConfig().getApiHome().getAbsolutePath(), DEBUG_FILE_NAME), REPLACE_EXISTING);
		} else {
			debugFilePath = Paths.get(getConfig().getApiHome().getAbsolutePath(), DEBUG_FILE_NAME);
		}
		debugFile = debugFilePath.toFile();
		debugFile.createNewFile();
		return debugFilePath;
	}

	File appendFeedbackData(final Path debugFilePath, final Feedback feedback) throws JAXBException, IOException {
		File debugFile = null;
		JAXBContext ctx = JAXBContext.newInstance(Feedback.class);
		Marshaller marshaller = ctx.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		debugFile = debugFilePath.toFile();
		try (Writer fw = new FileWriter(debugFile, true)) {
			marshaller.marshal(feedback, fw);
		}
		return debugFile;
	}

	void showDebugFileInExplorer(final Path debugFilePath, final Feedback feedback) {
		String os = feedback.getInfo().getOs().name();
		if (os.equalsIgnoreCase("windows")) {
			try {
				showFileInWindows(debugFilePath);
			} catch (IOException e) {
				logger.warn("Error while trying to open Windows file browser: {}", e.getMessage());
			}
		} else if (os.equalsIgnoreCase("linux")) {
			try {
				showFileInLinux(debugFilePath);
			} catch (IOException e) {
				logger.warn("Error while trying to open Linux file browser: {}", e.getMessage());
			}
		}
	}

	private void showFileInWindows(final Path debugFilePath) throws IOException {
		Runtime.getRuntime().exec(String.format("explorer.exe /select,%s", debugFilePath));
	}

	private void showFileInLinux(final Path debugFilePath) throws IOException {

		Process process = Runtime.getRuntime().exec("xdg-mime query default inode/directory");
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

		String os = stdInput.readLine().split("\\.")[0];
		Runtime.getRuntime().exec(String.format("%s %s", os, debugFilePath));
	}
	
	public Feedback processError(final Throwable throwable) throws IOException, JAXBException {
	
		final Feedback feedback = collectDebugData(throwable);
		final Path debugFilePath = buildDebugFile();
		final File debugFile = appendFeedbackData(debugFilePath, feedback);
		showDebugFileInExplorer(debugFile.toPath(), feedback);
		return feedback;
	}

	AppConfig getConfig() {
		return EsupDSSClientLauncher.getConfig();
	}

	Properties getProperties() {
		return System.getProperties();
	}

	String getDebugFileName() {
		return DEBUG_FILE_NAME;
	}
}
