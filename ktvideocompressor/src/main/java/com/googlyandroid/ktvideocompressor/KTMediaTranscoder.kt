package com.googlyandroid.ktvideocompressor

import com.googlyandroid.ktvideocompressor.mediaStrategy.MediaFormatStrategy
import com.googlyandroid.ktvideocompressor.mediaStrategy.NoOpMediaFormatStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import kotlin.coroutines.CoroutineContext

object KTMediaTranscoder {

  var currentTranscodingPath: String? = null
  var currentTranscodingOutPath: String? = null

  suspend fun transcodeVideo(inPath: String, outPath: String,
      outFormatStrategy: MediaFormatStrategy): Boolean {
    return withContext(Dispatchers.IO) {
      currentTranscodingPath = inPath
      currentTranscodingOutPath = outPath
      val fileInputStream = FileInputStream(inPath)
      val inFileDescriptor = fileInputStream.fd
      val engine = MediaTranscoderEngine(mediaFileDescriptor = inFileDescriptor,
          outPath = outPath)
      engine.transcodeVideo(outFormatStrategy = outFormatStrategy,
          coroutineContext = coroutineContext)
      true
    }
  }
}