package io.jettra.server.db.security;

import java.io.Serializable;

public class CompactHeader implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String version = "1.0";
    private long timestamp = System.currentTimeMillis();
    private String nodeInfo = "local";

    public CompactHeader() {}

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNodeInfo() {
        return nodeInfo;
    }

    public void setNodeInfo(String nodeInfo) {
        this.nodeInfo = nodeInfo;
    }
}
