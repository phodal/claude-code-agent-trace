package com.phodal.agenttrace.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * Utility for computing content hashes for position-independent tracking.
 * Supports multiple hash algorithms with versioned format for future upgrades.
 */
public final class ContentHasher {

    /**
     * Hash algorithm versions for forward compatibility.
     */
    public enum Algorithm {
        CRC32_V1("crc32", "v1"),
        SHA256_V1("sha256", "v1");

        private final String name;
        private final String version;

        Algorithm(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getPrefix() {
            return name + ":" + version + ":";
        }
    }

    /**
     * Default algorithm for new hashes.
     */
    public static final Algorithm DEFAULT_ALGORITHM = Algorithm.CRC32_V1;
    
    private ContentHasher() {
        // Utility class
    }

    /**
     * Compute a content hash using the default algorithm.
     * Returns a hash in versioned format "algorithm:version:hexvalue"
     * 
     * @param content The content to hash
     * @return Hash string in format "algorithm:version:hexvalue", or null if content is empty
     */
    public static String hash(String content) {
        return hash(content, DEFAULT_ALGORITHM);
    }

    /**
     * Compute a content hash using the specified algorithm.
     * 
     * @param content The content to hash
     * @param algorithm The hash algorithm to use
     * @return Hash string in format "algorithm:version:hexvalue", or null if content is empty
     */
    public static String hash(String content, Algorithm algorithm) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        return switch (algorithm) {
            case CRC32_V1 -> hashCrc32(content);
            case SHA256_V1 -> hashSha256(content);
        };
    }

    private static String hashCrc32(String content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content.getBytes(StandardCharsets.UTF_8));
        return Algorithm.CRC32_V1.getPrefix() + String.format("%08x", crc32.getValue());
    }

    private static String hashSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return Algorithm.SHA256_V1.getPrefix() + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java, but fallback to CRC32
            return hashCrc32(content);
        }
    }

    /**
     * Parse the algorithm from a hash string.
     * 
     * @param hashString The hash string to parse
     * @return The algorithm used, or null if unknown/invalid format
     */
    public static Algorithm parseAlgorithm(String hashString) {
        if (hashString == null || hashString.isEmpty()) {
            return null;
        }
        for (Algorithm algo : Algorithm.values()) {
            if (hashString.startsWith(algo.getPrefix())) {
                return algo;
            }
        }
        // Legacy format support: "crc32:hexvalue" (no version)
        if (hashString.startsWith("crc32:") && !hashString.startsWith("crc32:v")) {
            return Algorithm.CRC32_V1;
        }
        return null;
    }

    /**
     * Compute a hash for a range of lines using the default algorithm.
     * 
     * @param content Full file content
     * @param startLine Start line (1-indexed)
     * @param endLine End line (1-indexed, inclusive)
     * @return Hash of the specified line range, or null if invalid
     */
    public static String hashRange(String content, int startLine, int endLine) {
        return hashRange(content, startLine, endLine, DEFAULT_ALGORITHM);
    }

    /**
     * Compute a hash for a range of lines using the specified algorithm.
     * 
     * @param content Full file content
     * @param startLine Start line (1-indexed)
     * @param endLine End line (1-indexed, inclusive)
     * @param algorithm The hash algorithm to use
     * @return Hash of the specified line range, or null if invalid
     */
    public static String hashRange(String content, int startLine, int endLine, Algorithm algorithm) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        String[] lines = content.split("\n", -1);
        if (startLine < 1 || endLine > lines.length || startLine > endLine) {
            return null;
        }
        
        StringBuilder rangeContent = new StringBuilder();
        for (int i = startLine - 1; i < endLine; i++) {
            if (i > startLine - 1) {
                rangeContent.append("\n");
            }
            rangeContent.append(lines[i]);
        }
        
        return hash(rangeContent.toString(), algorithm);
    }

    /**
     * Verify if a hash matches the given content.
     * Automatically detects the algorithm from the hash string.
     * 
     * @param content The content to verify
     * @param hashString The expected hash string
     * @return true if the content matches the hash
     */
    public static boolean verify(String content, String hashString) {
        if (content == null || hashString == null) {
            return false;
        }
        Algorithm algo = parseAlgorithm(hashString);
        if (algo == null) {
            return false;
        }
        String computed = hash(content, algo);
        return hashString.equals(computed);
    }
}
