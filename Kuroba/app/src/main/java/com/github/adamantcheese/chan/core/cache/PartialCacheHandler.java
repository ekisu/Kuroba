package com.github.adamantcheese.chan.core.cache;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;

import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

public class PartialCacheHandler {
    private static final String TAG = "PartialCacheHandler";
    private static final int TRIM_TRIES = 20;
    private static final long FILE_CACHE_DISK_SIZE = 100 * 1024 * 1024;

    // TODO will this be needed?
    private final HashMap<String, CacheBackedRandomAccessStream> createdCacheBackedStreams = new HashMap<>();
    private final File directory;

    public PartialCacheHandler(File directory) {
        this.directory = directory;

        createDirectories();
        // backgroundRecalculateSize();
    }

    @MainThread
    public boolean exists(String key) {
        return get(key).exists();
    }

    @MainThread
    public File get(String key) {
        createDirectories();

        return new File(directory, hash(key));
    }

    public CacheBackedRandomAccessStream getCacheBackedRandomAccessStream(
            String key, RandomAccessStream inputStream
    ) {
        createDirectories();

        try {
            return new CacheBackedRandomAccessStream(directory, hash(key), inputStream);
        } catch (FileNotFoundException e) {
            // TODO deal with this shit
            throw new Error("This shouldn't happen.");
        }
    }

    public void createDirectories() {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Logger.e(TAG, "Unable to create file cache dir " +
                        directory.getAbsolutePath());
            }
        }
    }

    @AnyThread
    private String hash(String key) {
        return String.valueOf(key.hashCode());
    }
}
