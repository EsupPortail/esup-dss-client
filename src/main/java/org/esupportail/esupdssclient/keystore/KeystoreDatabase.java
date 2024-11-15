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
package org.esupportail.esupdssclient.keystore;

import jakarta.xml.bind.annotation.*;
import org.esupportail.esupdssclient.DatabaseEventHandler;
import org.esupportail.esupdssclient.ProductDatabase;
import org.esupportail.esupdssclient.api.ConfiguredKeystore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@XmlRootElement(name = "database")
@XmlAccessorType(XmlAccessType.FIELD)
public class KeystoreDatabase implements ProductDatabase {

	private static final Logger logger = LoggerFactory.getLogger(KeystoreDatabase.class.getName());

	@XmlElement(name = "keystore")
	private List<ConfiguredKeystore> keystores;

	@XmlTransient
	private DatabaseEventHandler onAddRemoveAction;

	/**
	 * Adds a new {@link ConfiguredKeystore} to the database.
	 * @param keystore The keystore to add.
	 */
	public final void add(final ConfiguredKeystore keystore) {
		getKeystores0().add(keystore);
		onAddRemove();
	}

	/**
	 * Removes the given {@link ConfiguredKeystore} from the database.
	 * @param keystore The keystore to remove.
	 */
	public final void remove(final ConfiguredKeystore keystore) {
		getKeystores0().remove(keystore);
		onAddRemove();
	}
	
	private void onAddRemove() {
		if(onAddRemoveAction != null) {
			onAddRemoveAction.execute(this);
		} else {
			logger.warn("No DatabaseEventHandler define, the database cannot be stored");
		}
	}

	private List<ConfiguredKeystore> getKeystores0() {
		if (keystores == null) {
			this.keystores = new ArrayList<>();
		}
		return keystores;
	}
	
	public List<ConfiguredKeystore> getKeystores() {
		return Collections.unmodifiableList(getKeystores0());
	}

	@Override
	public void setOnAddRemoveAction(DatabaseEventHandler eventHandler) {
		this.onAddRemoveAction = eventHandler;
	}
}
