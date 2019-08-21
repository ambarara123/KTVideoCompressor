package com.googlyandroid.ktvideocompressor.transcoders

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.googlyandroid.ktvideocompressor.compat.MediaCodecBufferCompatWrapper
import com.googlyandroid.ktvideocompressor.engine.AudioChannel
import com.googlyandroid.ktvideocompressor.muxer.QueuedMuxer
import com.googlyandroid.ktvideocompressor.muxer.SampleInfo

class AudioTrackTranscoder(private val mediaExtractor: MediaExtractor,
    private val mAudioTrackIndex: Int,
    private val audioOutputFormat: MediaFormat,
    private val queuedMuxer: QueuedMuxer) : TrackTranscoder {

  private lateinit var encoder: MediaCodec
  private lateinit var mDecoder: MediaCodec
  private var mInputFormat: MediaFormat? = null
  private var mActualOutputFormat: MediaFormat? = null
  private val DRAIN_STATE_NONE = 0
  private val DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1
  private val DRAIN_STATE_CONSUMED = 2

  private var audioChannel: AudioChannel? = null
  private var decoderBuffers: MediaCodecBufferCompatWrapper? = null
  private var encoderBuffers: MediaCodecBufferCompatWrapper? = null

  private var encoderStarted: Boolean = false
  private var decoderStarted: Boolean = false


  private var mIsExtractorEOS: Boolean = false
  private var mIsDecoderEOS: Boolean = false
  private var mIsEncoderEOS: Boolean = false

  private var mWrittenPresentationTimeUs: Long = 0


  init {
    mInputFormat = mediaExtractor.getTrackFormat(mAudioTrackIndex);

  }

  override fun setup() {
    mediaExtractor.selectTrack(mAudioTrackIndex)

    encoder = initEncoder()

    mDecoder = initDecoder()

    audioChannel = AudioChannel(mDecoder, encoder, audioOutputFormat)
  }

  private fun initDecoder(): MediaCodec {
    val inputFormat = mediaExtractor.getTrackFormat(mAudioTrackIndex)
    val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME))
    decoder.configure(inputFormat, null, null, 0)
    decoder.start()
    decoderStarted = true
    decoderBuffers = MediaCodecBufferCompatWrapper(decoder)
    return decoder
  }

  private fun initEncoder(): MediaCodec {
    val encoder = MediaCodec.createEncoderByType(audioOutputFormat.getString(MediaFormat.KEY_MIME))
    encoder.configure(audioOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    encoder.start()
    encoderStarted = true
    encoderBuffers = MediaCodecBufferCompatWrapper(encoder)
    return encoder
  }

  override fun getDeterminedFormat() = mInputFormat

  override fun stepPipeline(mBufferInfo: MediaCodec.BufferInfo): Boolean {
    var busy = false

    var status: Int
    while (drainEncoder(0, mBufferInfo) != DRAIN_STATE_NONE) busy = true
    do {
      status = drainDecoder(0, mBufferInfo)
      if (status != DRAIN_STATE_NONE) busy = true
      // NOTE: not repeating to keep from deadlock when encoder is full.
    } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY)

    while (audioChannel?.feedEncoder(0) == true) busy = true
    while (drainExtractor(0) != DRAIN_STATE_NONE) busy = true

    return busy
  }

  private fun drainExtractor(timeoutUs: Long): Int {
    if (mIsExtractorEOS) return DRAIN_STATE_NONE
    val trackIndex = mediaExtractor.sampleTrackIndex
    if (trackIndex >= 0 && trackIndex != mAudioTrackIndex) {
      return DRAIN_STATE_NONE
    }
    val result = mDecoder.dequeueInputBuffer(timeoutUs)
    if (result < 0) return DRAIN_STATE_NONE
    if (trackIndex < 0) {
      mIsExtractorEOS = true
      mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      return DRAIN_STATE_NONE
    }

    val sampleSize = decoderBuffers?.getInputBuffer(result)?.let { it1 ->
      mediaExtractor.readSampleData(it1, 0)
    }
    val isKeyFrame = mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
    sampleSize?.let { it1 ->
      mDecoder.queueInputBuffer(result, 0, it1, mediaExtractor.sampleTime,
          if (isKeyFrame) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0)
    }
    mediaExtractor.advance()
    return DRAIN_STATE_CONSUMED
  }

  private fun drainDecoder(timeoutUs: Long, mBufferInfo: MediaCodec.BufferInfo): Int {
    if (mIsDecoderEOS) return DRAIN_STATE_NONE

    val result = mDecoder.dequeueOutputBuffer(mBufferInfo, timeoutUs)
    when (result) {
      MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
        mDecoder.outputFormat.let { it1 -> audioChannel?.setActualDecodedFormat(it1) }
        return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
      }
      MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
    }

    when {
      mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 -> {
        mIsDecoderEOS = true
        audioChannel?.drainDecoderBufferAndQueue(AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
      }
      mBufferInfo.size > 0 -> audioChannel?.drainDecoderBufferAndQueue(result,
          mBufferInfo.presentationTimeUs)
    }

    return DRAIN_STATE_CONSUMED
  }

  private fun drainEncoder(timeoutUs: Long, mBufferInfo: MediaCodec.BufferInfo): Int {
    if (mIsEncoderEOS) return DRAIN_STATE_NONE

    val result = encoder.dequeueOutputBuffer(mBufferInfo, timeoutUs)
    when (result) {
      MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
        if (mActualOutputFormat != null) {
          throw RuntimeException("Audio output format changed twice.")
        }
        mActualOutputFormat = encoder.outputFormat
        mActualOutputFormat?.let { queuedMuxer.setOutputFormat(SampleInfo.SampleType.AUDIO, it) }
        return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
      }
      MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
        encoderBuffers = encoder.let { MediaCodecBufferCompatWrapper(it) }
        return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
      }
    }

    if (mActualOutputFormat == null) {
      throw RuntimeException("Could not determine actual output format.")
    }

    if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
      mIsEncoderEOS = true
      mBufferInfo.set(0, 0, 0, mBufferInfo.flags)
    }
    if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
      // SPS or PPS, which should be passed by MediaFormat.
      result.let { encoder.releaseOutputBuffer(it, false) }
      return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
    }
    result.let {
      encoderBuffers?.getOutputBuffer(it)?.let {
        queuedMuxer.writeSampleData(SampleInfo.SampleType.AUDIO, it,
            mBufferInfo)
      }
    }
    mWrittenPresentationTimeUs = mBufferInfo.presentationTimeUs
    result.let { encoder.releaseOutputBuffer(it, false) }
    return DRAIN_STATE_CONSUMED
  }

  override fun getWrittenPresentationTimeUS() = mWrittenPresentationTimeUs

  override fun isFinished() = mIsEncoderEOS

  override fun release() {
    if (decoderStarted) {
      mDecoder.release()
    }
    if (encoderStarted) {
      encoder.release()
    }
  }

}