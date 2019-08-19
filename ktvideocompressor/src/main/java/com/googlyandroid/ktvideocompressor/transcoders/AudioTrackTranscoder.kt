package com.googlyandroid.ktvideocompressor.transcoders

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.googlyandroid.ktvideocompressor.compat.MediaCodecBufferCompatWrapper
import com.googlyandroid.ktvideocompressor.engine.AudioChannel
import com.googlyandroid.ktvideocompressor.muxer.QueuedMuxer

class AudioTrackTranscoder(private val mediaExtractor: MediaExtractor,
    private val mAudioTrackIndex: Int, private val audioOutputFormat: MediaFormat,
    private val queuedMuxer: QueuedMuxer) : TrackTranscoder {

  private var audioChannel: AudioChannel? = null
  private var decoderBuffers: MediaCodecBufferCompatWrapper? = null
  private var encoderBuffers: MediaCodecBufferCompatWrapper? = null

  private var encoderStarted: Boolean = false
  private var encoder: MediaCodec? = null

  private var decoder: MediaCodec? = null
  private var decoderStarted: Boolean = false

  override fun setup() {
    mediaExtractor.selectTrack(mAudioTrackIndex)
    encoder = MediaCodec.createEncoderByType(audioOutputFormat.getString(MediaFormat.KEY_MIME))
    encoder?.configure(audioOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    encoder?.start()
    encoderStarted = true
    encoder?.let {
      encoderBuffers = MediaCodecBufferCompatWrapper(it)
    }

    val inputFormat = mediaExtractor.getTrackFormat(mAudioTrackIndex)
    decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME))
    decoder?.configure(inputFormat, null, null, 0)
    decoder?.start()
    decoderStarted = true
    decoder?.let {
      decoderBuffers = MediaCodecBufferCompatWrapper(it)
    }

    decoder?.let { dec ->
      encoder?.let { enc ->
        audioChannel = AudioChannel(dec, enc, audioOutputFormat)
      }
    }

  }

  override fun getDeterminedFormat(): MediaFormat? {

  }

  override fun stepPipeline(): Boolean {

  }

  override fun getWrittenPresentationTimeUS(): Long {

  }

  override fun isFinished(): Boolean {

  }

  override fun release() {

  }

}