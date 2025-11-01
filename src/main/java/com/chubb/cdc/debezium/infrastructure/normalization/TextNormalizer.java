package com.chubb.cdc.debezium.infrastructure.normalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;

/**
 * Normalizer for text values.
 *
 * <p>Ensures text values are valid UTF-8 encoded strings suitable for JSON serialization.
 * Handles various input types including byte arrays and CLOBs.</p>
 *
 * <p>Processing rules:</p>
 * <ul>
 *   <li>String: Validate UTF-8, replace invalid sequences</li>
 *   <li>byte[]: Decode as UTF-8</li>
 *   <li>Clob: Read and convert to String</li>
 *   <li>Special characters and emojis: Preserve (full UTF-8 support)</li>
 * </ul>
 *
 * <p>Requirement: FR-022 from spec.md</p>
 *
 * <p>Design Pattern: Strategy</p>
 */
@Component
public class TextNormalizer implements Normalizer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(TextNormalizer.class);

    // Replacement character for invalid UTF-8 sequences
    private static final char REPLACEMENT_CHAR = '\uFFFD';

    @Override
    public Object normalize(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        try {
            String normalized = convertToString(value);
            logger.trace("Normalized text field {}: length={}", fieldName,
                normalized != null ? normalized.length() : 0);
            return normalized;

        } catch (Exception e) {
            logger.warn("Failed to normalize text field {}: {}", fieldName, value, e);
            // Fall back to toString
            return value.toString();
        }
    }

    @Override
    public boolean canNormalize(Object value) {
        return value instanceof String ||
               value instanceof byte[] ||
               value instanceof char[] ||
               value instanceof CharSequence ||
               value instanceof Clob;
    }

    /**
     * Converts various text types to valid UTF-8 String.
     */
    private String convertToString(Object value) throws Exception {
        if (value instanceof String) {
            return ensureValidUtf8((String) value);
        }

        if (value instanceof byte[]) {
            return decodeBytes((byte[]) value);
        }

        if (value instanceof char[]) {
            return new String((char[]) value);
        }

        if (value instanceof CharSequence) {
            return ensureValidUtf8(value.toString());
        }

        if (value instanceof Clob) {
            return readClob((Clob) value);
        }

        return value.toString();
    }

    /**
     * Ensures a string contains valid UTF-8 sequences.
     *
     * Invalid characters are replaced with the Unicode replacement character (U+FFFD).
     */
    private String ensureValidUtf8(String str) {
        if (str == null) {
            return null;
        }

        // Check if string contains any invalid UTF-8
        boolean hasInvalid = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isSurrogate(c)) {
                // Check if it's a valid surrogate pair
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= str.length() || !Character.isLowSurrogate(str.charAt(i + 1))) {
                        hasInvalid = true;
                        break;
                    }
                } else if (Character.isLowSurrogate(c)) {
                    // Low surrogate without high surrogate
                    hasInvalid = true;
                    break;
                }
            }
        }

        if (!hasInvalid) {
            return str;
        }

        // Rebuild string with valid UTF-8
        logger.debug("Fixing invalid UTF-8 sequences in string");
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < str.length() && Character.isLowSurrogate(str.charAt(i + 1))) {
                    // Valid surrogate pair
                    sb.append(c);
                    sb.append(str.charAt(i + 1));
                    i++; // Skip next char
                } else {
                    // Invalid surrogate
                    sb.append(REPLACEMENT_CHAR);
                }
            } else if (Character.isLowSurrogate(c)) {
                // Orphaned low surrogate
                sb.append(REPLACEMENT_CHAR);
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Decodes byte array as UTF-8.
     *
     * Invalid sequences are replaced with the replacement character.
     */
    private String decodeBytes(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(String.valueOf(REPLACEMENT_CHAR));

        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            logger.warn("Failed to decode bytes as UTF-8, using default charset", e);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Reads content from a CLOB.
     */
    private String readClob(Clob clob) throws Exception {
        if (clob == null) {
            return null;
        }

        long length = clob.length();
        if (length == 0) {
            return "";
        }

        // Read the entire CLOB
        // Note: For very large CLOBs, this might need streaming approach
        if (length > Integer.MAX_VALUE) {
            logger.warn("CLOB size exceeds Integer.MAX_VALUE, truncating");
            length = Integer.MAX_VALUE;
        }

        String content = clob.getSubString(1, (int) length);
        return ensureValidUtf8(content);
    }
}
