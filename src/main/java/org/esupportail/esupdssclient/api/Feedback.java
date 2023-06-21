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
package org.esupportail.esupdssclient.api;

import javax.xml.bind.annotation.*;
import java.io.PrintWriter;
import java.io.StringWriter;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "feedback", propOrder = { "apiParameter", "feedbackStatus", "selectedAPI", "stacktrace", "userComment", "info", "apiVersion" })
public class Feedback {

	protected String apiParameter;
	@XmlElement(nillable = true)
	protected FeedbackStatus feedbackStatus;
	protected ScAPI selectedAPI;
	protected transient Exception exception;
	protected String stacktrace;
	protected String userComment;
	protected EnvironmentInfo info;
	protected String apiVersion;
	
	public Feedback() {
	}

	public Feedback(Exception e) {
		StringWriter buffer = new StringWriter();
		PrintWriter writer = new PrintWriter(buffer);
		e.printStackTrace(writer);
		writer.close();

		setStacktrace(buffer.toString());
		setFeedbackStatus(FeedbackStatus.EXCEPTION);
		setException(e);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((apiParameter == null) ? 0 : apiParameter.hashCode());
		result = prime * result + ((feedbackStatus == null) ? 0 : feedbackStatus.hashCode());
		result = prime * result + ((info == null) ? 0 : info.hashCode());
		result = prime * result + ((apiVersion == null) ? 0 : apiVersion.hashCode());
		result = prime * result + ((selectedAPI == null) ? 0 : selectedAPI.hashCode());
		result = prime * result + ((stacktrace == null) ? 0 : stacktrace.hashCode());
		result = prime * result + ((userComment == null) ? 0 : userComment.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Feedback other = (Feedback) obj;
		if (apiParameter == null) {
			if (other.apiParameter != null)
				return false;
		} else if (!apiParameter.equals(other.apiParameter))
			return false;
		if (feedbackStatus != other.feedbackStatus)
			return false;
		if (info == null) {
			if (other.info != null)
				return false;
		} else if (!info.equals(other.info))
			return false;
		if (apiVersion == null) {
			if (other.apiVersion != null)
				return false;
		} else if (!apiVersion.equals(other.apiVersion))
			return false;
		if (selectedAPI != other.selectedAPI)
			return false;
		if (stacktrace == null) {
			if (other.stacktrace != null)
				return false;
		} else if (!stacktrace.equals(other.stacktrace))
			return false;
		if (userComment == null) {
			if (other.userComment != null)
				return false;
		} else if (!userComment.equals(other.userComment))
			return false;
		return true;
	}

	/**
	 * Gets the value of the apiParameter property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	public String getApiParameter() {
		return apiParameter;
	}

	/**
	 * Sets the value of the apiParameter property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
	public void setApiParameter(String value) {
		this.apiParameter = value;
	}

	/**
	 * Gets the value of the feedbackStatus property.
	 * 
	 * @return possible object is {@link FeedbackStatus }
	 * 
	 */
	public FeedbackStatus getFeedbackStatus() {
		return feedbackStatus;
	}

	/**
	 * Sets the value of the feedbackStatus property.
	 * 
	 * @param value
	 *            allowed object is {@link FeedbackStatus }
	 * 
	 */
	public void setFeedbackStatus(FeedbackStatus value) {
		this.feedbackStatus = value;
	}

	/**
	 * Gets the value of the selectedAPI property.
	 * 
	 * @return possible object is {@link ScAPI }
	 * 
	 */
	public ScAPI getSelectedAPI() {
		return selectedAPI;
	}

	/**
	 * Sets the value of the selectedAPI property.
	 * 
	 * @param value
	 *            allowed object is {@link ScAPI }
	 * 
	 */
	public void setSelectedAPI(ScAPI value) {
		this.selectedAPI = value;
	}

	/**
	 * Gets the value of the stacktrace property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	public String getStacktrace() {
		return stacktrace;
	}

	/**
	 * Sets the value of the stacktrace property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
	public void setStacktrace(String value) {
		this.stacktrace = value;
	}

	public String getUserComment() {
		return userComment;
	}

	public void setUserComment(String userComment) {
		this.userComment = userComment;
	}

	public EnvironmentInfo getInfo() {
		return info;
	}

	public void setInfo(EnvironmentInfo info) {
		this.info = info;
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}
}
