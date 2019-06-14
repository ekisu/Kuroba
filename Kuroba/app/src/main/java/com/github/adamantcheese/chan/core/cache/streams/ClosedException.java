package com.github.adamantcheese.chan.core.cache.streams;

import java.io.IOException;

public class ClosedException extends IOException {
    public ClosedException() {
        super();
    }

    public ClosedException(String desc) {
        super(desc);
    }
}
