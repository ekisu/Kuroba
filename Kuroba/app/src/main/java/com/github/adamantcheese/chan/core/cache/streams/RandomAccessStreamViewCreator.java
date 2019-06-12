package com.github.adamantcheese.chan.core.cache.streams;

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

        public RandomAccessStream getInnerStream() {
            return this.parent.stream;
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
    private boolean closed = false;

    public RandomAccessStreamViewCreator(RandomAccessStream stream) {
        this.stream = stream;
    }

    protected long length() throws IOException {
        return this.stream.length();
    }

    protected int read(long position, byte[] output, long offset, long length) throws IOException{
        synchronized (this) {
            stream.seek(position);

            return stream.read(output, offset, length);
        }
    }

    public RandomAccessStreamView createView() {
        synchronized (this) {
            RandomAccessStreamView view = new RandomAccessStreamView(this);
            children.add(view);
            return view;
        }
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    protected void closeChildren(RandomAccessStreamView view) throws IOException {
        synchronized (this) {
            children.remove(view);

            if (children.size() == 0) {
                stream.close();
                closed = true;
            }
        }
    }
}
