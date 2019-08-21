package com.googlyandroid.ktvideocompressor.mediaStrategy

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_AUDIO_AAC


class NoOpMediaFormatStrategy : MediaFormatStrategy {

  val AUDIO_BITRATE_AS_IS = -1
  val AUDIO_CHANNELS_AS_IS = -1
  private val DEFAULT_VIDEO_BITRATE = 8000 * 1000 // From Nexus 4 Camera in 720p
  private val mVideoBitrate: Int = DEFAULT_VIDEO_BITRATE
  private var mAudioBitrate: Int = 128 * 1000
  private var mAudioChannels: Int = 1

  override fun createVideoOutputFormat(inputFormat: MediaFormat): MediaFormat? {
    val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
    val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
    val format = MediaFormat.createVideoFormat("video/avc", width, height)
    // From Nexus 4 Camera in 720p
    format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate)
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3)
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    return format
  }

  override fun createAudioOutputFormat(inputFormat: MediaFormat): MediaFormat? {
    if (mAudioBitrate == AUDIO_BITRATE_AS_IS || mAudioChannels == AUDIO_CHANNELS_AS_IS) return null

    // Use original sample rate, as resampling is not supported yet.
    val format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC,
        inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), mAudioChannels)
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
    format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate)
    return format
  }

}