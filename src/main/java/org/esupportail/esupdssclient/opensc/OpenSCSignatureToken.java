package org.esupportail.esupdssclient.opensc;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.esupportail.esupdssclient.EsupDSSClientLauncher;
import org.esupportail.esupdssclient.api.OpenSC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Product adapter for {@link OpenSC}.
 *
 * @author David Lemaignent (david.lemaignent@univ-rouen.fr)
 */
public class OpenSCSignatureToken implements SignatureTokenConnection {

    private static final Logger logger = LoggerFactory.getLogger(OpenSCSignatureToken.class);
    private static final Pattern OPENSC_HEX_ID_PATTERN = Pattern.compile("\\(\\s*0x([0-9a-fA-F:\\s]+)\\s*\\)");
    private static final Pattern OPENSC_DIRECT_HEX_ID_PATTERN = Pattern.compile("^0x([0-9a-fA-F:]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENSC_PIN_ARGUMENT_PATTERN = Pattern.compile("(?i)(\\s(?:-p|--pin)\\s+)([^\\s]+)");
    private static final Pattern OPENSC_PIN_EQUALS_ARGUMENT_PATTERN = Pattern.compile("(?i)(\\s--pin=)([^\\s]+)");
    private final KeyStore.PasswordProtection passwordProtection;
    private String module = "";
    private String slot = "";
    private String resolvedId;

    public OpenSCSignatureToken(KeyStore.PasswordProtection passwordProtection) {
        this.passwordProtection = passwordProtection;
            if(StringUtils.isNotBlank(EsupDSSClientLauncher.getProperties().getProperty("opensc_command_module"))) {
            this.module += " --module " + EsupDSSClientLauncher.getProperties().getProperty("opensc_command_module");
        }
        detectSlot();
    }

    private void detectSlot() {
        try {
            String command = "pkcs11-tool -L" + module;
            byte[] output = launchProcess(command, false);
            String outputStr = new String(output);
            String[] lines = outputStr.split("\n");
            logger.debug("OpenSC>>>pkcs11-tool -L output:\n{}", outputStr);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().startsWith("Slot") && line.contains("(") && line.contains(")")) {
                    logger.debug("OpenSC>>>Checking line: {}", line);
                    
                    // Vérifier si un token est présent dans ce slot
                    boolean tokenPresent = false;
                    for (int j = i + 1; j < lines.length && j < i + 10; j++) {
                        String nextLine = lines[j];
                        if (nextLine.trim().startsWith("Slot")) break;
                        if (nextLine.contains("token label")) {
                            tokenPresent = true;
                            break;
                        }
                    }

                    if (tokenPresent) {
                        String slotId = line.substring(line.indexOf("(") + 1, line.indexOf(")")).trim();
                        this.slot = " --slot " + slotId;
                        logger.info("OpenSC>>>Detected slot with token: {}", slotId);
                        break;
                    } else {
                        logger.debug("OpenSC>>>No token detected in slot: {}", line);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("OpenSC>>>Failed to detect slot: {}", e.getMessage());
        }
    }

    @Override
    public void close() {

    }

    @Override
    public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
        final InputStream inputStream = new ByteArrayInputStream(toBeSigned.getBytes());
        if (!(dssPrivateKeyEntry instanceof OpenSCPrivateKeyEntry)) {
            throw new DSSException("Unsupported DSSPrivateKeyEntry instance " + dssPrivateKeyEntry.getClass() + " / Must be OpenSCPrivateKeyEntry.");
        }
        final EncryptionAlgorithm encryptionAlgo = dssPrivateKeyEntry.getEncryptionAlgorithm();
        final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.getAlgorithm(encryptionAlgo, digestAlgorithm);

        logger.info("OpenSC>>>Signature algorithm: {}", signatureAlgorithm.getJCEId());
        File tmpDir = null;
        try {
            String password = String.valueOf(passwordProtection.getPassword());
            tmpDir = Files.createTempDirectory("esupdssclient").toFile();
            tmpDir.deleteOnExit();
            File toSignFile = new File(tmpDir + "/toSign");
            FileUtils.copyInputStreamToFile(inputStream, toSignFile);
            File signedFile = new File(tmpDir + "/signed");
            String command = MessageFormat.format(EsupDSSClientLauncher.getProperties().getProperty("opensc_command_sign"), getId(), password, toSignFile.getAbsolutePath(), signedFile.getAbsolutePath());
            launchProcess(command + module + slot);
            SignatureValue value = new SignatureValue();
            value.setAlgorithm(signatureAlgorithm);
            value.setValue(FileUtils.readFileToByteArray(signedFile));
            toSignFile.delete();
            signedFile.delete();
            return value;
        } catch (IOException e) {
            throw new DSSException(e);
        } finally {
            tmpDir.delete();
        }
    }

    @Override
    public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
        return null;
    }

    @Override
    public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
        return null;
    }

    @Override
    public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry dssPrivateKeyEntry) throws DSSException {
        return null;
    }

    @Override
    public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
        final List<DSSPrivateKeyEntry> list = new ArrayList<>();
        list.add(getKey());
        return list;
    }

    public DSSPrivateKeyEntry getKey() throws DSSException {
        String id = getId();
        logger.debug("OpenSC>>>Resolved ID before certificate read: '{}', module='{}', slot='{}'", id, module, slot);
        byte[] cert;
        try {
            cert = readCertificate(id);
            resolvedId = id;
        } catch (DSSException e) {
            logger.warn("OpenSC>>>getKey failed for ID {}: {}. Trying fallback certificate IDs.", id, e.getMessage());
            cert = findFallbackCertificate(id);
            if (cert == null) {
                throw e;
            }
        }
        logger.debug("OpenSC>>>Certificate retrieved, size: {} bytes", cert.length);
        CertificateToken certificateToken = DSSUtils.loadCertificate(cert);
        try {
            return new OpenSCPrivateKeyEntry(certificateToken.getCertificate().getEncoded());
        } catch (CertificateEncodingException e) {
            logger.error("OpenSC>>>Error encoding certificate", e);
            throw new DSSException(e);
        }
    }

    private byte[] readCertificate(String id) {
        String command = MessageFormat.format(EsupDSSClientLauncher.getProperties().getProperty("opensc_command_get_key"), id);
        logger.debug("OpenSC>>>Executing getKey command: {}{}{}", command, module, slot);
        return launchProcess(command + module + slot);
    }

    private byte[] findFallbackCertificate(String failedId) {
        List<String> certificateIds = new ArrayList<>();
        addIdCandidate(certificateIds, failedId);
        addZeroPaddedHexIds(certificateIds, failedId);
        for (String certificateId : getCertificateIds()) {
            addIdCandidate(certificateIds, certificateId);
            addZeroPaddedHexIds(certificateIds, certificateId);
        }

        logger.debug("OpenSC>>>Fallback candidate certificate IDs after normalization: {}", certificateIds);
        for (String certificateId : certificateIds) {
            if (StringUtils.equals(certificateId, failedId)) {
                continue;
            }
            try {
                logger.info("OpenSC>>>Trying fallback with certificate ID: {}", certificateId);
                byte[] cert = readCertificate(certificateId);
                resolvedId = certificateId;
                return cert;
            } catch (DSSException fallbackException) {
                logger.debug("OpenSC>>>Fallback failed for certificate ID {}: {}", certificateId, fallbackException.getMessage());
            }
        }
        return null;
    }

    public String getId() {
        if (StringUtils.isNotBlank(resolvedId)) {
            logger.debug("OpenSC>>>Using resolved certificate ID: {}", resolvedId);
            return resolvedId;
        }

        String id = EsupDSSClientLauncher.getProperties().getProperty("opensc_command_cert_id");
        if(StringUtils.isBlank(id)) {
            id = findFirstAvailableId();
            if (StringUtils.isBlank(id)) {
                logger.error("OpenSC>>>No ID found in OpenSC output");
                throw new OpenSCNoKeyException("No card or certificate key found");
            }
        } else {
            id = normalizeId(id);
            if (StringUtils.isBlank(id)) {
                logger.error("OpenSC>>>Configured certificate ID is blank after normalization");
                throw new OpenSCNoKeyException("No card or certificate key found");
            }
            logger.debug("OpenSC>>>Using configured certificate ID: {}", id);
        }
        logger.debug("OpenSC>>>getId returning '{}'", id);
        return id;
    }

    private String findFirstAvailableId() {
        List<String> commands = new ArrayList<>();
        String configuredGetIdCommand = EsupDSSClientLauncher.getProperties().getProperty("opensc_command_get_id");
        if (StringUtils.isNotBlank(configuredGetIdCommand)) {
            addCommand(commands, configuredGetIdCommand + module + slot);
        }
        addCommand(commands, "pkcs11-tool -O --type pubkey" + module + slot);
        addCommand(commands, "pkcs11-tool -O --type cert" + module + slot);
        addCommand(commands, "pkcs11-tool -O" + module + slot);
        if (StringUtils.isNotBlank(slot)) {
            if (StringUtils.isNotBlank(configuredGetIdCommand)) {
                addCommand(commands, configuredGetIdCommand + module);
            }
            addCommand(commands, "pkcs11-tool -O --type pubkey" + module);
            addCommand(commands, "pkcs11-tool -O --type cert" + module);
            addCommand(commands, "pkcs11-tool -O" + module);
        }

        for (String command : commands) {
            String id = extractId(command);
            if (StringUtils.isNotBlank(id)) {
                return id;
            }
        }
        return null;
    }

    private String extractId(String command) {
        logger.debug("OpenSC>>>Executing command for ID extraction: {}", command);
        try {
            byte[] outputBytes = launchProcess(command, false);
            String output = new String(outputBytes);
            logger.debug("OpenSC>>>ID extraction output:\n{}", output);
            List<String> ids = extractIds(output);
            if (!ids.isEmpty()) {
                logger.debug("OpenSC>>>Normalized IDs from command '{}': {}", command, ids);
                return ids.get(0);
            }
            logger.debug("OpenSC>>>No ID found in output for command '{}'", command);
        } catch (Exception e) {
            logger.debug("OpenSC>>>Error during ID extraction with command '{}': {}", command, e.getMessage());
        }
        return null;
    }

    private List<String> getCertificateIds() {
        List<String> ids = new ArrayList<>();
        addIdsFromCommand(ids, "pkcs11-tool -O --type cert" + module + slot);
        if (ids.isEmpty()) {
            addIdsFromCommand(ids, "pkcs11-tool -O" + module + slot);
        }
        if (ids.isEmpty() && StringUtils.isNotBlank(slot)) {
            logger.debug("OpenSC>>>No certificate ID found with slot {}, retrying without slot", slot);
            addIdsFromCommand(ids, "pkcs11-tool -O --type cert" + module);
            if (ids.isEmpty()) {
                addIdsFromCommand(ids, "pkcs11-tool -O" + module);
            }
        }
        logger.debug("OpenSC>>>Certificate IDs discovered from OpenSC listings: {}", ids);
        return ids;
    }

    private void addIdsFromCommand(List<String> ids, String command) {
        try {
            logger.debug("OpenSC>>>Listing IDs with command: {}", command);
            byte[] output = launchProcess(command, false);
            String outputString = new String(output);
            logger.debug("OpenSC>>>Listing output for '{}':\n{}", command, outputString);
            for (String id : extractIds(outputString)) {
                addIdCandidate(ids, id);
            }
        } catch (DSSException e) {
            logger.debug("OpenSC>>>Unable to list certificate IDs with command '{}': {}", command, e.getMessage());
        }
    }

    private static void addCommand(List<String> commands, String command) {
        if (StringUtils.isNotBlank(command) && !commands.contains(command)) {
            commands.add(command);
        }
    }

    private static void addIdCandidate(List<String> ids, String id) {
        String normalizedId = normalizeId(id);
        if (StringUtils.isNotBlank(normalizedId) && !ids.contains(normalizedId)) {
            ids.add(normalizedId);
        }
    }

    private static void addZeroPaddedHexIds(List<String> ids, String id) {
        String normalizedId = normalizeId(id);
        if (StringUtils.isBlank(normalizedId) || !normalizedId.matches("[0-9a-fA-F]+")) {
            return;
        }

        String evenLengthId = normalizedId.length() % 2 == 0 ? normalizedId : "0" + normalizedId;
        addIdCandidate(ids, evenLengthId);
        if (evenLengthId.length() <= 2) {
            addIdCandidate(ids, StringUtils.leftPad(evenLengthId, 4, '0'));
        }
    }

    static List<String> extractIds(String output) {
        List<String> ids = new ArrayList<>();
        if (StringUtils.isBlank(output)) {
            return ids;
        }

        String[] lines = output.split("\n");
        for (String line : lines) {
            int idIndex = line.indexOf("ID:");
            if (idIndex == -1) {
                continue;
            }
            logger.debug("OpenSC>>>Found ID line: {}", line);
            addIdCandidate(ids, line.substring(idIndex + 3));
        }
        return ids;
    }

    static String normalizeId(String rawId) {
        if (StringUtils.isBlank(rawId)) {
            return null;
        }

        String id = rawId.trim();
        int idIndex = id.indexOf("ID:");
        if (idIndex != -1) {
            id = id.substring(idIndex + 3).trim();
        }
        String hexId = extractHexId(id);
        if (StringUtils.isNotBlank(hexId)) {
            return hexId;
        }

        int commentIndex = id.indexOf("(");
        if (commentIndex != -1) {
            id = id.substring(0, commentIndex).trim();
        }
        if (id.contains(" ")) {
            id = id.split("\\s+")[0].trim();
        }
        id = StringUtils.strip(id, "'\"");
        if (id.startsWith("0x") || id.startsWith("0X")) {
            id = id.substring(2);
        }
        return id.replace(":", "").toLowerCase();
    }

    private static String extractHexId(String id) {
        Matcher hexMatcher = OPENSC_HEX_ID_PATTERN.matcher(id);
        if (hexMatcher.find()) {
            return sanitizeHexId(hexMatcher.group(1));
        }

        Matcher directHexMatcher = OPENSC_DIRECT_HEX_ID_PATTERN.matcher(id);
        if (directHexMatcher.matches()) {
            return sanitizeHexId(directHexMatcher.group(1));
        }
        return null;
    }

    private static String sanitizeHexId(String hexId) {
        String sanitizedHexId = hexId.replace(":", "").replaceAll("\\s+", "").trim();
        return sanitizedHexId.matches("[0-9a-fA-F]+") ? sanitizedHexId.toLowerCase() : null;
    }

    public byte[] launchProcess(String command) throws DSSException {
        return launchProcess(command, true);
    }

    public byte[] launchProcess(String command, boolean logError) throws DSSException {
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if(SystemUtils.IS_OS_WINDOWS) {
                String fullCommand = EsupDSSClientLauncher.getProperties().getProperty("opensc_path_windows") + "\\" + command;
                logger.debug("OpenSC>>>Full command: {}", maskSensitiveCommand(fullCommand));
                processBuilder.command("cmd", "/C", fullCommand);
                Map<String, String> envs = processBuilder.environment();
                envs.put("Path", EsupDSSClientLauncher.getProperties().getProperty("opensc_path_windows") + ";" + System.getenv("Path"));
            } else {
                String openscPathLinux = EsupDSSClientLauncher.getProperties().getProperty("opensc_path_linux");
                String fullCommand = (StringUtils.isNotBlank(openscPathLinux) ? openscPathLinux + "/" : "") + command;
                logger.debug("OpenSC>>>Full command: {}", maskSensitiveCommand(fullCommand));
                processBuilder.command("bash", "-c", fullCommand);
            }

            processBuilder.redirectErrorStream(false);
            process = processBuilder.start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            IOUtils.copy(process.getInputStream(), stdout);

            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            IOUtils.copy(process.getErrorStream(), stderr);

            int exitVal = process.waitFor();

            byte[] result = stdout.toByteArray();
            String err = stderr.toString();

            if (!err.isBlank() && logError) {
                logger.warn("OpenSC stderr:\n{}", err);
            }

            if (exitVal == 0) {
                return result;
            } else {
                if (logError) {
                    logger.error("OpenSc command fail with exit code: {}", exitVal);
                    if (isBinaryOutputCommand(command)) {
                        logger.error("stdout size: {} bytes", result.length);
                    } else {
                        logger.error("stdout: {}", new String(result));
                    }
                    logger.error("stderr: {}", err);
                }
                throw new DSSException("OpenSC command failed: " + err);
            }
        } catch (InterruptedException | IOException e) {
            throw new DSSException(e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    static String maskSensitiveCommand(String command) {
        if (StringUtils.isBlank(command)) {
            return command;
        }
        String maskedCommand = OPENSC_PIN_ARGUMENT_PATTERN.matcher(command).replaceAll("$1******");
        return OPENSC_PIN_EQUALS_ARGUMENT_PATTERN.matcher(maskedCommand).replaceAll("$1******");
    }

    private static boolean isBinaryOutputCommand(String command) {
        return StringUtils.contains(command, " -r ") && StringUtils.contains(command, "--type cert");
    }

}
