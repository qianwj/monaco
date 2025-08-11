package cn.elvis.monaco.gateway.entity.events;


public record ClientSessionClose(String clientId, boolean normalClosed) {}
