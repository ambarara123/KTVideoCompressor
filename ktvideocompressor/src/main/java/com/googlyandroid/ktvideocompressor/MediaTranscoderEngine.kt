package com.googlyandroid.ktvideocompressor

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import com.googlyandroid.ktvideocompressor.mediaStrategy.MediaFormatStrategy
import com.googlyandroid.ktvideocompressor.muxer.QueuedMuxer
import com.googlyandroid.ktvideocompressor.muxer.SampleInfo
import com.googlyandroid.ktvideocompressor.transcoders.AudioTrackTranscoder
import com.googlyandroid.ktvideocompressor.transcoders.PassThroughTrackTranscoder
import com.googlyandroid.ktvideocompressor.transcoders.TrackTranscoder
import com.googlyandroid.ktvideocompressor.transcoders.VideoTrackTranscoder
import com.googlyandroid.ktvideocompressor.utils.ISO6709LocationParser
import com.googlyandroid.ktvideocompressor.utils.MediaExtractorUtils
import java.io.FileDescriptor
import kotlin.math.min


class MediaTranscoderEngine(private val inFd: FileDescriptor,
    private val outPath: String) {

  private val PROGRESS_UNKNOWN = -1.0
  private val SLEEP_TO_WAIT_TRACK_TRANSCODERS: Long = 10
  private val PROGRESS_INTERVAL_STEPS: Long = 10

  fun transcodeVideo(outFormatStrategy: MediaFormatStrategy) {
    val mediaExtractor = MediaExtractor()
    mediaExtractor.setDataSource(inFd)

    val mediaMuxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val duration = setupMetadata(inFd, mediaMuxer)
    val (videoTrackTranscoder, audioTrackTranscoder) = setupTrackTranscoders(outFormatStrategy,
        mediaExtractor, mediaMuxer)
    runPipelines(duration, videoTrackTranscoder, audioTrackTranscoder)
    mediaMuxer.stop()

    videoTrackTranscoder.release()
    audioTrackTranscoder.release()
    mediaExtractor.release()
    mediaMuxer.release()
  }

  private fun runPipelines(duration: Long,
      videoTrackTranscoder: TrackTranscoder,
      audioTrackTranscoder: TrackTranscoder) {
    var loopCount = 0
    when {
      duration <= 0 -> {
        fireProgress(PROGRESS_UNKNOWN)
      }
    }

    while (!videoTrackTranscoder.isFinished() && !audioTrackTranscoder.isFinished()) {
      val stepped = videoTrackTranscoder.stepPipeline() || audioTrackTranscoder.stepPipeline()
      loopCount++
      when {
        isStillProcessing(duration, loopCount) -> {
          val videoProgress = if (videoTrackTranscoder.isFinished()) 1.0 else min(1.0,
              videoTrackTranscoder.getWrittenPresentationTimeUS().toDouble() / duration)
          val audioProgress = if (audioTrackTranscoder.isFinished()) 1.0 else min(1.0,
              audioTrackTranscoder.getWrittenPresentationTimeUS().toDouble() / duration)
          val progress = (videoProgress + audioProgress) / 2.0
          fireProgress(progress)
        }
      }
      if (!stepped) {
        Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS)
      }
    }
  }

  private fun isStillProcessing(duration: Long, loopCount: Int) =
      duration > 0 && (loopCount % PROGRESS_INTERVAL_STEPS == 0L)

  private fun fireProgress(progress: Double) {
    Log.d(this.javaClass.name, "Trans progress $progress")
  }

  private fun setupTrackTranscoders(
      formatStrategy: MediaFormatStrategy,
      mediaExtractor: MediaExtractor,
      mediaMuxer: MediaMuxer): Pair<TrackTranscoder, TrackTranscoder> {
    val trackResult = MediaExtractorUtils.getFirstVideoAndAudioTrack(mediaExtractor)

    val videoOutputFormat = trackResult.mVideoTrackFormat?.let {
      formatStrategy.createVideoOutputFormat(it)
    }
    val audioOutputFormat = trackResult.mAudioTrackFormat?.let {
      formatStrategy.createAudioOutputFormat(it)
    }

    val queuedMuxer = QueuedMuxer(mediaMuxer)

    val videoTrackTranscoder = videoOutputFormat?.let {
      VideoTrackTranscoder(mediaExtractor,
          trackResult.mVideoTrackIndex,
          videoOutputFormat,
          queuedMuxer)
    } ?: run {
      PassThroughTrackTranscoder(mediaExtractor,
          trackResult.mVideoTrackIndex,
          queuedMuxer,
          SampleInfo.SampleType.VIDEO)
    }

    videoTrackTranscoder.setup()

    val audioTrackTranscoder = audioOutputFormat?.let {
      AudioTrackTranscoder(mediaExtractor,
          trackResult.mAudioTrackIndex,
          audioOutputFormat,
          queuedMuxer)
    } ?: run {
      PassThroughTrackTranscoder(mediaExtractor,
          trackResult.mAudioTrackIndex,
          queuedMuxer,
          SampleInfo.SampleType.AUDIO)
    }

    audioTrackTranscoder.setup()

    mediaExtractor.selectTrack(trackResult.mVideoTrackIndex)
    mediaExtractor.selectTrack(trackResult.mAudioTrackIndex)

    return Pair(videoTrackTranscoder, audioTrackTranscoder)
  }

  private fun setupMetadata(inFd: FileDescriptor, mediaMuxer: MediaMuxer): Long {
    val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(inFd)

    val rotationString = mediaMetadataRetriever.extractMetadata(
        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
    rotationString.toSafeInt()?.let { it1 -> mediaMuxer.setOrientationHint(it1) }

    val location = mediaMetadataRetriever.extractMetadata(
        MediaMetadataRetriever.METADATA_KEY_LOCATION)
    location?.let {
      val parsedLocation = ISO6709LocationParser().parse(it)
      parsedLocation?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          mediaMuxer.setLocation(parsedLocation[0], parsedLocation[1])
        }
      }
    }

    return try {
      mediaMetadataRetriever.extractMetadata(
          MediaMetadataRetriever.METADATA_KEY_DURATION).toLong().times(1000)
    } catch (e: NumberFormatException) {
      -1
    }
  }
}

private fun String.toSafeInt(): Int? {
  return try {
    this.toInt()
  } catch (ex: Exception) {
    null
  }
}
