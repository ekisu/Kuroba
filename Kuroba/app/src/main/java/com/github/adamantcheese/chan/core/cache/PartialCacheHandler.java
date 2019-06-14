package com.github.adamantcheese.chan.core.cache;

import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.github.adamantcheese.chan.core.cache.streams.CacheBackedRandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStream;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PartialCacheHandler {
    private static final String TAG = "PartialCacheHandler";
    // TODO enforce cache size, trimming, etc.
    private static final int TRIM_TRIES = 20;
    private static final long FILE_CACHE_DISK_SIZE = 100 * 1024 * 1024;

    private final ExecutorService pool = Executors.newFixedThreadPool(1);

    /**
     * An estimation of the current size of the directory. Used to check if trim must be run
     * because the folder exceeds the maximum size.
     */
    private AtomicLong size = new AtomicLong();
    private AtomicBoolean trimRunning = new AtomicBoolean(false);

    private final File directory;

    public PartialCacheHandler(File directory) {
        this.directory = directory;

        createDirectories();
        backgroundRecalculateSize();
    }

    @MainThread
    public boolean exists(String key) {
        return CacheBackedRandomAccessStream.isKeyComplete(directory, key);
    }


    public CacheBackedRandomAccessStream getCacheBackedRandomAccessStream(
            String key, RandomAccessStream inputStream
    ) {
        createDirectories();

        return new CacheBackedRandomAccessStream(directory, hash(key), inputStream, this::streamWasClosed);
    }

    @MainThread
    public AtomicLong getSize() {
        return size;
    }

    @MainThread
    protected void streamWasClosed(long sizeDifference) {
        long adjustedSize = size.addAndGet(sizeDifference);

        if (adjustedSize > FILE_CACHE_DISK_SIZE && trimRunning.compareAndSet(false, true)) {
            pool.submit(() -> {
                try {
                    trim();
                } catch (Exception e) {
                    Logger.e(TAG, "Error trimming", e);
                } finally {
                    trimRunning.set(false);
                }
            });
        }
    }

    @MainThread
    public void clearCache() {
        Logger.d(TAG, "Clearing cache");

        if (directory.exists() && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (!file.delete()) {
                    Logger.d(TAG, "Could not delete cache file while clearing cache " +
                            file.getName());
                }
            }
        }

        recalculateSize();
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

    @MainThread
    private void backgroundRecalculateSize() {
        pool.submit(this::recalculateSize);
    }

    @AnyThread
    private void recalculateSize() {
        long calculatedSize = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                calculatedSize += file.length();
            }
        }

        size.set(calculatedSize);
    }

    @WorkerThread
    private void trim() {
        File[] directoryFiles = directory.listFiles();

        // Don't try to trim empty directories or just one file in it.
        if (directoryFiles == null || directoryFiles.length <= 1) {
            return;
        }

        // Get all files with their last modified times.
        List<Pair<File, Long>> files = new ArrayList<>(directoryFiles.length);
        for (File file : directoryFiles) {
            // Skip cache metadata files here.
            if (file.getName().endsWith(".metadata")) continue;

            files.add(new Pair<>(file, file.lastModified()));
        }

        // Sort by oldest first.
        Collections.sort(files, (o1, o2) -> Long.signum(o1.second - o2.second));

        // Trim as long as the directory size exceeds the threshold and we haven't reached
        // the trim limit.
        long workingSize = size.get();
        for (int i = 0; workingSize >= FILE_CACHE_DISK_SIZE && i < Math.min(files.size(), TRIM_TRIES); i++) {
            File file = files.get(i).first;
            File metadataFile = new File(file.getAbsolutePath() + ".metadata");

            Logger.d(TAG, "Delete for trim " + file.getAbsolutePath());
            workingSize -= file.length();

            boolean deleteResult = file.delete();
            boolean deleteMetadataResult = metadataFile.delete();

            if (!deleteResult) {
                Logger.e(TAG, "Failed to delete cache file for trim");
            }

            if (!deleteMetadataResult) {
                Logger.e(TAG, "Failed to delete metadata file for trim");
            }
        }

        recalculateSize();
    }
}
