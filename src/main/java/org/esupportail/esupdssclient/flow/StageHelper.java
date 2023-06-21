package org.esupportail.esupdssclient.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.apache.commons.lang.StringUtils.isBlank;

public class StageHelper {

	private static StageHelper instance;
	
	private static final Logger logger = LoggerFactory.getLogger(StageHelper.class);

	private String title;
	
	private ResourceBundle bundle;
	
	private static final String BUNDLE_NAME = "bundles/api";

	private StageHelper() {
	}

	public static synchronized StageHelper getInstance() {
		if (instance == null) {
			synchronized (StageHelper.class) {
				if (instance == null) {
					instance = new StageHelper();
					instance.setBundle(ResourceBundle.getBundle(BUNDLE_NAME));
				}
			}
		}
		return instance;
	}

	public String getTitle() {
		return title;
	}
	
	public void setTitle(final String applicationName, final String resourceBundleKey) {
		if(isBlank(applicationName) && isBlank(resourceBundleKey)) {
			title = "";
			return;
		}
		String translatedTitle = "";
		try {
			translatedTitle = getBundle().getString(resourceBundleKey);
		} catch (MissingResourceException mre) {
			logger.warn("Resource bundle key \"{}\" is missing.", resourceBundleKey);
		}catch(Exception e) {
			logger.error(e.getMessage(), e);
		}
		if(!isBlank(applicationName) && !isBlank(translatedTitle)) {
			title = applicationName + " - " + translatedTitle;
		} else if(isBlank(applicationName)) {
			title = translatedTitle;
		} else if(isBlank(translatedTitle)) {
			title = applicationName;
		} else {
			title = "";
		}
	}

	public ResourceBundle getBundle() {
		return bundle;
	}

	public void setBundle(ResourceBundle bundle) {
		this.bundle = bundle;
	}
}
