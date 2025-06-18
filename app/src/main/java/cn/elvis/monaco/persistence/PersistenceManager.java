package cn.elvis.monaco.persistence;

import cn.elvis.monaco.configuration.PersistenceConfiguration;

import java.util.Optional;

public class PersistenceManager {

//    private final Repository repository;
//
//    public PersistenceManager(PersistenceConfiguration configuration) {
//        this.repository = createRepository(configuration);
//    }
//
//    private Repository createRepository(PersistenceConfiguration configuration) {
//        var type = Optional.ofNullable(configuration).map(PersistenceConfiguration::getType).orElse("");
//        return  switch (type) {
//            case "memory" -> new MemoryPersistence();
//            case "file" -> {
//                var fileConfig = configuration.getFile();
//                yield new RocksdbPersistence(fileConfig.getPath());
//            }
//            default -> throw new IllegalArgumentException("Unknown persistence type: " + type);
//        };
//    }
}
