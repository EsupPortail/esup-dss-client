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
package org.esupportail.esupdssclient.object.model;

/**
 * POJO that encapsulates some feedback information. 
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class Feedback {

	private FeedbackStatus feedbackStatus;
	private String stacktrace;
	private String userComment;
	private EnvironmentInfo info;
	private String apiVersion;
	
	public Feedback() {
		super();
	}

	public FeedbackStatus getFeedbackStatus() {
		return feedbackStatus;
	}

	public void setFeedbackStatus(FeedbackStatus feedbackStatus) {
		this.feedbackStatus = feedbackStatus;
	}

	public String getStacktrace() {
		return stacktrace;
	}

	public void setStacktrace(String stacktrace) {
		this.stacktrace = stacktrace;
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

	public String getapiVersion() {
		return apiVersion;
	}

	public void setapiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}
}
