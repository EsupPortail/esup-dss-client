package org.esupportail.esupdssclient.dssclient;

import org.esupportail.esupdssclient.StandaloneUIDisplay;
import org.esupportail.esupdssclient.UserPreferences;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DssClientConnectionManager {

	public enum ConnectionStatus {
		CONNECTING("Connexion..."),
		WAITING_APPROVAL("En attente d'autorisation"),
		CONNECTED("Lien vérifié · connecté"),
		DISCONNECTED("Serveur injoignable"),
		REVOKING("Révocation en cours..."),
		REVOCATION_FAILED("Échec de la révocation · réessayez");

		private final String label;

		ConnectionStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	private final EsupDSSClientAPI api;
	private final UserPreferences preferences;
	private final StandaloneUIDisplay uiDisplay;
	private final DssClientSigningCoordinator signingCoordinator = new DssClientSigningCoordinator();
	private final Map<String, DssClientWebSocketService> services = new ConcurrentHashMap<>();
	private final Map<String, ConnectionStatus> statuses = new ConcurrentHashMap<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	public DssClientConnectionManager(EsupDSSClientAPI api, UserPreferences preferences, StandaloneUIDisplay uiDisplay) {
		this.api = api;
		this.preferences = preferences;
		this.uiDisplay = uiDisplay;
	}

	public synchronized void startIfConfigured() {
		refreshConnections();
	}

	public synchronized void refreshConnections() {
		List<DssClientAssociation> associations = preferences.getDssClientAssociations();
		services.entrySet().removeIf(entry -> {
			boolean removed = associations.stream().noneMatch(association -> association.getDeviceId().equals(entry.getKey()));
			if (removed) {
				entry.getValue().stop();
				statuses.remove(entry.getKey());
			}
			return removed;
		});

		for (DssClientAssociation association : associations) {
			services.computeIfAbsent(association.getDeviceId(), ignored -> createService(association)).connect();
		}
		notifyListeners();
	}

	public void revokeAssociation(DssClientAssociation association) {
		synchronized (this) {
			if (statuses.get(association.getDeviceId()) == ConnectionStatus.REVOKING) {
				return;
			}
			statuses.put(association.getDeviceId(), ConnectionStatus.REVOKING);
			notifyListeners();
		}
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				new DssClientPairingService(preferences, api.getAppConfig().getApplicationVersion()).revoke(association);
				removeAssociationLocally(association.getDeviceId());
			} catch (Exception e) {
				statuses.put(association.getDeviceId(), ConnectionStatus.REVOCATION_FAILED);
				notifyListeners();
			}
		});
	}

	private synchronized void removeAssociationLocally(String deviceId) {
		DssClientWebSocketService service = services.remove(deviceId);
		if (service != null) {
			service.stop();
		}
		statuses.remove(deviceId);
		preferences.removeDssClientAssociation(deviceId);
		notifyListeners();
	}

	public ConnectionStatus getStatus(String deviceId) {
		return statuses.getOrDefault(deviceId, ConnectionStatus.DISCONNECTED);
	}

	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	public synchronized void stop() {
		services.values().forEach(DssClientWebSocketService::stop);
		services.clear();
		statuses.clear();
		notifyListeners();
	}

	private DssClientWebSocketService createService(DssClientAssociation association) {
		return new DssClientWebSocketService(api, association, uiDisplay, signingCoordinator,
				status -> updateStatus(association.getDeviceId(), status),
				() -> removeAssociationLocally(association.getDeviceId()));
	}

	private void updateStatus(String deviceId, ConnectionStatus status) {
		if (!services.containsKey(deviceId)) {
			return;
		}
		if (statuses.get(deviceId) == ConnectionStatus.REVOKING) {
			return;
		}
		statuses.put(deviceId, status);
		notifyListeners();
	}

	private void notifyListeners() {
		listeners.forEach(Runnable::run);
	}
}
