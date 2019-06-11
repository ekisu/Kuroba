package com.github.adamantcheese.chan.core.cache;

import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public class RandomAccessStreamReplicator implements RandomAccessStream {
    private static final String TAG = "RandomAccessStreamReplicator";

    public interface InputStreamFactory {
        RandomAccessStream createInputStream(long startingPosition) throws IOException;
    }

    private static final int MAX_INPUT_STREAMS = 2;

    private final Deque<RandomAccessStream> inputStreams = new ArrayDeque<>(MAX_INPUT_STREAMS);
    private final InputStreamFactory inputStreamFactory;
    private long position = 0;

    public RandomAccessStreamReplicator(InputStreamFactory factory) throws IOException {
        this.inputStreamFactory = factory;

        createNewInputStream(position);
    }

    private RandomAccessStream findInputStream(long position) throws IOException {
        for (RandomAccessStream r : inputStreams) {
            if (r.position() == position) {
                return r;
            }
        }

        return null;
    }

    private RandomAccessStream createNewInputStream(long position) throws IOException {
        RandomAccessStream r = inputStreamFactory.createInputStream(position);
        if (inputStreams.size() == MAX_INPUT_STREAMS) {
            inputStreams.removeFirst();
        }

        inputStreams.add(r);
        return r;
    }

    @Override
    public long position() {
        return this.position;
    }

    @Override
    public long length() throws IOException {
        // As they're all from the same source, it should be fine to pick any of them.
        return inputStreams.getFirst().length();
    }

    @Override
    public int read(byte[] output, long offset, long length) throws IOException {
        RandomAccessStream inputStream = findInputStream(position);
        if (inputStream == null) {
            inputStream = createNewInputStream(position);
        }

        int readBytes = inputStream.read(output, offset, length);
        if (readBytes > 0) {
            this.position += readBytes;
        }

        return readBytes;
    }

    @Override
    public void seek(long pos) throws IOException {
        this.position = pos;
    }

    public void close() throws IOException {
        for (RandomAccessStream r : inputStreams) {
            r.close();
        }

        inputStreams.clear();
    }
}
