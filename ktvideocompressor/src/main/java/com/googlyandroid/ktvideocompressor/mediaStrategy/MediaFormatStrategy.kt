package com.googlyandroid.ktvideocompressor.mediaStrategy

import android.media.MediaFormat

interface MediaFormatStrategy {
  fun createVideoOutputFormat(inputFormat: MediaFormat):MediaFormat?
  fun createAudioOutputFormat(inputFormat: MediaFormat):MediaFormat?
}
