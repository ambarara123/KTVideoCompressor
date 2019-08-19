package com.googlyandroid.ktvideocompressor.compat

import android.media.MediaCodec
import android.os.Build
import java.nio.ByteBuffer

class MediaCodecBufferCompatWrapper(private val encoder: MediaCodec) {
  private var mInputBuffers: Array<ByteBuffer>? = null
  private var mOutputBuffers: Array<ByteBuffer>? = null

  init {
    if (Build.VERSION.SDK_INT < 21) {
      mInputBuffers = encoder.inputBuffers
      mOutputBuffers = encoder.outputBuffers
    }
  }


  fun getInputBuffer(index: Int): ByteBuffer? {
    return when {
      Build.VERSION.SDK_INT >= 21 -> encoder.getInputBuffer(index)
      else -> mInputBuffers?.get(index)
    }
  }

  fun getOutputBuffer(index: Int): ByteBuffer? {
    return when {
      Build.VERSION.SDK_INT >= 21 -> encoder.getOutputBuffer(index)
      else -> mOutputBuffers?.get(index)
    }
  }
}
