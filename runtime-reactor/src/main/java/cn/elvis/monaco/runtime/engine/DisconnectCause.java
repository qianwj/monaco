package cn.elvis.monaco.runtime.engine;

public enum DisconnectCause {
    CLIENT_DISCONNECT,
    PROTOCOL_ERROR,
    KEEP_ALIVE_TIMEOUT,
    SESSION_TAKEN_OVER,
    SERVER_SHUTDOWN,
    NETWORK_ERROR
}
