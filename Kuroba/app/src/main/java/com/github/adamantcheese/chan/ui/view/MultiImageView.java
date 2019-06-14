/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.FileCacheDownloader;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.cache.FileCacheDataSource;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.streams.CacheBackedRandomAccessStream;
import com.github.adamantcheese.chan.core.cache.streams.RandomAccessStreamViewCreator;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import static com.github.adamantcheese.chan.Chan.inject;

public class MultiImageView extends FrameLayout implements View.OnClickListener, AudioListener, LifecycleObserver {
    public enum Mode {
        UNLOADED, LOWRES, BIGIMAGE, GIF, MOVIE
    }

    private static final String TAG = "MultiImageView";

    @Inject
    FileCache fileCache;

    @Inject
    ImageLoader imageLoader;

    private Context context;
    private ImageView playView;

    private PostImage postImage;
    private Callback callback;
    private Mode mode = Mode.UNLOADED;

    private boolean hasContent = false;
    private ImageContainer thumbnailRequest;
    private FileCacheDownloader bigImageRequest;
    private FileCacheDownloader gifRequest;
    private FileCacheDownloader videoRequest;

    private SimpleExoPlayer exoPlayer;
    private FileCacheDataSource videoDataSource;

    private boolean backgroundToggle;

    public MultiImageView(Context context) {
        this(context, null);
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;

        inject(this);
        setOnClickListener(this);

        playView = new ImageView(getContext());
        playView.setVisibility(View.GONE);
        playView.setImageResource(R.drawable.ic_play_circle_outline_white_48dp);
        addView(playView, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        if (context instanceof StartActivity) {
            ((StartActivity) context).getLifecycle().addObserver(this);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;

        playView.setVisibility(postImage.type == PostImage.Type.MOVIE ? View.VISIBLE : View.GONE);
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(final Mode newMode, boolean center) {
        if (this.mode != newMode) {
            this.mode = newMode;

            AndroidUtils.waitForMeasure(this, view -> {
                switch (newMode) {
                    case LOWRES:
                        setThumbnail(postImage.getThumbnailUrl().toString(), center);
                        break;
                    case BIGIMAGE:
                        setBigImage(postImage.imageUrl.toString());
                        break;
                    case GIF:
                        setGif(postImage.imageUrl.toString());
                        break;
                    case MOVIE:
                        setVideo(postImage.imageUrl.toString());
                        break;
                }
                return true;
            });
        }
    }

    public Mode getMode() {
        return mode;
    }

    public CustomScaleImageView findScaleImageView() {
        CustomScaleImageView bigImage = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CustomScaleImageView) {
                bigImage = (CustomScaleImageView) getChildAt(i);
            }
        }
        return bigImage;
    }

    public GifImageView findGifImageView() {
        GifImageView gif = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof GifImageView) {
                gif = (GifImageView) getChildAt(i);
            }
        }
        return gif;
    }

    public void setVolume(boolean muted) {
        final float volume = muted ? 0f : 1f;
        if (exoPlayer != null) {
            Player.AudioComponent audioComponent = exoPlayer.getAudioComponent();
            if (audioComponent != null) {
                audioComponent.setVolume(volume);
            }
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        //cancel any loads
        if (thumbnailRequest != null) {
            thumbnailRequest.cancelRequest();
            thumbnailRequest = null;
        }
        if (bigImageRequest != null) {
            bigImageRequest.cancel();
            bigImageRequest = null;
        }
        if (gifRequest != null) {
            gifRequest.cancel();
            gifRequest = null;
        }
        if (videoRequest != null) {
            videoRequest.cancel();
            videoRequest = null;
        }

        if (exoPlayer != null) {
            // ExoPlayer will keep loading resources if we don't release it here.
            exoPlayer.release();
            exoPlayer = null;
        }

        if (videoDataSource != null) {
            videoDataSource.closeStream();
            videoDataSource = null;
        }

        if (context instanceof StartActivity) {
            ((StartActivity) context).getLifecycle().removeObserver(this);
        }

        context = null;
    }

    private void setThumbnail(String thumbnailUrl, boolean center) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        if (thumbnailRequest != null) {
            return;
        }

        // Also use volley for the thumbnails
        thumbnailRequest = imageLoader.get(thumbnailUrl, new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                thumbnailRequest = null;
                if (center) {
                    onError();
                }
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                thumbnailRequest = null;
                if (response.getBitmap() != null && (!hasContent || mode == Mode.LOWRES)) {
                    ImageView thumbnail = new ImageView(getContext());
                    thumbnail.setImageBitmap(response.getBitmap());

                    onModeLoaded(Mode.LOWRES, thumbnail);
                }
            }
        }, getWidth(), getHeight());

        if (thumbnailRequest.getBitmap() != null) {
            // Request was immediate and thumbnailRequest was first set to null in onResponse, and then set to the container
            // when the method returned
            // Still set it to null here
            thumbnailRequest = null;
        }
    }

    private void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

        if (bigImageRequest != null) {
            return;
        }

        callback.showProgress(this, true);
        bigImageRequest = fileCache.downloadFile(imageUrl, new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onProgress(MultiImageView.this, downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }

            @Override
            public void onEnd() {
                bigImageRequest = null;
                callback.showProgress(MultiImageView.this, false);
            }
        });
    }

    private void setBigImageFile(File file) {
        setBitImageFileInternal(file, true, Mode.BIGIMAGE);
    }

    private void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        if (gifRequest != null) {
            return;
        }

        callback.showProgress(this, true);
        gifRequest = fileCache.downloadFile(gifUrl, new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onProgress(MultiImageView.this, downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                if (!hasContent || mode == Mode.GIF) {
                    setGifFile(file);
                }
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }

            @Override
            public void onEnd() {
                gifRequest = null;
                callback.showProgress(MultiImageView.this, false);
            }
        });
    }

    private void setGifFile(File file) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(file);

            // For single frame gifs, use the scaling image instead
            // The region decoder doesn't work for gifs, so we unfortunately
            // have to use the more memory intensive non tiling mode.
            if (drawable.getNumberOfFrames() == 1) {
                drawable.recycle();
                setBitImageFileInternal(file, false, Mode.GIF);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            onError();
            return;
        } catch (OutOfMemoryError e) {
            Runtime.getRuntime().gc();
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
            callback.showProgress(this, false);
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        onModeLoaded(Mode.GIF, view);
    }

    private void setVideo(String videoUrl) {
        openVideoInternalStream(videoUrl);
    }

    private void openVideoInternalStream(String videoUrl) {
        try {
            videoDataSource = fileCache.createDataSource(videoUrl);
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(() -> videoDataSource)
                    .createMediaSource(Uri.parse(videoUrl));

            if (!hasContent || mode == Mode.MOVIE) {
                PlayerView exoVideoView = new PlayerView(getContext());
                exoPlayer = ExoPlayerFactory.newSimpleInstance(getContext());
                exoVideoView.setPlayer(exoPlayer);

                exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get() ?
                        Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);

                exoPlayer.prepare(mediaSource);
                exoPlayer.setVolume(0f);
                exoPlayer.addAudioListener(MultiImageView.this);

                addView(exoVideoView);
                exoPlayer.setPlayWhenReady(true);
                onModeLoaded(Mode.MOVIE, exoVideoView);
                callback.onVideoLoaded(MultiImageView.this);
            }
        } catch (IOException e) {
            Logger.e(TAG, "IOException", e);
        }
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (exoPlayer.getAudioFormat() != null) {
            callback.onAudioLoaded(this);
        }
    }

    public void toggleTransparency() {
        final int BACKGROUND_COLOR = Color.argb(255, 211, 217, 241);
        CustomScaleImageView imageView = findScaleImageView();
        GifImageView gifView = findGifImageView();
        if (imageView == null && gifView == null) return;
        boolean isImage = imageView != null && gifView == null;
        if (backgroundToggle) {
            if (isImage) {
                imageView.setTileBackgroundColor(Color.TRANSPARENT);
            } else {
                gifView.getDrawable().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.DST_OVER);
            }
            backgroundToggle = false;
        } else {
            if (isImage) {
                imageView.setTileBackgroundColor(BACKGROUND_COLOR);
            } else {
                gifView.getDrawable().setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.DST_OVER);
            }
            backgroundToggle = true;
        }
    }

    public void rotateImage(boolean CW) {
        CustomScaleImageView imageView = findScaleImageView();
        if (imageView == null) return;
        //swap the current scale to the opposite one every 90 degree increment
        //0 degrees is X scale, 90 is Y, 180 is X, 270 is Y
        float curScale = imageView.getScale();
        float scaleX = imageView.getWidth() / (float) imageView.getSWidth();
        float scaleY = imageView.getHeight() / (float) imageView.getSHeight();
        imageView.setScaleAndCenter(curScale == scaleX ? scaleY : scaleX, imageView.getCenter());
        //apply the rotation through orientation rather than rotation, as
        //orientation is internal to the subsamplingimageview's internal bitmap while rotation is on the entire view
        switch (imageView.getAppliedOrientation()) {
            case SubsamplingScaleImageView.ORIENTATION_0:
                if (CW) {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_90);
                } else {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_270);
                }
                break;
            case SubsamplingScaleImageView.ORIENTATION_90:
                if (CW) {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_180);
                } else {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
                }
                break;
            case SubsamplingScaleImageView.ORIENTATION_180:
                if (CW) {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_270);
                } else {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_90);
                }
                break;
            case SubsamplingScaleImageView.ORIENTATION_270:
                if (CW) {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
                } else {
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_180);
                }
                break;
        }
    }

    private void setBitImageFileInternal(File file, boolean tiling, final Mode forMode) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(ImageSource.uri(file.getAbsolutePath()).tiling(tiling));
        image.setOnClickListener(MultiImageView.this);
        addView(image, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == forMode) {
                    callback.showProgress(MultiImageView.this, false);
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onError(boolean wasInitial) {
                if (wasInitial) {
                    Toast.makeText(getContext(), R.string.image_failed_big_image, Toast.LENGTH_SHORT).show();
                    callback.showProgress(MultiImageView.this, false);
                }
            }
        });
    }

    private void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
        callback.showProgress(this, false);
    }

    private void onNotFoundError() {
        callback.showProgress(this, false);
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_SHORT).show();
    }

    private void onModeLoaded(Mode mode, View view) {
        if (view != null) {
            // Remove all other views
            boolean alreadyAttached = false;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != playView) {
                    if (child != view) {
                        if (child instanceof PlayerView) {
                            ((PlayerView) child).getPlayer().release();
                        }
                        removeViewAt(i);
                    } else {
                        alreadyAttached = true;
                    }
                }
            }

            if (!alreadyAttached) {
                addView(view, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    public interface Callback {
        void onTap();

        void showProgress(MultiImageView multiImageView, boolean progress);

        void onProgress(MultiImageView multiImageView, long current, long total);

        void onVideoLoaded(MultiImageView multiImageView);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);

        void onAudioLoaded(MultiImageView multiImageView);
    }
}
