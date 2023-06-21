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
package org.esupportail.esupdssclient.flow.operation;

import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.Match;
import org.esupportail.esupdssclient.api.TokenId;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.OperationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This {@link CompositeOperation} allows to get or create a {@link TokenId}.
 *
 * <p>Expected parameters:
 * <ol>
 * <li>{@link EsupDSSClientAPI}</li>
 * <li>{@link TokenId}</li>
 * </ol>
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class GetTokenOperation extends AbstractCompositeOperation<Map<TokenOperationResultKey, Object>> {

	private EsupDSSClientAPI api;
	private TokenId previousTokenId;
	
	public GetTokenOperation() {
		super();
	}

	@Override
	public void setParams(Object... params) {
		try {
			this.api = (EsupDSSClientAPI) params[0];
			this.previousTokenId = (TokenId) params[1];
		} catch(final ArrayIndexOutOfBoundsException | ClassCastException e) {
			throw new IllegalArgumentException("Expected parameters: API, TokenId");
		}
	}

	@Override
	public OperationResult<Map<TokenOperationResultKey, Object>> perform() {
		if(previousTokenId != null) {
			final Map<TokenOperationResultKey, Object> map = new HashMap<TokenOperationResultKey, Object>();
			map.put(TokenOperationResultKey.ADVANCED_CREATION, false);
			map.put(TokenOperationResultKey.TOKEN_ID, previousTokenId);
			return new OperationResult<Map<TokenOperationResultKey, Object>>(map);
		} else {
			final OperationResult<List<Match>> getMatchingProductAdaptersOperationResult =
					operationFactory.getOperation(GetMatchingProductAdaptersOperation.class, null, api).perform();
			if(getMatchingProductAdaptersOperationResult.getStatus().equals(BasicOperationStatus.SUCCESS)) {
				final List<Match> matchingProductAdapters = getMatchingProductAdaptersOperationResult.getResult();
				return operationFactory.getOperation(CreateTokenOperation.class, api, matchingProductAdapters).perform();
			} else {
				if(getMatchingProductAdaptersOperationResult.getStatus().equals(BasicOperationStatus.EXCEPTION)) {
					return new OperationResult<Map<TokenOperationResultKey,Object>>(getMatchingProductAdaptersOperationResult.getException());
				} else {
					return new OperationResult<Map<TokenOperationResultKey,Object>>(getMatchingProductAdaptersOperationResult.getStatus());
				}
			}
		}
	}
}
