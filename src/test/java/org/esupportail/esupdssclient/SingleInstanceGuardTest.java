package org.esupportail.esupdssclient;

import org.junit.After;
import org.junit.Test;

import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SingleInstanceGuardTest {

	private Path lockFile;

	@After
	public void releaseLock() throws Exception {
		SingleInstanceGuard.release();
		if (lockFile != null) {
			Files.deleteIfExists(lockFile);
		}
	}

	@Test
	public void holdsAnOperatingSystemFileLock() throws Exception {
		String applicationName = "esup-dss-client-test-" + UUID.randomUUID();
		assertTrue(SingleInstanceGuard.acquire(applicationName));
		lockFile = Path.of(System.getProperty("user.home"), ".esup-dss-client",
				applicationName + ".lock");

		try (FileChannel secondChannel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
			assertThrows(OverlappingFileLockException.class, secondChannel::tryLock);
		}
	}

	@Test
	public void refusesASecondJvmProcess() throws Exception {
		String applicationName = "esup-dss-client-test-" + UUID.randomUUID();
		assertTrue(SingleInstanceGuard.acquire(applicationName));
		lockFile = Path.of(System.getProperty("user.home"), ".esup-dss-client",
				applicationName + ".lock");
		String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
		Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
				SingleInstanceProbe.class.getName(), applicationName)
				.redirectErrorStream(true)
				.start();

		assertTrue(process.waitFor(10, TimeUnit.SECONDS));
		assertEquals(new String(process.getInputStream().readAllBytes()), 0, process.exitValue());
	}

	@Test
	public void refusesStartupWhenLockCannotBeCreated() throws Exception {
		String originalUserHome = System.getProperty("user.home");
		Path invalidHome = Files.createTempFile("esup-dss-client-home", ".tmp");
		try {
			System.setProperty("user.home", invalidHome.toString());

			assertFalse(SingleInstanceGuard.acquire("esup-dss-client"));
		} finally {
			System.setProperty("user.home", originalUserHome);
			Files.deleteIfExists(invalidHome);
		}
	}
}
