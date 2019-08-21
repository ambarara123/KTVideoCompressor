package com.googlyandroid.ktvideocompressor

import android.media.MediaFormat
import com.googlyandroid.ktvideocompressor.mediaStrategy.MediaFormatStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.withContext
import java.io.FileInputStream

object KTMediaTranscoder {

  var currentTranscodingPath: String? = null
  var currentTranscodingOutPath: String? = null
  val progressChannel = ConflatedBroadcastChannel<Double>()

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
          coroutineContext = coroutineContext,progressChannel=progressChannel)
      true
    }
  }

  suspend fun videoInfoExtract(videoPath: String?):Pair<MediaFormat?,MediaFormat?> {
    return withContext(Dispatchers.IO) {
      val fileInputStream = FileInputStream(videoPath)
      val inFileDescriptor = fileInputStream.fd
      val engine = MediaTranscoderEngine(mediaFileDescriptor = inFileDescriptor)
      engine.extractInfo(coroutineContext)
    }
  }
}