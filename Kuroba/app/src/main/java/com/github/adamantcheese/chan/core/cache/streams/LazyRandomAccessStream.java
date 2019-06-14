package com.github.adamantcheese.chan.core.cache.streams;

import java.io.IOException;

public class LazyRandomAccessStream implements RandomAccessStream {
    public interface StreamFactory {
        RandomAccessStream createStream() throws IOException;
    }

    private final StreamFactory factory;
    private RandomAccessStream stream;
    private boolean initialized = false;
    private long startPosition = 0;

    public LazyRandomAccessStream(StreamFactory factory) {
        this.factory = factory;
    }

    private void initialize() throws IOException {
        stream = factory.createStream();
        stream.open(startPosition);
        initialized = true;
    }

    @Override
    public void open(long startPosition) {
        this.startPosition = startPosition;
    }

    @Override
    public long position() throws IOException {
        if (!initialized) {
            initialize();
        }

        return stream.position();
    }

    @Override
    public long length() throws IOException {
        if (!initialized) {
            initialize();
        }

        return stream.length();
    }

    @Override
    public int read(byte[] output, long offset, long length) throws IOException {
        if (!initialized) {
            initialize();
        }

        return stream.read(output, offset, length);
    }

    @Override
    public void seek(long pos) throws IOException {
        if (!initialized) {
            initialize();
        }

        stream.seek(pos);
    }

    @Override
    public void close() throws IOException {
        // It makes more sense than creating an stream, just to close it.
        if (initialized) {
            stream.close();
        }
    }
}
