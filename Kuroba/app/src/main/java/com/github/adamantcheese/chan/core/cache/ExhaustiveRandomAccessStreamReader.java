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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStreamViewCreator;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExhaustiveRandomAccessStreamReader implements Runnable {
    private static final String TAG = "ExhaustiveRandomAccessStreamReader";
    private static final long BUFFER_SIZE = 8192;
    private static final long NOTIFY_SIZE = BUFFER_SIZE * 8;

    private final Handler handler;

    // Main thread only.
    private final Callback callback;
    private final List<FileCacheListener> listeners = new ArrayList<>();

    // Main and worker thread.
    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicBoolean cancel = new AtomicBoolean(false);
    private Future<?> future;
    private final RandomAccessStreamViewCreator.RandomAccessStreamView stream;

    static ExhaustiveRandomAccessStreamReader fromCallbackStream(
            Callback callback, RandomAccessStreamViewCreator.RandomAccessStreamView stream) {
        return new ExhaustiveRandomAccessStreamReader(callback, stream);
    }

    private ExhaustiveRandomAccessStreamReader(Callback callback, RandomAccessStreamViewCreator.RandomAccessStreamView stream) {
        this.callback = callback;
        this.stream = stream;

        handler = new Handler(Looper.getMainLooper());
    }

    @MainThread
    public void execute(ExecutorService executor) {
        future = executor.submit(this);
    }

    @AnyThread
    public Future<?> getFuture() {
        return future;
    }

    @MainThread
    public void addListener(FileCacheListener callback) {
        listeners.add(callback);
    }

    /**
     * Cancel this download.
     */
    @MainThread
    public void cancel() {
        if (cancel.compareAndSet(false, true)) {
            // Did not start running yet, mark finished here.
            if (!running.get()) {
                callback.downloaderFinished(this);
            }
        }
    }

    @AnyThread
    private void post(Runnable runnable) {
        handler.post(runnable);
    }

    @AnyThread
    private void log(String message) {
        Logger.d(TAG, message);
    }

    @AnyThread
    private void log(String message, Exception e) {
        Logger.e(TAG, message, e);
    }

    @Override
    @WorkerThread
    public void run() {
        log("start");
        running.set(true);
        execute();
    }

    @WorkerThread
    private void execute() {
        try {
            checkCancel();

            log("starting read");

            readExhaustive();

            log("done");

            post(() -> {
                callback.downloaderFinished(this);
                for (FileCacheListener callback : listeners) {
                    callback.onSuccess(stream);
                    callback.onEnd();
                }
            });
        } catch (IOException e) {
            boolean isNotFound = false;
            boolean cancelled = false;
            if (e instanceof HttpCodeIOException) {
                int code = ((HttpCodeIOException) e).code;
                log("exception: http error, code: " + code, e);
                isNotFound = code == 404;
            } else if (e instanceof CancelException) {
                // Don't log the stack.
                log("exception: cancelled");
                cancelled = true;
            } else {
                log("exception", e);
            }

            // TODO implement not found
            final boolean finalIsNotFound = isNotFound;
            final boolean finalCancelled = cancelled;
            post(() -> {
                for (FileCacheListener callback : listeners) {
                    if (finalCancelled) {
                        callback.onCancel();
                    } else {
                        callback.onFail(finalIsNotFound);
                    }

                    callback.onEnd();
                }
                callback.downloaderFinished(this);
            });
        } finally {
            // TODO close stream? (v2)
            try {
                stream.close();
            } catch (IOException e) {}
        }
    }

    @WorkerThread
    private void readExhaustive() throws IOException {
        long contentLength = stream.length();

        long read;
        long total = 0;
        long notifyTotal = 0;

        byte[] buffer = new byte[(int) BUFFER_SIZE];

        while ((read = stream.read(buffer, 0, BUFFER_SIZE)) != -1) {
            total += read;

            if (total >= notifyTotal + NOTIFY_SIZE) {
                notifyTotal = total;
                log("progress " + (total / (float) contentLength));
                postProgress(total, contentLength <= 0 ? total : contentLength);
            }

            checkCancel();
        }

        // TODO close stream?
    }

    @WorkerThread
    private void checkCancel() throws IOException {
        if (cancel.get()) {
            throw new CancelException();
        }
    }

    @WorkerThread
    private void postProgress(final long downloaded, final long total) {
        post(() -> {
            for (FileCacheListener callback : listeners) {
                callback.onProgress(downloaded, total);
            }
        });
    }

    private static class CancelException extends IOException {
        public CancelException() {
        }
    }

    private static class HttpCodeIOException extends IOException {
        private int code;

        public HttpCodeIOException(int code) {
            this.code = code;
        }
    }

    public interface Callback {
        void downloaderFinished(ExhaustiveRandomAccessStreamReader fileCacheDownloader);
    }
}
