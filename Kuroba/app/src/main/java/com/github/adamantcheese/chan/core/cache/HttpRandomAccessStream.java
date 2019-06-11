package com.github.adamantcheese.chan.core.cache;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

public class HttpRandomAccessStream implements RandomAccessStream {
    private final OkHttpClient httpClient;
    private final long startPosition;
    private final String url;

    private final long contentLength;
    private long position = 0;

    private Call call;
    private ResponseBody body;
    private BufferedSource source;

    public HttpRandomAccessStream(OkHttpClient httpClient, String url, long startPosition) throws IOException {
        this.httpClient = httpClient;
        this.url = url;
        this.startPosition = startPosition;
        this.position = startPosition;

        // TODO opening on creation is... weird.
        getBody(startPosition);
        contentLength = body.contentLength();
        source = body.source();
    }

    private void getBody(long startPosition) throws IOException {
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

        Response response = call.execute();
        if (!response.isSuccessful()) {
            // TODO this was HTTPCodeIOException before.
            throw new IOException("Invalid response: " + response.code());
        }

        body = response.body();
        if (body == null) {
            throw new IOException("body == null");
        }
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long length() {
        return startPosition + contentLength;
    }

    @Override
    public int read(byte[] output, long offset, long length) throws IOException {
        int bytesRead = source.read(output, (int) offset, (int) length);
        if (bytesRead > 0) {
            this.position += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public void seek(long pos) {
        /* TODO this is not seekable. change the interface type used with RandomAccessStreamReplicator
         to something that reflects this. */
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
