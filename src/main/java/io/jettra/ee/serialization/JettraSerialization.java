package io.jettra.ee.serialization;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * High-performance, native serialization engine for the Jettra ecosystem.
 * <p>
 * Designed for Java 25+ low-latency execution with compact memory overhead.
 * Provides specialized binary record encoding with 21-byte compact headers,
 * zero-copy payload extraction, advanced record compression (Deflate/Adaptive),
 * and optimized object graph persistence.
 */
public final class JettraSerialization {

    private static final byte[] MAGIC_JDAT = CompactBinaryHeader.MAGIC_JDAT;
    private static final byte[] MAGIC_JSER = CompactBinaryHeader.MAGIC_JSER;
    private static final int HEADER_SIZE = CompactBinaryHeader.HEADER_SIZE;
    public static final int COMPRESSION_THRESHOLD = 48;

    private JettraSerialization() {}

    /**
     * Serializes record attributes into a compact binary byte array without compression.
     */
    public static byte[] serializeRecord(String recordId, int version, long timestamp, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadLen);
        
        CompactBinaryHeader header = CompactBinaryHeader.of(version, timestamp, payloadLen);
        header.writeTo(buffer);

        if (payloadLen > 0) {
            buffer.put(payload);
        }

        return buffer.array();
    }

    /**
     * Serializes record attributes into a high-density binary byte array with advanced compression.
     * Payloads below {@link #COMPRESSION_THRESHOLD} or payloads where compression does not yield savings
     * are stored uncompressed with format version 1.
     */
    public static byte[] serializeRecordCompressed(String recordId, int version, long timestamp, byte[] payload) {
        if (payload == null || payload.length < COMPRESSION_THRESHOLD) {
            return serializeRecord(recordId, version, timestamp, payload);
        }

        byte[] compressed = compressDeflate(payload);
        // Fallback to uncompressed if compression doesn't save at least 4 bytes (size of length prefix)
        if (compressed.length + 4 >= payload.length) {
            return serializeRecord(recordId, version, timestamp, payload);
        }

        int payloadSectionLen = 4 + compressed.length;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadSectionLen);
        CompactBinaryHeader header = CompactBinaryHeader.ofCompressed(version, timestamp, payloadSectionLen);
        header.writeTo(buffer);
        buffer.putInt(payload.length);
        buffer.put(compressed);

        return buffer.array();
    }

    /**
     * Deserializes a binary array into a {@link JettraSerializedRecord}.
     * Supports modern compact binary headers, compressed format version 2, and transparent fallback to raw payloads.
     */
    public static JettraSerializedRecord deserializeRecord(String recordId, byte[] rawBytes) {
        String safeId = (recordId != null) ? recordId : "";
        if (rawBytes == null || rawBytes.length == 0) {
            return new JettraSerializedRecord(safeId, 1, System.currentTimeMillis(), new byte[0]);
        }

        if (rawBytes.length >= HEADER_SIZE) {
            ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
            CompactBinaryHeader header = CompactBinaryHeader.readFrom(buffer);

            if (header != null && header.isRecognizedMagic()) {
                int readLen = Math.min(header.payloadLength(), buffer.remaining());
                byte[] rawPayload = new byte[readLen];
                if (readLen > 0) {
                    buffer.get(rawPayload);
                }

                if (header.formatVersion() == CompactBinaryHeader.FORMAT_VERSION_COMPRESSED && rawPayload.length >= 4) {
                    ByteBuffer pb = ByteBuffer.wrap(rawPayload);
                    int originalLen = pb.getInt();
                    byte[] compBytes = new byte[rawPayload.length - 4];
                    pb.get(compBytes);
                    byte[] decompressed = decompressDeflate(compBytes, originalLen);
                    return new JettraSerializedRecord(safeId, header.recordVersion(), header.timestamp(), decompressed);
                }

                return new JettraSerializedRecord(safeId, header.recordVersion(), header.timestamp(), rawPayload);
            }
        }

        // Fallback: legacy or raw un-headered payload
        return new JettraSerializedRecord(safeId, 1, System.currentTimeMillis(), rawBytes);
    }

    /**
     * Extracts only the payload bytes directly from serialized data without allocating
     * the full record wrapper. Transparently decompresses if payload was stored compressed.
     */
    public static byte[] extractPayload(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }

        if (rawBytes.length >= HEADER_SIZE) {
            boolean isJdat = rawBytes[0] == MAGIC_JDAT[0] && rawBytes[1] == MAGIC_JDAT[1] &&
                             rawBytes[2] == MAGIC_JDAT[2] && rawBytes[3] == MAGIC_JDAT[3];
            boolean isJser = rawBytes[0] == MAGIC_JSER[0] && rawBytes[1] == MAGIC_JSER[1] &&
                             rawBytes[2] == MAGIC_JSER[2] && rawBytes[3] == MAGIC_JSER[3];

            if (isJdat || isJser) {
                ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
                buffer.position(4);
                byte fmtVersion = buffer.get();
                buffer.position(4 + 1 + 4 + 8); // Skip to payload length
                int payloadLen = buffer.getInt();
                int available = buffer.remaining();
                int readLen = Math.min(payloadLen, available);
                byte[] rawPayload = new byte[readLen];
                if (readLen > 0) {
                    buffer.get(rawPayload);
                }

                if (fmtVersion == CompactBinaryHeader.FORMAT_VERSION_COMPRESSED && rawPayload.length >= 4) {
                    ByteBuffer pb = ByteBuffer.wrap(rawPayload);
                    int originalLen = pb.getInt();
                    byte[] compBytes = new byte[rawPayload.length - 4];
                    pb.get(compBytes);
                    return decompressDeflate(compBytes, originalLen);
                }

                return rawPayload;
            }
        }

        return rawBytes;
    }

    /**
     * Compresses bytes using Deflater BEST_COMPRESSION.
     */
    public static byte[] compressDeflate(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Decompresses Deflate-compressed bytes.
     */
    public static byte[] decompressDeflate(byte[] compressed, int originalLength) {
        if (compressed == null || compressed.length == 0) {
            return new byte[0];
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(originalLength > 0 ? originalLength : compressed.length * 2);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalStateException("Failed to decompress record payload", e);
        } finally {
            inflater.end();
        }
    }

    /**
     * Serializes a general Java {@link Serializable} object into compact binary bytes.
     * Prefixes with JSER magic header and stream bytes.
     *
     * @param entity the entity to serialize
     * @return binary array
     * @throws IOException on serialization failure
     */
    public static byte[] serializeObject(Serializable entity) throws IOException {
        Objects.requireNonNull(entity, "Entity to serialize cannot be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(entity);
            oos.flush();
        }
        byte[] objectBytes = baos.toByteArray();
        return serializeRecord(entity.getClass().getName(), 1, System.currentTimeMillis(), objectBytes);
    }

    /**
     * Deserializes a general object from binary bytes previously serialized with {@link #serializeObject(Serializable)}.
     *
     * @param rawBytes the binary data
     * @param clazz    the expected class
     * @param <T>      the type of the object
     * @return deserialized object
     * @throws IOException            on I/O failure
     * @throws ClassNotFoundException if the class cannot be found
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeObject(byte[] rawBytes, Class<T> clazz) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(clazz, "Target class cannot be null");
        byte[] payload = extractPayload(rawBytes);
        if (payload == null || payload.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (!clazz.isInstance(obj)) {
                throw new ClassCastException("Expected object of type " + clazz.getName() + " but found " + (obj != null ? obj.getClass().getName() : "null"));
            }
            return (T) obj;
        }
    }

    /**
     * Checks if the given byte array begins with Jettra compact binary magic bytes.
     *
     * @param rawBytes the byte array to check
     * @return true if header matches JDAT or JSER
     */
    public static boolean isJettraBinary(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length < 4) return false;
        return (rawBytes[0] == MAGIC_JDAT[0] && rawBytes[1] == MAGIC_JDAT[1] &&
                rawBytes[2] == MAGIC_JDAT[2] && rawBytes[3] == MAGIC_JDAT[3]) ||
               (rawBytes[0] == MAGIC_JSER[0] && rawBytes[1] == MAGIC_JSER[1] &&
                rawBytes[2] == MAGIC_JSER[2] && rawBytes[3] == MAGIC_JSER[3]);
    }
}
