package cn.elvis.monaco.configuration;

public class MetricsConfiguration {

    private String endpoint;

    private int port;

    private boolean enabled;

    public String getEndpoint() {
        return endpoint;
    }

    public MetricsConfiguration setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public int getPort() {
        return port;
    }

    public MetricsConfiguration setPort(int port) {
        this.port = port;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public MetricsConfiguration setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}
