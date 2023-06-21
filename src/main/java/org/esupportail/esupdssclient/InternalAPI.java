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
package org.esupportail.esupdssclient;

import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.flow.BasicOperationStatus;
import org.esupportail.esupdssclient.api.flow.OperationFactory;
import org.esupportail.esupdssclient.api.plugin.HttpPlugin;
import org.esupportail.esupdssclient.cache.FIFOCache;
import org.esupportail.esupdssclient.flow.Flow;
import org.esupportail.esupdssclient.flow.FlowRegistry;
import org.esupportail.esupdssclient.flow.operation.CoreOperationStatus;
import org.esupportail.esupdssclient.generic.ConnectionInfo;
import org.esupportail.esupdssclient.generic.SCDatabase;
import org.esupportail.esupdssclient.view.core.UIDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Implementation of the API
 * 
 * @author David Naramski
 *
 */
@SuppressWarnings("restriction")
public class InternalAPI implements EsupDSSClientAPI {

	public static final ThreadGroup EXECUTOR_THREAD_GROUP = new ThreadGroup("ExecutorThreadGroup");
	
	private Logger logger = LoggerFactory.getLogger(InternalAPI.class.getName());

//	private CardDetector detector;

	private List<ProductAdapter> adapters = new ArrayList<>();

	private Map<TokenId, SignatureTokenConnection> connections;

	private Map<String, HttpPlugin> httpPlugins = new HashMap<>();

	private UIDisplay display;

	private SCDatabase myDatabase;

	private FlowRegistry flowRegistry;

	private OperationFactory operationFactory;
	
	private AppConfig appConfig;
	
	private ExecutorService executor;

	private Future<?> currentTask;
	
	public InternalAPI(UIDisplay display, SCDatabase myDatabase, FlowRegistry flowRegistry, OperationFactory operationFactory, AppConfig appConfig) {
		this.display = display;
		this.myDatabase = myDatabase;
		this.flowRegistry = flowRegistry;
		this.operationFactory = operationFactory;
		this.appConfig = appConfig;
		this.connections = new FIFOCache<>(this.appConfig.getConnectionsCacheMaxSize());
		this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				final Thread t = new Thread(EXECUTOR_THREAD_GROUP, r);
				t.setDaemon(true);
				return t;
			}
		});
		this.currentTask = null;
	}

	@Override
	public List<Match> matchingProductAdapters(Product p) {
		if (p == null) {
			logger.warn("Product argument should not be null");
			return Collections.emptyList();
		}
		List<Match> matches = new ArrayList<>();
		for (ProductAdapter adapter : adapters) {
			if (adapter.accept(p)) {
				logger.info("Product is instance of " + adapter.getClass().getSimpleName());
				matches.add(new Match(adapter, p));
			}
		}
		return matches;
	}

	@Override
	public void registerProductAdapter(ProductAdapter adapter) {
		adapters.add(adapter);
	}

	@Override
	public EnvironmentInfo getEnvironmentInfo() {
		EnvironmentInfo info = EnvironmentInfo.buildFromSystemProperties(System.getProperties());
		return info;
	}

	@Override
	public TokenId registerTokenConnection(SignatureTokenConnection connection) {
		TokenId id = new TokenId();
		connections.put(id, connection);
		return id;
	}

	@Override
	public SignatureTokenConnection getTokenConnection(TokenId tokenId) {
		return connections.get(tokenId);
	}

	private <I, O> Execution<O> executeRequest(Flow<I, O> flow, I request) {
		Execution<O> resp = null;

		try {
			if(!EXECUTOR_THREAD_GROUP.equals(Thread.currentThread().getThreadGroup())) {
				final Future<Execution<O>> task;
				// Prevent race condition on currentTask
				synchronized (this) {
					if((currentTask != null) && !currentTask.isDone()) {
						currentTask.cancel(true);
					}

					task = executor.submit(() -> {
						return flow.execute(this, request);
					});
					currentTask = task;
				}

				resp = task.get();
			} else {
				// Allow re-entrant calls
				resp = flow.execute(this, request);
			}
			if(resp == null) {
				resp = new Execution<O>(CoreOperationStatus.NO_RESPONSE);
			}
			return resp;
		}  catch (Exception e) {
			resp = new Execution<O>(BasicOperationStatus.EXCEPTION);
			logger.error("Cannot execute request", e);
			final Feedback feedback = new Feedback(e);
			resp.setFeedback(feedback);
			return resp;
		} finally {
			final Feedback feedback;
			if(resp.getFeedback() == null) {
				feedback = new Feedback();
				resp.setFeedback(feedback);
			} else {
				feedback = resp.getFeedback();
			}
			feedback.setApiVersion(this.getAppConfig().getApplicationVersion());
			feedback.setInfo(this.getEnvironmentInfo());
		}
	}

	@Override
	public Execution<GetCertificateResponse> getCertificate(GetCertificateRequest request) {
		Flow<GetCertificateRequest, GetCertificateResponse> flow =
				flowRegistry.getFlow(FlowRegistry.CERTIFICATE_FLOW, display, this);
		flow.setOperationFactory(operationFactory);
		return executeRequest(flow, request);
	}

	@Override
	public Execution<SignatureResponse> sign(SignatureRequest request) {
		Flow<SignatureRequest, SignatureResponse> flow =
				flowRegistry.getFlow(FlowRegistry.SIGNATURE_FLOW, display, this);
		flow.setOperationFactory(operationFactory);
		return executeRequest(flow, request);
	}

	@Override
	public Execution<GetIdentityInfoResponse> getIdentityInfo(GetIdentityInfoRequest request) {
		final Flow<GetIdentityInfoRequest, GetIdentityInfoResponse> flow =
				flowRegistry.getFlow(FlowRegistry.GET_IDENTITY_INFO_FLOW, display, this);
		flow.setOperationFactory(operationFactory);
		return executeRequest(flow, request);
	}
	
	@Override
	public Execution<AuthenticateResponse> authenticate(AuthenticateRequest request) {
		final Flow<AuthenticateRequest, AuthenticateResponse> flow =
				flowRegistry.getFlow(FlowRegistry.AUTHENTICATE_FLOW, display, this);
		flow.setOperationFactory(operationFactory);
		return executeRequest(flow, request);
	}
	
	@Override
	public HttpPlugin getHttpPlugin(String context) {
		return httpPlugins.get(context);
	}

	public void registerHttpContext(String context, HttpPlugin plugin) {
		httpPlugins.put(context, plugin);
	}

	public void store(String detectedAtr, ScAPI selectedApi, String apiParam) {
		if (myDatabase != null) {

			EnvironmentInfo env = getEnvironmentInfo();
			ConnectionInfo cInfo = new ConnectionInfo();
			cInfo.setSelectedApi(selectedApi);
			cInfo.setEnv(env);
			cInfo.setApiParam(apiParam);

			myDatabase.add(detectedAtr, cInfo);
		}
	}

	@Override
	public AppConfig getAppConfig() {
		return appConfig;
	}

	@Override
	public List<SystrayMenuItem> getExtensionSystrayMenuItems() {
		final List<SystrayMenuItem> result = new ArrayList<>();
		for(final ProductAdapter adapter : adapters) {
			final SystrayMenuItem menuItem = adapter.getExtensionSystrayMenuItem();
			if(menuItem != null) {
				result.add(menuItem);
			}
		}
		return result;
	}

	@Override
	public List<Product> detectProducts() {
		final List<Product> result = new ArrayList<>();
		for(final ProductAdapter adapter : adapters) {
			result.addAll(adapter.detectProducts());
		}
		return result;
	}

	@Override
	public String getLabel(Product p) {
		final List<Match> matches = this.matchingProductAdapters(p);
		if(matches.isEmpty()) {
			return p.getLabel();
		} else {
			final ProductAdapter adapter = matches.iterator().next().getAdapter();
			if(adapter.supportMessageDisplayCallback(p)) {
				return adapter.getLabel(this, p, display.getPasswordInputCallback(), display.getMessageDisplayCallback());
			} else {
				return adapter.getLabel(this, p, display.getPasswordInputCallback());
			}
		}
	}
}
