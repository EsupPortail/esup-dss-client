package org.esupportail.esupdssclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.nio.file.StandardOpenOption;

/** Keeps the desktop client to one JVM without relying on the legacy HTTP server. */
final class SingleInstanceGuard {

	private static final Logger logger = LoggerFactory.getLogger(SingleInstanceGuard.class);
	private static FileChannel channel;
	private static FileLock lock;
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE);

	private SingleInstanceGuard() {
	}

	static synchronized boolean acquire(String applicationName) {
		if (lock != null && lock.isValid()) {
			return true;
		}

		try {
			Path directory = Path.of(System.getProperty("user.home"), ".esup-dss-client");
			Files.createDirectories(directory);
			setPosixPermissions(directory, DIRECTORY_PERMISSIONS);
			Path lockFile = directory.resolve(safeFileName(applicationName) + ".lock");
			channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			setPosixPermissions(lockFile, FILE_PERMISSIONS);
			lock = channel.tryLock();
			if (lock == null) {
				closeChannel();
				logger.warn("Esup-DSS-Client is already running; refusing to start a second instance.");
				return false;
			}
			Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceGuard::release, "esup-dss-client-lock-release"));
			logger.info("Single-instance lock acquired at {}", lockFile);
			return true;
		} catch (OverlappingFileLockException e) {
			closeChannel();
			logger.warn("Esup-DSS-Client is already running in this JVM.");
			return false;
		} catch (IOException e) {
			closeChannel();
			logger.error("Unable to acquire single-instance lock; refusing startup: {}", e.getMessage());
			return false;
		}
	}

	static synchronized void release() {
		try {
			if (lock != null && lock.isValid()) {
				lock.release();
			}
		} catch (IOException e) {
			logger.debug("Unable to release single-instance lock", e);
		} finally {
			lock = null;
			closeChannel();
		}
	}

	private static void closeChannel() {
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException e) {
				logger.debug("Unable to close single-instance lock channel", e);
			} finally {
				channel = null;
			}
		}
	}

	private static String safeFileName(String applicationName) {
		return applicationName == null ? "esup-dss-client" : applicationName.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
			Files.setPosixFilePermissions(path, permissions);
		}
	}
}
