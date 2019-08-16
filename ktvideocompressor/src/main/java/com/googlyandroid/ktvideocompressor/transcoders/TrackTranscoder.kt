package com.googlyandroid.ktvideocompressor.transcoders

import android.media.MediaFormat

interface TrackTranscoder {
  fun setup()
  fun getDeterminedFormat(): MediaFormat?
  fun stepPipeline():Boolean
  fun getWrittenPresentationTimeUS(): Long
  fun isFinished(): Boolean
  fun release()
}
