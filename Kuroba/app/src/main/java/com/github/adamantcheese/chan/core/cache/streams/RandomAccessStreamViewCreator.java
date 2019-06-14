package com.github.adamantcheese.chan.core.cache.streams;

import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RandomAccessStreamViewCreator<S extends RandomAccessStream> {
    public class RandomAccessStreamView implements RandomAccessStream {
        private static final String TAG = "RandomAccessStreamView";
        private final RandomAccessStreamViewCreator<S> parent;
        private long position = 0;

        public RandomAccessStreamView(RandomAccessStreamViewCreator<S> parent) {
            this.parent = parent;
        }

        @Override
        public void open(long startPosition) throws IOException {
            parent.open(startPosition);
            this.seek(startPosition);
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public long length() throws IOException {
            return parent.length();
        }

        public S getInnerStream() {
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

    private static final String TAG = "RandomAccessStreamViewCreator";

    private final S stream;
    private final List<RandomAccessStreamView> children = new ArrayList<>();
    private boolean opened = false;
    private boolean closed = false;

    public RandomAccessStreamViewCreator(S stream) {
        this.stream = stream;
    }

    protected void open(long startPosition) throws IOException {
        if (opened) return;

        stream.open(startPosition);
        opened = true;
    }

    protected long length() throws IOException {
        return this.stream.length();
    }

    protected int read(long position, byte[] output, long offset, long length) throws IOException {
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
