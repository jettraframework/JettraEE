package io.jettra.ee.serialization;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance, native serialization engine for the Jettra ecosystem.
 * <p>
 * Designed for Java 25+ low-latency execution with compact memory overhead.
 * Provides specialized binary record encoding with 21-byte compact headers,
 * zero-copy payload extraction, and optimized object graph persistence.
 */
public final class JettraSerialization {

    private static final byte[] MAGIC_JDAT = CompactBinaryHeader.MAGIC_JDAT;
    private static final byte[] MAGIC_JSER = CompactBinaryHeader.MAGIC_JSER;
    private static final int HEADER_SIZE = CompactBinaryHeader.HEADER_SIZE;

    private JettraSerialization() {}

    /**
     * Serializes record attributes into a compact binary byte array.
     *
     * @param recordId  the record identifier
     * @param version   the schema / revision version
     * @param timestamp the creation / mutation epoch millis
     * @param payload   the binary data payload
     * @return the serialized compact binary representation
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
     * Deserializes a binary array into a {@link JettraSerializedRecord}.
     * Supports both modern compact binary headers and transparent fallback to raw payloads.
     *
     * @param recordId the record identifier
     * @param rawBytes the serialized bytes read from disk or network
     * @return decoded {@link JettraSerializedRecord}
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
                byte[] payload = new byte[readLen];
                if (readLen > 0) {
                    buffer.get(payload);
                }
                return new JettraSerializedRecord(safeId, header.recordVersion(), header.timestamp(), payload);
            }
        }

        // Fallback: legacy or raw un-headered payload
        return new JettraSerializedRecord(safeId, 1, System.currentTimeMillis(), rawBytes);
    }

    /**
     * Extracts only the payload bytes directly from serialized data without allocating
     * the full record wrapper.
     *
     * @param rawBytes the serialized bytes
     * @return payload byte array, or raw bytes if un-headered
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
                buffer.position(4 + 1 + 4 + 8); // Skip magic, formatVersion, recordVersion, timestamp
                int payloadLen = buffer.getInt();
                int available = buffer.remaining();
                int readLen = Math.min(payloadLen, available);
                byte[] payload = new byte[readLen];
                if (readLen > 0) {
                    buffer.get(payload);
                }
                return payload;
            }
        }

        return rawBytes;
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
