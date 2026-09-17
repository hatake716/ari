package io.github.hatake716.ari

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

/** Lifecycle-aware, offline, original music. Respects calls and other audio focus owners. */
class AmbientMusic(private val context: Context) {
    private val preferences=context.getSharedPreferences("audio",Context.MODE_PRIVATE)
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var foreground=false
    private var player: MediaPlayer?=null
    var enabled=preferences.getBoolean("enabled",true)
        private set
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            when(change) {
                AudioManager.AUDIOFOCUS_GAIN -> {player?.setVolume(.5f,.5f);if(foreground && enabled)startPlayer()}
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(.12f,.12f)
                else -> player?.pause()
            }
        }.build()
    fun setEnabled(value: Boolean) {
        enabled=value;preferences.edit().putBoolean("enabled",value).apply()
        if(value && foreground)resume()else pausePlayer()
    }
    fun resume() {
        foreground=true
        if(enabled && manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)startPlayer()
    }
    private fun startPlayer() {
        try {
            if(player==null) {
                player=MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    context.assets.openFd("underground-afternoon.ogg").use {setDataSource(it.fileDescriptor,it.startOffset,it.length)}
                    isLooping=true;setVolume(.5f,.5f);prepare()
                }
            }
            player?.start()
        } catch(_: Exception) {player?.release();player=null}
    }
    private fun pausePlayer() {player?.pause();manager.abandonAudioFocusRequest(focus)}
    fun pause() {foreground=false;pausePlayer()}
    fun release() {pause();player?.release();player=null}
}
