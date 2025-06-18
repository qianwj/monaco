package cn.elvis.monaco.configuration;

public class PersistenceConfiguration {

    private String type;

    private FilePersistenceConfiguration file;

    public String getType() {
        return type;
    }

    public PersistenceConfiguration setType(String type) {
        this.type = type;
        return this;
    }

    public FilePersistenceConfiguration getFile() {
        return file;
    }

    public PersistenceConfiguration setFile(FilePersistenceConfiguration file) {
        this.file = file;
        return this;
    }

    public static class FilePersistenceConfiguration {

        private String path;

        private int bucketCount;

        public String getPath() {
            return path;
        }

        public FilePersistenceConfiguration setPath(String path) {
            this.path = path;
            return this;
        }

        public int getBucketCount() {
            return bucketCount;
        }

        public FilePersistenceConfiguration setBucketCount(int bucketCount) {
            this.bucketCount = bucketCount;
            return this;
        }
    }
}
