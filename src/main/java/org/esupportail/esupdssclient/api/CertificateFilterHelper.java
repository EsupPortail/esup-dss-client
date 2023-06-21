package org.esupportail.esupdssclient.api;

import eu.europa.esig.dss.enumerations.KeyUsageBit;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides filtering capabilities for product adapters.
 * 
 * @author Landry Soules
 *
 */
public class CertificateFilterHelper {

	public List<DSSPrivateKeyEntry> filterKeys(SignatureTokenConnection token, CertificateFilter filter) {
		if (filter.getNonRepudiationBit()) {
			List<DSSPrivateKeyEntry> filteredList = new ArrayList<>();
			for (DSSPrivateKeyEntry entry : token.getKeys()) {
				if (entry.getCertificate().checkKeyUsage(KeyUsageBit.NON_REPUDIATION)) {
					filteredList.add(entry);
				}
			}
			return filteredList;
		}
		return token.getKeys();
	}
}
