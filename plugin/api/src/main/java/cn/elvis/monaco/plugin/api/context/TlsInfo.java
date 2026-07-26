package cn.elvis.monaco.plugin.api.context;

/** Non-secret TLS information associated with an MQTT connection. */
public record TlsInfo(
        String protocol,
        String cipherSuite,
        String peerPrincipal,
        String peerCertificateFingerprint
) {

    public TlsInfo {
        protocol = normalize(protocol);
        cipherSuite = normalize(cipherSuite);
        peerPrincipal = normalize(peerPrincipal);
        peerCertificateFingerprint = normalize(peerCertificateFingerprint);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
