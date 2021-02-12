package com.guichaguri.trackplayer.service;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.PlayerMessage;

public class EqualizedExoPlayer implements ExoPlayer {

    private static final int NO_AUDIO_SESSION_ID = 0;

    private Context mContext;

    private SimpleExoPlayer mExoPlayer;
    private Equalizer mEqualizer;

    private boolean mEqualizerEnabled;
    private Equalizer.Settings mEqualizerSettings;

    public EqualizedExoPlayer(Context context, SimpleExoPlayer delegate) {
        mContext = context;
        mExoPlayer = delegate;
        mExoPlayer.setAudioDebugListener(new EqualizerEventListener());
    }

    private void onAudioSessionId(int sessionId) {
        Bundle bundle = new Bundle();
        bundle.putInt("sessionId", sessionId);
        MusicService service = (MusicService) mContext;
        service.emit("audio-session-id", bundle);
    }

    public Equalizer getEqualizer() {
        if (mEqualizer == null) {
            Log.i("EQExoPlayer", "Initializing equalizer...");
            updateEqualizerPrefs(true, true);
        }
        return mEqualizer;
    }

    public void setEqualizerSettings(boolean enabled, Equalizer.Settings settings) {
        boolean invalidate = mEqualizerEnabled != enabled || mEqualizerEnabled;
        boolean wasSystem = isUsingSystemEqualizer();

        mEqualizerEnabled = enabled;
        mEqualizerSettings = settings;

        if (invalidate) {
            updateEqualizerPrefs(enabled, wasSystem);
        }
    }

    private void updateEqualizerPrefs(boolean useCustom, boolean wasSystem) {
        Log.i("EQExoPlayer", "Updating equalizer prefs...");
        int audioSessionId = mExoPlayer.getAudioSessionId();
        Log.i("EQExoPlayer", "AudioSessionId=" + String.valueOf(audioSessionId));

        if (audioSessionId == NO_AUDIO_SESSION_ID) {
            // No equalizer is currently bound. Nothing to do.
            return;
        }

        if (useCustom) {
            if (wasSystem || mEqualizer == null) {
                // System -> custom
                unbindSystemEqualizer(audioSessionId);
                bindCustomEqualizer(audioSessionId);
            } else {
                // Custom -> custom
                mEqualizer.setProperties(mEqualizerSettings);
            }
        } else {
            if (!wasSystem) {
                // Custom -> system
                unbindCustomEqualizer();
                bindSystemEqualizer(audioSessionId);
            }
            // Nothing to do for system -> system
        }
    }

    private boolean isUsingSystemEqualizer() {
        return false; // mEqualizerSettings == null || !mEqualizerEnabled;
    }

    private void onBindEqualizer(int newAudioSessionId) {
        onAudioSessionId(newAudioSessionId);
        if (isUsingSystemEqualizer()) {
            bindSystemEqualizer(newAudioSessionId);
        } else {
            bindCustomEqualizer(newAudioSessionId);
        }
    }

    private void bindSystemEqualizer(int audioSessionId) {
        Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

    private void bindCustomEqualizer(int audioSessionId) {
        mEqualizer = new Equalizer(0, audioSessionId);
        if (mEqualizerSettings != null)
            mEqualizer.setProperties(mEqualizerSettings);
        mEqualizer.setEnabled(true);
    }

    private void onUnbindEqualizer(int oldAudioSessionId) {
        if (isUsingSystemEqualizer()) {
            unbindSystemEqualizer(oldAudioSessionId);
        } else {
            unbindCustomEqualizer();
        }
    }

    private void unbindSystemEqualizer(int audioSessionId) {
        Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

    public void destroy() {
        if (mEqualizer != null) {
            Log.i("EQExoPlayer", "Destroying equalizer...");
            mEqualizer.setEnabled(false);
            mEqualizer.release();
            mEqualizer = null;
        }
    }

    private void unbindCustomEqualizer() {
        destroy();
    }

    private class EqualizerEventListener implements AudioRendererEventListener {

        private int lastAudioSessionId = NO_AUDIO_SESSION_ID;

        @Override
        public void onAudioSessionId(int audioSessionId) {
            if (audioSessionId != NO_AUDIO_SESSION_ID) {
                onBindEqualizer(audioSessionId);
                lastAudioSessionId = audioSessionId;
            }
        }

        @Override
        public void onAudioDisabled(DecoderCounters counters) {
            if (lastAudioSessionId != NO_AUDIO_SESSION_ID) {
                onUnbindEqualizer(lastAudioSessionId);
            }
        }

        @Override
        public void onAudioEnabled(DecoderCounters counters) {
        }

        @Override
        public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
                                              long initializationDurationMs) {
        }

        @Override
        public void onAudioInputFormatChanged(Format format) {
        }
    }

    // region DELEGATED METHODS

    public void setVolume(float volume) {
        mExoPlayer.setVolume(volume);
    }

    public float getVolume() {
        return mExoPlayer.getVolume();
    }

    @Override
    public Looper getPlaybackLooper() {
        return mExoPlayer.getPlaybackLooper();
    }

    @Override
    public long getContentBufferedPosition() {
        return mExoPlayer.getContentBufferedPosition();
    }

    @Override
    public SeekParameters getSeekParameters() {
        return mExoPlayer.getSeekParameters();
    }

    @Override
    public void setSeekParameters(SeekParameters parameters) {
        mExoPlayer.setSeekParameters(parameters);
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        return mExoPlayer.createMessage(target);
    }

    @Override
    public void setForegroundMode(boolean mode) {
        mExoPlayer.setForegroundMode(mode);
    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        return mExoPlayer.getAudioComponent();
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        return mExoPlayer.getVideoComponent();
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        return mExoPlayer.getTextComponent();
    }

    @Nullable
    @Override
    public MetadataComponent getMetadataComponent() {
        return mExoPlayer.getMetadataComponent();
    }

    @Override
    public Looper getApplicationLooper() {
        return mExoPlayer.getApplicationLooper();
    }

    @Override
    public void addListener(EventListener listener) {
        mExoPlayer.addListener(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        mExoPlayer.removeListener(listener);
    }

    @Override
    public int getPlaybackState() {
        return mExoPlayer.getPlaybackState();
    }

    @Nullable
    @Override
    public ExoPlaybackException getPlaybackError() {
        return mExoPlayer.getPlaybackError();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        mExoPlayer.prepare(mediaSource);
        mExoPlayer.setAudioDebugListener(new EqualizerEventListener());
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline) {
        mExoPlayer.prepare(mediaSource, resetPosition, resetTimeline);
        mExoPlayer.setAudioDebugListener(new EqualizerEventListener());
    }

    @Override
    public void retry() {
        mExoPlayer.retry();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        mExoPlayer.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayer.getPlayWhenReady();
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        mExoPlayer.setRepeatMode(repeatMode);
    }

    @Override
    public int getRepeatMode() {
        return mExoPlayer.getRepeatMode();
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        mExoPlayer.setShuffleModeEnabled(shuffleModeEnabled);
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return mExoPlayer.getShuffleModeEnabled();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayer.isLoading();
    }

    @Override
    public void seekToDefaultPosition() {
        mExoPlayer.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
        mExoPlayer.seekToDefaultPosition(windowIndex);
    }

    @Override
    public void seekTo(long windowPositionMs) {
        mExoPlayer.seekTo(windowPositionMs);
    }

    @Override
    public void seekTo(int windowIndex, long windowPositionMs) {
        mExoPlayer.seekTo(windowIndex, windowPositionMs);
    }

    @Override
    public boolean hasPrevious() {
        return mExoPlayer.hasPrevious();
    }

    @Override
    public void previous() {
        mExoPlayer.previous();
    }

    @Override
    public boolean hasNext() {
        return mExoPlayer.hasNext();
    }

    @Override
    public void next() {

    }

    @Override
    public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
        mExoPlayer.setPlaybackParameters(playbackParameters);
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return mExoPlayer.getPlaybackParameters();
    }

    @Override
    public void stop() {
        mExoPlayer.stop();
    }

    @Override
    public void stop(boolean reset) {
        mExoPlayer.stop();
    }

    @Override
    public void release() {
        mExoPlayer.release();
        mExoPlayer = null;
        destroy();
    }

    @Override
    public void sendMessages(ExoPlayerMessage... messages) {
        mExoPlayer.sendMessages(messages);
    }

    @Override
    public void blockingSendMessages(ExoPlayerMessage... messages) {
        mExoPlayer.blockingSendMessages(messages);
    }

    @Override
    public int getRendererCount() {
        return mExoPlayer.getRendererCount();
    }

    @Override
    public int getRendererType(int index) {
        return mExoPlayer.getRendererType(index);
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        return mExoPlayer.getCurrentTrackGroups();
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        return mExoPlayer.getCurrentTrackSelections();
    }

    @Override
    public Object getCurrentManifest() {
        return mExoPlayer.getCurrentManifest();
    }

    @Override
    public Timeline getCurrentTimeline() {
        return mExoPlayer.getCurrentTimeline();
    }

    @Override
    public int getCurrentPeriodIndex() {
        return mExoPlayer.getCurrentPeriodIndex();
    }

    @Override
    public int getCurrentWindowIndex() {
        return mExoPlayer.getCurrentWindowIndex();
    }

    @Override
    public int getNextWindowIndex() {
        return mExoPlayer.getNextWindowIndex();
    }

    @Override
    public int getPreviousWindowIndex() {
        return mExoPlayer.getPreviousWindowIndex();
    }

    @Nullable
    @Override
    public Object getCurrentTag() {
        return mExoPlayer.getCurrentTag();
    }

    @Override
    public long getDuration() {
        return mExoPlayer.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return mExoPlayer.getCurrentPosition();
    }

    @Override
    public long getBufferedPosition() {
        return mExoPlayer.getBufferedPosition();
    }

    @Override
    public int getBufferedPercentage() {
        return mExoPlayer.getBufferedPercentage();
    }

    @Override
    public long getTotalBufferedDuration() {
        return mExoPlayer.getTotalBufferedDuration();
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return mExoPlayer.isCurrentWindowDynamic();
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return mExoPlayer.isCurrentWindowSeekable();
    }

    @Override
    public boolean isPlayingAd() {
        return mExoPlayer.isPlayingAd();
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return mExoPlayer.getCurrentAdGroupIndex();
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return mExoPlayer.getCurrentAdIndexInAdGroup();
    }

    @Override
    public long getContentDuration() {
        return mExoPlayer.getContentDuration();
    }

    @Override
    public long getContentPosition() {
        return mExoPlayer.getContentPosition();
    }

    // endregion DELEGATED METHODS
}
