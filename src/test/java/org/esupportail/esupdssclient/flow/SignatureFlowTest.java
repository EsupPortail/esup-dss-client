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
package org.esupportail.esupdssclient.flow;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.ToBeSigned;
import org.esupportail.esupdssclient.EsupDSSClientException;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.esupportail.esupdssclient.api.SignatureRequest;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.Operation;
import org.esupportail.esupdssclient.api.flow.OperationResult;
import org.esupportail.esupdssclient.flow.operation.BasicOperationFactory;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.esupportail.esupdssclient.view.core.UIOperation;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignatureFlowTest {

	@Test(expected = EsupDSSClientException.class)
	public void testInputValidation1() throws Exception {

		UIDisplay display = mock(UIDisplay.class);

		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);

		SignatureRequest req = new SignatureRequest();
		req.setDigestAlgorithm(DigestAlgorithm.SHA256);

		SignatureFlow flow = new SignatureFlow(display, api);
		flow.process(api, req);

	}

	@Test(expected = EsupDSSClientException.class)
	public void testInputValidation2() throws Exception {

		UIDisplay display = mock(UIDisplay.class);

		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);

		SignatureRequest req = new SignatureRequest();
		req.setToBeSigned(new ToBeSigned());

		SignatureFlow flow = new SignatureFlow(display, api);
		flow.process(api, req);

	}

	@Test(expected = EsupDSSClientException.class)
	public void testInputValidation3() throws Exception {

		UIDisplay display = mock(UIDisplay.class);

		EsupDSSClientAPI api = mock(EsupDSSClientAPI.class);

		SignatureRequest req = new SignatureRequest();
		req.setToBeSigned(new ToBeSigned("hello".getBytes()));

		SignatureFlow flow = new SignatureFlow(display, api);
		flow.process(api, req);

	}

	private static class NoUIOperationFactory extends BasicOperationFactory {

		@SuppressWarnings("rawtypes")
		private final Operation successOperation;

		public NoUIOperationFactory() {
			this.successOperation = mock(Operation.class);
			when(successOperation.perform()).thenReturn(new OperationResult<Void>(BasicOperationStatus.SUCCESS));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <R, T extends Operation<R>> Operation<R> getOperation(Class<T> clazz, Object... params) {
			if (UIOperation.class.isAssignableFrom(clazz)) {
				return successOperation;
			} else {
				return super.getOperation(clazz, params);
			}
		}
	}
}
