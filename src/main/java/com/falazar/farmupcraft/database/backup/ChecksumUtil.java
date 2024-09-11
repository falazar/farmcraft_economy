package com.falazar.farmupcraft.database.backup;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class ChecksumUtil {

    public static String generateChecksum(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        //long fileSize = Files.size(filePath);

        try (InputStream is = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
        }

        byte[] hashBytes = digest.digest();
        String checksum = bytesToHex(hashBytes);
        return checksum;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static void saveChecksum(Path backupFile, String checksum) throws Exception {
        Path checksumFile = Paths.get(backupFile.toString() + ".sha256");
        Files.write(checksumFile, checksum.getBytes());
    }

    public static boolean verifyChecksum(Path backupFile, Path checksumFile) throws Exception {
        String storedChecksum = new String(Files.readAllBytes(checksumFile));
        String computedChecksum = ChecksumUtil.generateChecksum(backupFile);
        return storedChecksum.equals(computedChecksum);
    }
}