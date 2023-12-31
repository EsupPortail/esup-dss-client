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

public class CertificateFilter {

	private Purpose purpose;
	
	/*
	 * if false the nonRepudiation bit is not checked. If true, only certificate with nonRepudiationKeyUsage will be 
	 * returned.
	 */
	private Boolean nonRepudiationBit = false;
	
	private byte[] certificateSHA1;

	public CertificateFilter() {
		
	}
	
	public CertificateFilter(Purpose purpose) {
		this.purpose = purpose;
	}
	
	public CertificateFilter(byte[] certificateSHA1) {
		this.certificateSHA1 = certificateSHA1;
	}
	
	public Purpose getPurpose() {
		return purpose;
	}

	public void setPurpose(Purpose purpose) {
		this.purpose = purpose;
	}

	public byte[] getCertificateSHA1() {
		return certificateSHA1;
	}

	public void setCertificateSHA1(byte[] certificateSHA1) {
		this.certificateSHA1 = certificateSHA1;
	}

	public Boolean getNonRepudiationBit() {
		return nonRepudiationBit;
	}

	public void setNonRepudiationBit(Boolean nonRepudiationBit) {
		this.nonRepudiationBit = nonRepudiationBit;
	}

}
