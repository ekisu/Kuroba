package com.github.adamantcheese.chan.core.cache;

import android.net.Uri;
import android.util.Range;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class FileCacheDataSource extends BaseDataSource {
    public interface UriRandomAccessStreamFactory {
        RandomAccessStream createStream(Uri uri, long position) throws IOException;
    }

    private final String TAG = "FileCacheDataSource";

    private final RandomAccessStream inputStream;
    private @Nullable Uri uri;
    private long bytesRemaining = 0;

    private boolean opened = false;

    public FileCacheDataSource(
            RandomAccessStream inputStream) {
        super(/* isNetwork= */ true);
        Logger.i(TAG, "new");
        this.inputStream = inputStream;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);

        Logger.i(TAG, "opening, position: " + dataSpec.position + " length: " + dataSpec.length);
        inputStream.seek(dataSpec.position);

        bytesRemaining = dataSpec.length == C.LENGTH_UNSET
            ? inputStream.length() - dataSpec.position
            : dataSpec.length;

        Logger.i(TAG, "bytes remaining: " + bytesRemaining);
        if (bytesRemaining < 0) {
            throw new EOFException();
        }

        transferStarted(dataSpec);

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int maxReadableBytes = (int) Math.min(bytesRemaining, readLength);
        int readBytes = inputStream.read(buffer, offset, maxReadableBytes);

        if (readBytes > 0) {
            bytesTransferred(readBytes);
            bytesRemaining -= readBytes;
        }

        return readBytes;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        Logger.i(TAG, "close");
        if (opened) {
            opened = false;
            transferEnded();
        }
    }
}
