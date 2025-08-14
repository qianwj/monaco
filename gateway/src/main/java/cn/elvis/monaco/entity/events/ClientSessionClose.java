package cn.elvis.monaco.entity.events;


public record ClientSessionClose(String clientId, boolean normalClosed) {}
