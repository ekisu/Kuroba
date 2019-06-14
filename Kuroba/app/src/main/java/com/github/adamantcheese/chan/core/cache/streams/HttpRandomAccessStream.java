package com.github.adamantcheese.chan.core.cache.streams;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class HttpRandomAccessStream implements RandomAccessStream {
    private static final String TAG = "HttpRandomAccessStream";

    private final OkHttpClient httpClient;
    private long startPosition;
    private final String url;

    private long contentLength;
    private long position = 0;

    private Call call;
    private Response response;
    private ResponseBody body;
    private BufferedSource source;

    private AtomicBoolean closed = new AtomicBoolean(false);

    public HttpRandomAccessStream(OkHttpClient httpClient, String url) {
        this.httpClient = httpClient;
        this.url = url;
    }

    private void throwIfClosed() throws ClosedException {
        if (closed.get()) {
            throw new ClosedException("Stream was closed");
        }
    }

    private synchronized void getBody(long startPosition) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT);

        if (startPosition > 0) {
            builder.header("Range", "bytes=" + startPosition + "-");
        }

        Request request = builder.build();

        call = httpClient.newBuilder()
                .proxy(ChanSettings.getProxy())
                .build()
                .newCall(request);

        response = call.execute();
        if (!response.isSuccessful()) {
            // TODO this was HTTPCodeIOException before.
            throw new IOException("Invalid response: " + response.code());
        }

        throwIfClosed();

        body = response.body();
        if (body == null) {
            throw new IOException("body == null");
        }
    }

    @Override
    public synchronized void open(long startPosition) throws IOException {
        this.startPosition = startPosition;
        this.position = startPosition;

        getBody(startPosition);
        contentLength = body.contentLength();
        source = body.source();
    }

    @Override
    public synchronized long position() {
        return position;
    }

    @Override
    public synchronized long length() {
        return startPosition + contentLength;
    }

    @Override
    public synchronized int read(byte[] output, long offset, long length) throws IOException {
        int bytesRead = source.read(output, (int) offset, (int) length);
        if (bytesRead > 0) {
            this.position += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public synchronized void seek(long pos) {
        /* TODO this is not seekable. change the interface type used with RandomAccessStreamReplicator
         to something that reflects this. */
    }

    private synchronized void doClose() {
        try {
            if (source != null) {
                source.close();
            }
            if (body != null) {
                body.close();
            }
            if (response != null) {
                if (response.body() != null) {
                    response.body().close();
                }

                response.close();
            }
        } catch (IOException e) {
            Logger.e(TAG, "doClose: ", e);
        } finally {
            call = null;
            source = null;
            body = null;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            if (call != null) {
                call.cancel();
            }

            doClose();
        }).run();
    }
}
