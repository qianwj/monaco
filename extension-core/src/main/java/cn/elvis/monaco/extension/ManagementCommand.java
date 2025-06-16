package cn.elvis.monaco.extension;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public final class ManagementCommand {

    private Type type;

    private String extensionType;

    public Type getType() {
        return type;
    }

    public ManagementCommand setType(Type type) {
        this.type = type;
        return this;
    }

    public String getExtensionType() {
        return extensionType;
    }

    public ManagementCommand setExtensionType(String extensionType) {
        this.extensionType = extensionType;
        return this;
    }

    public enum Type {
        STOP,
        RESUME,
        UNDEPLOY,
    }

    public static class Codec implements MessageCodec<ManagementCommand, ManagementCommand> {

        @Override
        public void encodeToWire(Buffer buffer, ManagementCommand managementCommand) {
            String type = managementCommand.getType().toString();
            buffer.appendString(type);
            buffer.appendString("|");
            buffer.appendString(managementCommand.getExtensionType());
        }

        @Override
        public ManagementCommand decodeFromWire(int pos, Buffer buffer) {
            String[] data = buffer.toString().split("\\|");
            Type type = Type.valueOf(data[0]);
            String extensionType = data[1];
            return new ManagementCommand().setType(type).setExtensionType(extensionType);
        }

        @Override
        public ManagementCommand transform(ManagementCommand managementCommand) {
            return managementCommand;
        }

        @Override
        public String name() {
            return "ExtensionManagementCommandCodec";
        }

        @Override
        public byte systemCodecID() {
            return 0;
        }
    }
}
