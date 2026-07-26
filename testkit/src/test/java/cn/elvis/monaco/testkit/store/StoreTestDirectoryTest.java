package cn.elvis.monaco.testkit.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTestDirectoryTest {

    @TempDir
    Path parentDirectory;

    @Test
    void createsOwnedDirectoryAndDeletesItRecursively() throws Exception {
        StoreTestDirectory directory = StoreTestDirectory.create(parentDirectory, "memory-");
        Path root = directory.path();
        Path dataFile = directory.resolve("nested/state.bin");
        Files.createDirectories(dataFile.getParent());
        Files.writeString(dataFile, "state");

        assertTrue(Files.exists(dataFile));
        assertFalse(directory.isClosed());

        directory.close();
        directory.close();

        assertFalse(Files.exists(root));
        assertTrue(directory.isClosed());
    }

    @Test
    void refusesPathsOutsideOwnedDirectory() throws Exception {
        try (StoreTestDirectory directory = StoreTestDirectory.create(parentDirectory, "rocks-")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> directory.resolve("../outside"));

            assertEquals("path must stay inside the store test directory", error.getMessage());
        }
    }
}
