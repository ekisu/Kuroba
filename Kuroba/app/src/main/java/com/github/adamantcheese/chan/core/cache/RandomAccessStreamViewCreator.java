package com.github.adamantcheese.chan.core.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RandomAccessStreamViewCreator {
    public class RandomAccessStreamView implements RandomAccessStream {
        private final RandomAccessStreamViewCreator parent;
        private long position = 0;

        public RandomAccessStreamView(RandomAccessStreamViewCreator parent) {
            this.parent = parent;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public long length() throws IOException {
            return parent.length();
        }

        @Override
        public int read(byte[] output, long offset, long length) throws IOException {
            int readBytes = parent.read(position, output, offset, length);

            if (readBytes > 0) {
                this.position += readBytes;
            }

            return readBytes;
        }

        @Override
        public void seek(long pos) {
            this.position = pos;
        }

        @Override
        public void close() throws IOException {
            parent.closeChildren(this);
        }
    }

    private final RandomAccessStream stream;
    private final List<RandomAccessStreamView> children = new ArrayList<>();

    public RandomAccessStreamViewCreator(RandomAccessStream stream) {
        this.stream = stream;
    }

    protected long length() throws IOException {
        return this.stream.length();
    }

    protected int read(long position, byte[] output, long offset, long length) throws IOException{
        synchronized (stream) {
            stream.seek(position);

            return stream.read(output, offset, length);
        }
    }

    public RandomAccessStreamView createView() {
        RandomAccessStreamView view = new RandomAccessStreamView(this);
        children.add(view);
        return view;
    }

    protected void closeChildren(RandomAccessStreamView view) {
        children.remove(view);
    }
}
