package com.googlyandroid.ktvideocompressor.mediaStrategy

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_AUDIO_AAC
import android.util.Log


class NoOpMediaFormatStrategy : MediaFormatStrategy {

  val AUDIO_BITRATE_AS_IS = -1
  val AUDIO_CHANNELS_AS_IS = -1
  private val TAG = "720pFormatStrategy"
  private val LONGER_LENGTH = 1280
  private val SHORTER_LENGTH = 720
  private val DEFAULT_VIDEO_BITRATE = 8000 * 1000 // From Nexus 4 Camera in 720p
  private val mVideoBitrate: Int = DEFAULT_VIDEO_BITRATE
  private var mAudioBitrate: Int = 128 * 1000
  private var mAudioChannels: Int = 1

  override fun createVideoOutputFormat(inputFormat: MediaFormat): MediaFormat? {
    val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
    val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
    val longer: Int
    val shorter: Int
    val outWidth: Int
    val outHeight: Int
    if (width >= height) {
      longer = width
      shorter = height
      outWidth = LONGER_LENGTH
      outHeight = SHORTER_LENGTH
    } else {
      shorter = width
      longer = height
      outWidth = SHORTER_LENGTH
      outHeight = LONGER_LENGTH
    }
    if (longer * 9 != shorter * 16) {
      throw RuntimeException(
          "This video is not 16:9, and is not able to transcode. (" + width + "x" + height + ")")
    }
    if (shorter <= SHORTER_LENGTH) {
      Log.d(TAG,
          "This video is less or equal to 720p, pass-through. (" + width + "x" + height + ")")
      return null
    }
    val format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight)
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