package org.esupportail.esupdssclient.dssclient;

class DssClientSigningCoordinator {

	private DssClientWebSocketService owner;

	public synchronized boolean acquire(DssClientWebSocketService candidate) {
		if (owner == null) {
			owner = candidate;
		}
		return owner == candidate;
	}

	public synchronized boolean release(DssClientWebSocketService candidate) {
		if (owner == candidate) {
			owner = null;
			return true;
		}
		return false;
	}
}
