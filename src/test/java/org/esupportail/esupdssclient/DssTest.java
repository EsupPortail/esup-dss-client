package org.esupportail.esupdssclient;


import eu.europa.esig.dss.enumerations.KeyUsageBit;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.extension.CertificatePolicies;
import eu.europa.esig.dss.model.x509.extension.CertificatePolicy;
import eu.europa.esig.dss.model.x509.extension.QcStatements;
import eu.europa.esig.dss.spi.CertificateExtensionsUtils;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.QcStatementUtils;

import java.util.List;

public class DssTest {

	public static void main(String args[]) {

		CertificateToken certificateToken = DSSUtils.loadCertificate(EsupDSSClientApplication.class.getClassLoader().getResourceAsStream("dummy-cert.pem"));
		System.out.println("certificate token : " + certificateToken.toString());

		CertificatePolicies policies = CertificateExtensionsUtils.getCertificatePolicies(certificateToken);

		if(policies != null) {
			for (CertificatePolicy policy : policies.getPolicyList()) {
				System.out.println(policy.getOid() + " " + policy.getCpsUrl());
			}
		}

		QcStatements qcStatementsIdList = QcStatementUtils.getQcStatements(certificateToken);
		System.out.println(qcStatementsIdList);

		List<KeyUsageBit> keyUsageBits = certificateToken.getKeyUsageBits();
		for (KeyUsageBit keyUsageBit : keyUsageBits) {
			System.out.println(keyUsageBit);
		}

		System.out.println("----------");

		System.out.println("Issuer : " + certificateToken.getIssuerX500Principal().getName());
		System.out
				.println("Other data : " + DSSASN1Utils.get(certificateToken.getIssuerX500Principal()).get("2.5.4.3"));
		System.out.println("Extended key usage : " + DSSASN1Utils.getExtendedKeyUsage(certificateToken));
		List<KeyUsageBit> kubs = certificateToken.getKeyUsageBits();
		for (KeyUsageBit kub : kubs) {
			System.out.println("Usage : " + kub.name() + " | " + kub.toString());
		}

	}
}
