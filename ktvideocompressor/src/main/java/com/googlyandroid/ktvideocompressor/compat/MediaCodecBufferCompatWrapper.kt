package com.googlyandroid.ktvideocompressor.compat

import android.media.MediaCodec
import android.os.Build
import java.nio.ByteBuffer

class MediaCodecBufferCompatWrapper(private val mediaCodec: MediaCodec) {
  private var mInputBuffers: Array<ByteBuffer>? = null
  private var mOutputBuffers: Array<ByteBuffer>? = null

  init {
    if (Build.VERSION.SDK_INT < 21) {
      mInputBuffers = mediaCodec.inputBuffers
      mOutputBuffers = mediaCodec.outputBuffers
    }
  }


  fun getInputBuffer(index: Int): ByteBuffer? {
    return when {
      Build.VERSION.SDK_INT >= 21 -> mediaCodec.getInputBuffer(index)
      else -> mInputBuffers?.get(index)
    }
  }

  fun getOutputBuffer(index: Int): ByteBuffer? {
    return when {
      Build.VERSION.SDK_INT >= 21 -> mediaCodec.getOutputBuffer(index)
      else -> mOutputBuffers?.get(index)
    }
  }
}
