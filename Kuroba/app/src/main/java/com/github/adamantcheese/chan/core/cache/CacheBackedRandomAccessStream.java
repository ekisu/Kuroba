package com.github.adamantcheese.chan.core.cache;

import android.util.Range;

import com.github.adamantcheese.chan.utils.RangeSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class CacheBackedRandomAccessStream implements RandomAccessStream {
    private final File directory;
    private final String filename;
    private final RandomAccessFile file;
    private final RandomAccessStream inputStream;

    private RangeSet cachedRegions = new RangeSet();
    private long position = 0;

    public CacheBackedRandomAccessStream(File directory, String filename, RandomAccessStream inputStream) throws FileNotFoundException {
        this.directory = directory;
        this.filename = filename;
        this.file = new RandomAccessFile(new File(directory, filename), "rw");
        this.inputStream = inputStream;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long length() throws IOException {
        return inputStream.length();
    }

    private Range<Long> getCachedRange(long length) {
        return cachedRegions.intersect(new Range<>(position, position + length - 1));
    }

    private void writeToCache(long position, byte[] data, int offset, int length) throws IOException {
        file.seek(position);
        file.write(data, offset, length);

        cachedRegions.union(new Range<>(position, position + length - 1));
    }

    // TODO change all longs to int?
    @Override
    public int read(byte[] output, long offset, long length) throws IOException {
        int readBytes;
        // TODO what if not everything is cached, but a part is?
        Range<Long> cachedPart = getCachedRange(length);
        if (cachedPart != null) {
            file.seek(cachedPart.getLower());
            long cachedPartLength = cachedPart.getUpper() - cachedPart.getLower() + 1;
            readBytes = file.read(output, (int) offset, (int) cachedPartLength);
        } else {
            inputStream.seek(position);
            readBytes = inputStream.read(output, offset, length);

            if (readBytes > 0) {
                writeToCache(position, output, (int) offset, readBytes);
            }
        }

        if (readBytes > 0) {
            position += readBytes;
        }

        return readBytes;
    }

    @Override
    public void seek(long pos) {
        this.position = pos;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
