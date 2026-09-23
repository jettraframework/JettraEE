package io.jettra.ee.serialization;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable Java 25 record holding decoded record attributes and byte payload.
 */
public record JettraSerializedRecord(
    String recordId,
    int version,
    long timestamp,
    byte[] payload
) implements Serializable {

    public JettraSerializedRecord {
        Objects.requireNonNull(recordId, "recordId must not be null");
        payload = (payload != null) ? payload : new byte[0];
    }

    public static JettraSerializedRecord of(String recordId, int version, long timestamp, byte[] payload) {
        return new JettraSerializedRecord(recordId, version, timestamp, payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JettraSerializedRecord that)) return false;
        return version == that.version &&
               timestamp == that.timestamp &&
               Objects.equals(recordId, that.recordId) &&
               Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(recordId, version, timestamp);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
