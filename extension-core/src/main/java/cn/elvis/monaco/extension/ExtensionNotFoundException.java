package cn.elvis.monaco.extension;

public final class ExtensionNotFoundException extends RuntimeException {

    ExtensionNotFoundException(String extensionName) {
        super("Extension not found: " + extensionName);
    }
}
