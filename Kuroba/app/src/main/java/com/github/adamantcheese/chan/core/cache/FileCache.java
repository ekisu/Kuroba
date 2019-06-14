/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.cache;

import androidx.annotation.MainThread;

import com.android.volley.Cache;
import com.github.adamantcheese.chan.core.cache.streams.CacheBackedRandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.HttpRandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.LazyRandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStreamReplicator;
import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStreamViewCreator;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;

public class FileCache implements FileCacheDownloader.Callback {
    private static final String TAG = "FileCache";
    private static final int TIMEOUT = 10000;
    private static final int DOWNLOAD_POOL_SIZE = 2;

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_POOL_SIZE);
    protected OkHttpClient httpClient;

    private final CacheHandler cacheHandler;
    private final PartialCacheHandler partialCacheHandler;

    private HashMap<String, RandomAccessStreamViewCreator<CacheBackedRandomAccessStream>> openCacheBackedStreams = new HashMap<>();
    private HashMap<String, FileCacheDownloader> downloaders = new HashMap<>();

    public FileCache(File directory) {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                // Disable SPDY, causes reproducible timeouts, only one download at the same time and other fun stuff
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();

        cacheHandler = new CacheHandler(directory);
        partialCacheHandler = new PartialCacheHandler(directory);
    }

    public void clearCache() {
        for (FileCacheDownloader downloader : downloaders.values()) {
            downloader.cancel();
        }

        cacheHandler.clearCache();
    }

    /**
     * Start downloading the file located at the url.<br>
     * If the file is in the cache then the callback is executed immediately and null is
     * returned.<br>
     * Otherwise if the file is downloading or has not yet started downloading a
     * {@link FileCacheDownloader} is returned.<br>
     *
     * @param url      the url to download.
     * @param listener listener to execute callbacks on.
     * @return {@code null} if in the cache, {@link FileCacheDownloader} otherwise.
     */
    @MainThread
    public FileCacheDownloader downloadFile(String url, FileCacheListener listener) {
        if (downloaders.containsKey(url)) {
            FileCacheDownloader downloader = downloaders.get(url);
            downloader.addListener(listener);
            return downloader;
        }

        try {
            RandomAccessStreamViewCreator.RandomAccessStreamView view = getCacheBackedStream(url);
            FileCacheDownloader downloader = FileCacheDownloader.fromCallbackStream(this, view);
            downloader.addListener(listener);
            downloader.execute(downloadPool);
            downloaders.put(url, downloader);

            return downloader;
        } catch (IOException e) {
            Logger.e(TAG, "downloadFile: ", e);
            return null;
        }
    }

    public FileCacheDataSource createDataSource(String url) throws IOException {
        FileCacheDataSource dataSource = new FileCacheDataSource(getCacheBackedStream(url));

        if (downloaders.containsKey(url)) {
            downloaders.get(url).cancel();
        }

        return dataSource;
    }

    public RandomAccessStreamViewCreator<CacheBackedRandomAccessStream>.RandomAccessStreamView getCacheBackedStream(String url) throws IOException {
        if (!openCacheBackedStreams.containsKey(url)
            || openCacheBackedStreams.get(url).isClosed()) {
            final LazyRandomAccessStream lazyReplicatedHttpStream = new LazyRandomAccessStream(
                () -> new RandomAccessStreamReplicator(
                    startingPosition -> {
                        HttpRandomAccessStream stream = new HttpRandomAccessStream(httpClient, url);
                        stream.open(startingPosition);
                        return stream;
                    }
            ));

            final CacheBackedRandomAccessStream cacheBackedStream = partialCacheHandler.getCacheBackedRandomAccessStream(url, lazyReplicatedHttpStream);

            final RandomAccessStreamViewCreator viewCreator = new RandomAccessStreamViewCreator(
                cacheBackedStream
            );

            openCacheBackedStreams.put(url, viewCreator);
        }

        return openCacheBackedStreams.get(url).createView();
    }

    @Override
    public void downloaderFinished(FileCacheDownloader fileCacheDownloader) {
        downloaders.values().remove(fileCacheDownloader);
    }

    public boolean exists(String key) {
        return cacheHandler.exists(key);
    }

    public File get(String key) {
        return cacheHandler.get(key);
    }

    public long getFileCacheSize() {
        return cacheHandler.getSize().get();
    }
}
