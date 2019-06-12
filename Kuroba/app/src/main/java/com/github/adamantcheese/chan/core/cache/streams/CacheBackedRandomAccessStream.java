package com.github.adamantcheese.chan.core.cache.streams;

import android.util.Range;

import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.RangeSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.List;

public class CacheBackedRandomAccessStream implements RandomAccessStream {
    private static final String TAG = "CacheBackedRandomAccessStream";

    private final File directory;
    private final String filename;
    private final RandomAccessFile file;
    private final RandomAccessStream inputStream;

    private RangeSet cachedRegions = new RangeSet();
    private Long metadataSavedLength = null;
    private long position = 0;

    public CacheBackedRandomAccessStream(File directory, String filename, RandomAccessStream inputStream) throws IOException {
        this.directory = directory;
        this.filename = filename;
        this.file = new RandomAccessFile(getBackingFile(), "rw");
        this.inputStream = inputStream;

        readMetadata();
    }

    private void readMetadata() throws IOException {
        Logger.d(TAG, "reading metadata");
        // TODO uhh serialize is ok?
        try (
                FileInputStream fout = new FileInputStream(getMetadataFile());
                ObjectInputStream oos = new ObjectInputStream(fout);
        ) {
            RangeSet metadataCachedRegions = (RangeSet) oos.readObject();
            if (metadataCachedRegions != null) {
                cachedRegions = metadataCachedRegions;
            }

            metadataSavedLength = (Long) oos.readObject();
        } catch (FileNotFoundException | ClassNotFoundException e) {
            Logger.e(TAG, "failed to read metadata", e);
        }
    }

    private void writeMetadata() throws IOException {
        Logger.d(TAG, "writing metadata");
        try (
                FileOutputStream fout = new FileOutputStream(getMetadataFile());
                ObjectOutputStream oos = new ObjectOutputStream(fout);
        ) {
            oos.writeObject(cachedRegions);
            oos.writeObject(new Long(length()));
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "failed to write metadata", e);
        }
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long length() throws IOException {
        if (metadataSavedLength != null) {
            return metadataSavedLength;
        } else {
            return inputStream.length();
        }
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
            Logger.d(TAG, "Reading cached part: " + cachedPart);
            file.seek(cachedPart.getLower());
            long cachedPartLength = cachedPart.getUpper() - cachedPart.getLower() + 1;
            readBytes = file.read(output, (int) offset, (int) cachedPartLength);
        } else if (metadataSavedLength != null && position >= metadataSavedLength) {
            // EOF.
            return -1;
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
        writeMetadata();

        inputStream.close();
        file.close();
    }

    public File getBackingFile() {
        return new File(directory, filename);
    }

    public File getMetadataFile() {
        return new File(directory, filename + ".metadata");
    }
}
