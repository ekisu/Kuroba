package com.github.adamantcheese.chan.core.cache.streams.adaptors;

import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStream;

import java.io.IOException;
import java.io.InputStream;

public class RandomAccessStreamToInputStreamAdaptor extends InputStream {
    private final RandomAccessStream stream;

    public RandomAccessStreamToInputStreamAdaptor(RandomAccessStream stream) {
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        byte[] byteArray = new byte[1];

        int readBytes = stream.read(byteArray, 0, 1);
        if (readBytes > 0) {
            return byteArray[0];
        } else {
            return -1;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
    }
}
