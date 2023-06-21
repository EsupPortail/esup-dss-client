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
import org.esupportail.esupdssclient.api.Match;
import org.esupportail.esupdssclient.api.Product;
import org.esupportail.esupdssclient.api.ProductAdapter;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.FutureOperationInvocation;
import org.esupportail.esupdssclient.api.flow.OperationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * This {@link CompositeOperation} allows a {@link ProductAdapter} to configure a {@link Product}.
 *
 * <p>Expected parameters:
 * <ol>
 * <li>List of {@link Match}.</li>
 * <li>{@link EsupDSSClientAPI}</li>
 * </ol>
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class ConfigureProductOperation extends AbstractCompositeOperation<List<Match>> {

	private List<Match> matches;
	private EsupDSSClientAPI api;
	
	@Override
	@SuppressWarnings("unchecked")
	public void setParams(Object... params) {
		try {
			this.matches = (List<Match>) params[0];
			this.api = (EsupDSSClientAPI) params[1];
		} catch(final ArrayIndexOutOfBoundsException | ClassCastException e) {
			throw new IllegalArgumentException("Expected parameters: list of Match, API");
		}
	}

	@Override
	public OperationResult<List<Match>> perform() {
		final List<Match> result = new ArrayList<>(matches.size());
		for(final Match match : matches) {
			final OperationResult<Product> op = handleMatch(match.getAdapter(), match.getProduct());
			if(op.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				result.add(new Match(match.getAdapter(), op.getResult()));
			} else {
				if(op.getStatus().equals(BasicOperationStatus.EXCEPTION)) {
					return new OperationResult<List<Match>>(op.getException());
				} else {
					return new OperationResult<List<Match>>(op.getStatus());
				}
			}
		}
		return new OperationResult<List<Match>>(result);
	}
	
	private OperationResult<Product> handleMatch(final ProductAdapter productAdapter, final Product product) {
		final FutureOperationInvocation<Product> futureOperationInvocation = productAdapter.getConfigurationOperation(api, product);
		return futureOperationInvocation.call(operationFactory);
	}
}
