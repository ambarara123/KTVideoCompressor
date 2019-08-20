package com.googlyandroid.ktvideocompressor

import com.googlyandroid.ktvideocompressor.mediaStrategy.MediaFormatStrategy
import com.googlyandroid.ktvideocompressor.mediaStrategy.NoOpMediaFormatStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.FileInputStream
import kotlin.coroutines.CoroutineContext

object KTMediaTranscoder {
  suspend fun transcodeVideo(inPath: String, outPath: String,
      outFormatStrategy: MediaFormatStrategy? = NoOpMediaFormatStrategy()): Boolean {
    return withContext(Dispatchers.IO) {
      val fileInputStream = FileInputStream(inPath)
      val inFileDescriptor = fileInputStream.fd
      outFormatStrategy?.let {
        transcodeVideoInternal(inFileDescriptor, outPath, it, coroutineContext)
      }
      true
    }
  }

  private suspend fun transcodeVideoInternal(inFileDescriptor: FileDescriptor,
      outPath: String,
      outFormatStrategy: MediaFormatStrategy,
      coroutineContext: CoroutineContext) {
    withContext(coroutineContext) {
      val engine = MediaTranscoderEngine(mediaFileDescriptor = inFileDescriptor, outPath = outPath)
      engine.transcodeVideo(outFormatStrategy = outFormatStrategy,
          coroutineContext = coroutineContext)
    }
  }
}