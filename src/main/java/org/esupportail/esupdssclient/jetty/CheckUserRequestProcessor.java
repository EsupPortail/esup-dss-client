package org.esupportail.esupdssclient.jetty;

import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.api.EnvironmentInfo;
import org.esupportail.esupdssclient.api.OS;
import org.esupportail.esupdssclient.process.NativeProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specialized version of {@link RequestProcessor} that checks whether requests
 * come from the user running this instance of Esup-DSS-Client.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class CheckUserRequestProcessor extends RequestProcessor {

	private static final Logger logger = LoggerFactory.getLogger(CheckUserRequestProcessor.class.getName());
	
	private static final OS OPERATING_SYSTEM = EnvironmentInfo.buildFromSystemProperties(System.getProperties()).getOs();
	
	private static final String EXPECTED_USERNAME = System.getProperty("user.name");
	
	private static final String PORT_TO_PID_PATTERN_PREFIX = "\\s+TCP\\s+[0-9a-f\\.]+:";
	private static final String PORT_TO_PID_PATTERN_SUFFIX = "\\s+[0-9a-f\\.:]+\\s+ESTABLISHED\\s+([0-9]+)";

	private final UserByPIDStrategy userByPIDStrategy;
	
	public CheckUserRequestProcessor() {
		try {
			switch(OPERATING_SYSTEM) {
			case WINDOWS:
				// Use reflection to avoid any wrong initialization issues
				userByPIDStrategy = Class.forName("org.esupportail.esupdssclient.jetty.Win32JNAUserByPIDStrategy").asSubclass(
						UserByPIDStrategy.class).getDeclaredConstructor().newInstance();
				break;
			default:
				userByPIDStrategy = null;
			}
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException |
				 InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	private int getPID(final int port) {
		final Pattern pattern = Pattern.compile(PORT_TO_PID_PATTERN_PREFIX + port + PORT_TO_PID_PATTERN_SUFFIX);
		final String netstatResult = executeCommand("netstat -no");
		final Matcher matcher = pattern.matcher(netstatResult);
		if(!matcher.find()) {
			logger.error("Pattern " + pattern.toString() + " cannot match " + netstatResult);
			// Do not put netstat result, nor pattern in exception to prevent sending private information through the Internet
			throw new IllegalStateException("Cannot match pattern with netstat result");
		}
		return Integer.parseInt(matcher.group(1));
	}
	
	private String getUsername(final int pid) {
		final String username = userByPIDStrategy.getUserForPID(pid);
		logger.info("User for pid " + pid + ": " + username);
		return username;
	}
	
	private String executeCommand(final String command) {
		final NativeProcessExecutor executor = new NativeProcessExecutor(command, 10000);
		final int resultCode = executor.getResultCode();
		if(resultCode != 0) {
			throw new EsupDSSClientException("Result code of " + command + " is different from 0: " + resultCode);
		}
		return executor.getResult();
	}
}
