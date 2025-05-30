package cn.elvis.monaco.configuration;

import cn.elvis.monaco.transport.TransportType;

import java.util.StringJoiner;

public class TransportConfiguration {

    private TransportType type;

    private int port;

    private int instances;

    private String serverKeyPath;

    private String serverCertPath;

    public TransportType getType() {
        return type;
    }

    public TransportConfiguration setType(TransportType type) {
        this.type = type;
        return this;
    }

    public int getPort() {
        return port;
    }

    public TransportConfiguration setPort(int port) {
        this.port = port;
        return this;
    }

    public int getInstances() {
        return instances;
    }

    public TransportConfiguration setInstances(int instances) {
        this.instances = instances;
        return this;
    }

    public String getServerKeyPath() {
        assert this.type != TransportType.TLS
                && this.type != TransportType.WSS
                || this.serverKeyPath != null
                && !this.serverKeyPath.isBlank();
        return serverKeyPath;
    }

    public TransportConfiguration setServerKeyPath(String serverKeyPath) {
        this.serverKeyPath = serverKeyPath;
        return this;
    }

    public String getServerCertPath() {
        return serverCertPath;
    }

    public TransportConfiguration setServerCertPath(String serverCertPath) {
        assert this.type != TransportType.TLS
                && this.type != TransportType.WSS
                || this.serverCertPath != null
                && !this.serverCertPath.isBlank();
        this.serverCertPath = serverCertPath;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TransportConfiguration.class.getSimpleName() + "[", "]")
                .add("type=" + type)
                .add("port=" + port)
                .add("instances=" + instances)
                .add("serverKeyPath='" + serverKeyPath + "'")
                .add("serverCertPath='" + serverCertPath + "'")
                .toString();
    }
}
