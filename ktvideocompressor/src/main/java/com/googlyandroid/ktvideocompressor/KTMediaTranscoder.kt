package com.googlyandroid.ktvideocompressor

import com.googlyandroid.ktvideocompressor.mediaStrategy.MediaFormatStrategy
import com.googlyandroid.ktvideocompressor.mediaStrategy.NoOpMediaFormatStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import kotlin.coroutines.CoroutineContext

object KTMediaTranscoder {
  suspend fun transcodeVideo(inPath: String, outPath: String,
      outFormatStrategy: MediaFormatStrategy? = NoOpMediaFormatStrategy(),
      coroutineContext: CoroutineContext):Boolean {
    return withContext(Dispatchers.IO) {
      val fileInputStream = FileInputStream(inPath)
      val inFileDescriptor = fileInputStream.fd
      outFormatStrategy?.let {
        val engine = MediaTranscoderEngine(mediaFileDescriptor = inFileDescriptor,
            outPath = outPath)
        engine.transcodeVideo(outFormatStrategy = outFormatStrategy,
            coroutineContext = coroutineContext)
      }
      true
    }
  }
}