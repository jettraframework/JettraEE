package io.jettra.ee.serialization;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Compact binary header for ultra-fast record serialization in JettraEE and JettraDB.
 * <p>
 * Binary structure (21 bytes total):
 * <ul>
 *   <li>Magic bytes (4 bytes): {@code 0x4A, 0x44, 0x41, 0x54} ("JDAT") or {@code 0x4A, 0x53, 0x45, 0x52} ("JSER")</li>
 *   <li>Format version (1 byte): byte value</li>
 *   <li>Record version (4 bytes): int</li>
 *   <li>Timestamp (8 bytes): long (epoch milliseconds)</li>
 *   <li>Payload length (4 bytes): int</li>
 * </ul>
 */
public record CompactBinaryHeader(
    byte[] magic,
    byte formatVersion,
    int recordVersion,
    long timestamp,
    int payloadLength
) implements Serializable {

    public static final byte[] MAGIC_JDAT = new byte[]{0x4A, 0x44, 0x41, 0x54}; // "JDAT"
    public static final byte[] MAGIC_JSER = new byte[]{0x4A, 0x53, 0x45, 0x52}; // "JSER"
    public static final byte DEFAULT_FORMAT_VERSION = 1;
    public static final int HEADER_SIZE = 21;

    public CompactBinaryHeader {
        magic = (magic != null) ? magic : MAGIC_JDAT;
    }

    public static CompactBinaryHeader of(int recordVersion, long timestamp, int payloadLength) {
        return new CompactBinaryHeader(MAGIC_JDAT, DEFAULT_FORMAT_VERSION, recordVersion, timestamp, payloadLength);
    }

    public void writeTo(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        buffer.put(magic);
        buffer.put(formatVersion);
        buffer.putInt(recordVersion);
        buffer.putLong(timestamp);
        buffer.putInt(payloadLength);
    }

    public static CompactBinaryHeader readFrom(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        if (buffer.remaining() < HEADER_SIZE) {
            return null;
        }
        byte[] magic = new byte[4];
        buffer.get(magic);
        byte fmt = buffer.get();
        int version = buffer.getInt();
        long ts = buffer.getLong();
        int len = buffer.getInt();
        return new CompactBinaryHeader(magic, fmt, version, ts, len);
    }

    public boolean isRecognizedMagic() {
        return Arrays.equals(magic, MAGIC_JDAT) || Arrays.equals(magic, MAGIC_JSER);
    }
}
