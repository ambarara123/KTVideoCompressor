package com.googlyandroid.ktvideocompressor.engine

import android.media.MediaCodec
import android.media.MediaFormat
import com.googlyandroid.ktvideocompressor.compat.MediaCodecBufferCompatWrapper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*


class AudioChannel(decoder: MediaCodec,
    private val encoder: MediaCodec,
    private val audioOutputFormat: MediaFormat) {

  private class AudioBuffer {
    internal var bufferIndex: Int = 0
    internal var presentationTimeUs: Long = 0
    internal var data: ShortBuffer? = null
  }

  val BUFFER_INDEX_END_OF_STREAM = -1

  private val BYTES_PER_SHORT = 2
  private val MICROSECS_PER_SEC: Long = 1000000

  private val mEmptyBuffers = ArrayDeque<AudioBuffer>()
  private val mFilledBuffers = ArrayDeque<AudioBuffer>()

  private var mInputSampleRate: Int = 0
  private var mInputChannelCount: Int = 0
  private var mOutputChannelCount: Int = 0

  private var mRemixer: AudioRemixer? = null

  private var mDecoderBuffers: MediaCodecBufferCompatWrapper? = null
  private var mEncoderBuffers: MediaCodecBufferCompatWrapper? = null

  private val mOverflowBuffer = AudioBuffer()

  private var mActualDecodedFormat: MediaFormat? = null

  init {
    mDecoderBuffers = MediaCodecBufferCompatWrapper(decoder)
    mEncoderBuffers = MediaCodecBufferCompatWrapper(encoder)
  }

  @Throws(UnsupportedOperationException::class)
  fun setActualDecodedFormat(decodedFormat: MediaFormat) {
    this.mActualDecodedFormat = decodedFormat

    // extract the sample rate
    mInputSampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

    when {
      mInputSampleRate != audioOutputFormat.getInteger(
          MediaFormat.KEY_SAMPLE_RATE) -> throw UnsupportedOperationException(
          "Audio sample rate conversion not supported yet.")
    }

    // check the channel count!
    mInputChannelCount = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    mOutputChannelCount = audioOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    when {
      mInputChannelCount != 1 && mInputChannelCount != 2 -> throw UnsupportedOperationException(
          "Input channel count ($mInputChannelCount) not supported.")
      mOutputChannelCount != 1 && mOutputChannelCount != 2 -> throw UnsupportedOperationException(
          "Output channel count ($mOutputChannelCount) not supported.")
    }

    mRemixer = when {
      mInputChannelCount > mOutputChannelCount -> DOWNMIX
      mInputChannelCount < mOutputChannelCount -> UPMIX
      else -> PASSTHROUGH
    }

    mOverflowBuffer.presentationTimeUs = 0
  }

  fun drainDecoderBufferAndQueue(bufferIndex: Int, presentationTimeUs: Long) {
    mActualDecodedFormat?.let {
      // get the buffer data at @bufferIndex and null if EOS
      val data = when (bufferIndex) {
        BUFFER_INDEX_END_OF_STREAM -> {
          null
        }
        else -> {
          mDecoderBuffers?.getOutputBuffer(bufferIndex)
        }
      }

      var buffer = mEmptyBuffers.poll()
      buffer.takeIf { it == null }.let {
        buffer = AudioBuffer()
      }

      buffer?.bufferIndex = bufferIndex
      buffer?.presentationTimeUs = presentationTimeUs
      buffer?.data = data?.asShortBuffer()


      mOverflowBuffer.data?.let {
        // already has data
      } ?: run {
        mOverflowBuffer.data = data?.capacity()?.let {
          ByteBuffer
              .allocateDirect(it)
              .order(ByteOrder.nativeOrder())
              .asShortBuffer()
        }
        mOverflowBuffer.data?.clear()?.flip()
      }

      mFilledBuffers.add(buffer)
    } ?: run {
      throw RuntimeException("Buffer received before format!")
    }
  }

  fun feedEncoder(timeoutUs: Long): Boolean {
    val hasOverflow = mOverflowBuffer.data != null && mOverflowBuffer.data?.hasRemaining() == true

    // Encoder is full - Bail out

    //Drain overflow first
    when {
      mFilledBuffers.isEmpty() && !hasOverflow -> // No audio data - Bail out
        return false
      else -> {
        val encoderInBuffIndex = encoder.dequeueInputBuffer(timeoutUs)
        if (encoderInBuffIndex < 0) {
          // Encoder is full - Bail out
          return false
        }

        //Drain overflow first
        val outBuffer = mEncoderBuffers?.getInputBuffer(encoderInBuffIndex)?.asShortBuffer()

        return when {
          hasOverflow -> {
            val presentationTimeUs = outBuffer?.let { drainOverflow(it) } ?: 0
            encoder.queueInputBuffer(encoderInBuffIndex, 0,
                outBuffer?.position() ?: 0.times(BYTES_PER_SHORT),
                presentationTimeUs, 0)
            true
          }
          else -> true
        }

      }
    }

  }

  private fun sampleCountToDurationUs(sampleCount: Int,
      sampleRate: Int,
      channelCount: Int): Long {
    return sampleCount / (sampleRate * MICROSECS_PER_SEC) / channelCount
  }

  private fun drainOverflow(outBuff: ShortBuffer): Long {
    val overflowBuff = mOverflowBuffer.data
    val overflowLimit = overflowBuff?.limit()
    val overflowSize = overflowBuff?.remaining() ?: 0

    val beginPresentationTimeUs = mOverflowBuffer.presentationTimeUs +
        sampleCountToDurationUs(overflowBuff?.position() ?: 0, mInputSampleRate,
            mOutputChannelCount)

    outBuff.clear();
    // Limit overflowBuff to outBuff's capacity
    overflowBuff?.limit(outBuff.capacity())
    // Load overflowBuff onto outBuff
    outBuff.put(overflowBuff)

    if (overflowSize >= outBuff.capacity()) {
      // Overflow fully consumed - Reset
      overflowBuff?.clear()?.limit(0)
    } else {
      // Only partially consumed - Keep position & restore previous limit
      overflowLimit?.let { overflowBuff.limit(it) }
    }

    return beginPresentationTimeUs

  }

  private fun remixAndMaybeFillOverflow(input: AudioBuffer,
      outBuff: ShortBuffer): Long {
    val inBuff = input.data
    val overflowBuff = mOverflowBuffer.data

    outBuff.clear()

    // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
    inBuff?.clear()

    if (inBuff?.remaining() ?: 0 > outBuff.remaining()) {
      // Overflow
      // Limit inBuff to outBuff's capacity
      inBuff?.limit(outBuff.capacity())
      inBuff?.let { mRemixer?.remix(it, outBuff) }

      // Reset limit to its own capacity & Keep position
      inBuff?.limit(inBuff.capacity())

      // Remix the rest onto overflowBuffer
      // NOTE: We should only reach this point when overflow buffer is empty
      val consumedDurationUs = sampleCountToDurationUs(inBuff?.position() ?: 0, mInputSampleRate,
          mInputChannelCount)
      inBuff?.let { overflowBuff?.let { it1 -> mRemixer?.remix(it, it1) } }

      // Seal off overflowBuff & mark limit
      overflowBuff?.flip()
      mOverflowBuffer.presentationTimeUs = input.presentationTimeUs + consumedDurationUs
    } else {
      // No overflow
      inBuff?.let { mRemixer?.remix(it, outBuff) }
    }

    return input.presentationTimeUs
  }
}
