package cn.elvis.monaco.configuration;

import java.util.Map;

public final class AuthConfiguration {

    private String mode;

    private Map<String, String> adminUsers;

    private String rootUsername;

    private String rootPassword;

    public String getMode() {
        return mode;
    }

    public AuthConfiguration setMode(String mode) {
        this.mode = mode;
        return this;
    }

    public Map<String, String> getAdminUsers() {
        return adminUsers;
    }

    public AuthConfiguration setAdminUsers(Map<String, String> adminUsers) {
        this.adminUsers = adminUsers;
        return this;
    }

    public String getRootUsername() {
        return rootUsername;
    }

    public AuthConfiguration setRootUsername(String rootUsername) {
        this.rootUsername = rootUsername;
        return this;
    }

    public String getRootPassword() {
        return rootPassword;
    }

    public AuthConfiguration setRootPassword(String rootPassword) {
        this.rootPassword = rootPassword;
        return this;
    }
}
