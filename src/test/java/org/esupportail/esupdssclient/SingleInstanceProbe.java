package org.esupportail.esupdssclient;

/** Helper process used to verify that the single-instance lock crosses JVM boundaries. */
public final class SingleInstanceProbe {

	private SingleInstanceProbe() {
	}

	public static void main(String[] args) {
		boolean unexpectedlyAcquired = SingleInstanceGuard.acquire(args[0]);
		if (unexpectedlyAcquired) {
			SingleInstanceGuard.release();
			System.exit(1);
		}
	}
}
