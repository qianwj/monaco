package cn.elvis.monaco.common.entity;

import java.time.LocalDateTime;

public class Session {

    private String id;

    private LocalDateTime expiredTime;

    private boolean hasWill;

    private LocalDateTime willTriggerTime;
}
