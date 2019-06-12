package com.github.adamantcheese.chan.core.cache.streams;

import java.io.IOException;

public interface RandomAccessStream {
    long position() throws IOException;
    long length() throws IOException;
    int read(byte[] output, long offset, long length) throws IOException;
    void seek(long pos) throws IOException;
    void close() throws IOException;
}
