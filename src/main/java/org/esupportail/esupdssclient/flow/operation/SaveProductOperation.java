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
package org.esupportail.esupdssclient.flow.operation;

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Product;
import org.esupportail.esupdssclient.api.ProductAdapter;
import org.esupportail.esupdssclient.api.flow.OperationResult;

/**
 * This {@link CompositeOperation} allows a {@link ProductAdapter} to save a {@link Product}.
 *
 * <p>Expected parameters:
 * <ol>
 * <li>{@link ProductAdapter}</li>
 * <li>{@link Product}</li>
 * <li>{@link EsupDSSClientAPI}</li>
 * </ol>
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class SaveProductOperation extends AbstractCompositeOperation<Boolean> {

	private ProductAdapter adapter;
	private Product product;
	private EsupDSSClientAPI api;
	
	@Override
	public void setParams(Object... params) {
		try {
			this.adapter = (ProductAdapter) params[0];
			this.product = (Product) params[1];
			this.api = (EsupDSSClientAPI) params[2];
		} catch(final ArrayIndexOutOfBoundsException | ClassCastException e) {
			throw new IllegalArgumentException("Expected parameters: ProductAdapter, Product, API");
		}
	}

	@Override
	public OperationResult<Boolean> perform() {
		return adapter.getSaveOperation(api, product).call(operationFactory);
	}
}
