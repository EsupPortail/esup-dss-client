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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Properties;

/**
 * Represents the information collected on the user environment
 * 
 * @author David Naramski
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "environment", propOrder = { "jreVersion", "osName", "osArch", "osVersion", "arch", "os" })
public class EnvironmentInfo {

	private static final String OS_VERSION = "os.version";

	private static final String OS_NAME = "os.name";

	private static final String JAVA_VERSION = "java.version";

	private static final String OS_ARCH = "os.arch";

	private JREVendor jreVersion;

	private String osName;

	private String osArch;

	private String osVersion;

	private Arch arch;

	private OS os;

	public EnvironmentInfo() {
	}

	public static EnvironmentInfo buildFromSystemProperties(Properties systemProperties) {
		EnvironmentInfo info = new EnvironmentInfo();

		String osArch = systemProperties.getProperty(OS_ARCH);
		info.setOsArch(osArch);
		info.setArch(Arch.forOSArch(osArch));

		info.setJreVersion(JREVendor.forJREVersion(System.getProperty(JAVA_VERSION)));

		String osName = systemProperties.getProperty(OS_NAME);
		info.setOsName(osName);
		info.setOs(OS.forOSName(osName));

		String osVersion = systemProperties.getProperty(OS_VERSION);
		info.setOsVersion(osVersion);

		return info;
	}

	/**
	 * Compare the filter value with the EnvironmentInfo to see if there is a match.
	 * 
	 * @param env
	 * @return true if the EnvironmentInfo matches the filter
	 */
	public boolean matches(EnvironmentInfo env) {
		if (os != null && os != env.getOs()) {
			return false;
		}
		if (arch != null && arch != env.getArch()) {
			return false;
		}
		return true;
	}

	public JREVendor getJreVersion() {
		return jreVersion;
	}

	public void setJreVersion(JREVendor jreVersion) {
		this.jreVersion = jreVersion;
	}

	public String getOsVersion() {
		return osVersion;
	}

	public void setOsVersion(String osVersion) {
		this.osVersion = osVersion;
	}

	public Arch getArch() {
		return arch;
	}

	public void setArch(Arch arch) {
		this.arch = arch;
	}

	public OS getOs() {
		return os;
	}

	public void setOs(OS os) {
		this.os = os;
	}

	public String getOsName() {
		return osName;
	}

	public void setOsName(String osName) {
		this.osName = osName;
	}

	public String getOsArch() {
		return osArch;
	}

	public void setOsArch(String osArch) {
		this.osArch = osArch;
	}

}