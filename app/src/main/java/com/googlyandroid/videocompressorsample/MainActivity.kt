package com.googlyandroid.videocompressorsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.googlyandroid.ktvideocompressor.KTMediaTranscoder
import com.googlyandroid.ktvideocompressor.mediaStrategy.NoOpMediaFormatStrategy
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.io.File


class MainActivity : AppCompatActivity() {

  private var job: Job? = null
  private var transcodingJob: CoroutineScope? = null

  private val REQUEST_READ_STORAGE: Int = 1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    pickVideo.setOnClickListener {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_READ_STORAGE)
      } else {
        pickVideoInternal()
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
      grantResults: IntArray) {
    if (requestCode == REQUEST_READ_STORAGE) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        pickVideoInternal()
      } else {
        // Permission denied
        Toast.makeText(this, "Transcoder is useless without access to external storage :/",
            Toast.LENGTH_SHORT).show();
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private fun pickVideoInternal() {
    val intent = Intent(Intent.ACTION_PICK)
        .setData(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setType("video/*")
    val title = getString(R.string.app_name)
    startActivityForResult(Intent.createChooser(intent, title), REQUEST_READ_STORAGE)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    data?.data?.let {
      val cursor = contentResolver.query(it, null, null,
          null, null)
      cursor?.let {
        if (it.count > 0) {
          cursor.moveToFirst()
          val columnIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
          val videoPath = cursor.getString(columnIndex)
          displayVideoThumb(videoPath)

          initTranscodingInfo(videoPath)

          DatabaseUtils.dumpCursor(cursor)
          cursor.close()
        }
      }
    }

  }

  private fun initTranscodingInfo(videoPath: String?) {

    transcodingJob?.let {
      it.cancel()
    }
    job = Job()
    transcodingJob = CoroutineScope(Dispatchers.Main + job!!)

    transcodingJob!!.launch {
      val (videoFormat, audioFormat) = KTMediaTranscoder.videoInfoExtract(videoPath)

      videoFormat?.let {
        val width = it.getInteger(MediaFormat.KEY_WIDTH)
        val height = it.getInteger(MediaFormat.KEY_HEIGHT)

        edtHeight.setText(height.toString())
        edtWidth.setText(width.toString())

      }

      val bitRate = audioFormat?.getInteger(MediaFormat.KEY_BIT_RATE)
      bitRate?.let {
        edtBitRate.setText(bitRate.toString())
      }
    }

    transcodeNow.setOnClickListener {
      transcodeVideo(videoPath, edtWidth.text.toString(), edtHeight.text.toString(),
          edtBitRate.text.toString())
    }
  }

  private fun displayVideoThumb(videoPath: String) {
    val videoPath = File(videoPath)
    mediathumb.setVideoURI(Uri.fromFile(videoPath))
    mediathumb.start()
    mediathumb.setOnCompletionListener {
      it.seekTo(0)
      it.start()
    }
  }

  private fun transcodeVideo(videoPath: String?, width: String,
      height: String, bitrate: String) {
    transcodingJob?.launch {

      btnStop.setOnClickListener {
        transcodingJob?.cancel()
      }

      videoPath?.let {
        val formatStrategy = NoOpMediaFormatStrategy(width, height, bitrate)
        val tempFile = File(externalCacheDir, "${System.currentTimeMillis()}.mp4")
        KTMediaTranscoder.transcodeVideo(it, tempFile.path, formatStrategy)
        onTranscodingDone(tempFile)
        progress.progress = 0
      }
    }
    transcodingJob?.launch {
      KTMediaTranscoder.progressChannel.consumeEach {
        when {
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> progress.setProgress(
              it.times(100).toInt(), true)
          else -> progress.progress = it.times(100).toInt()
        }
      }
    }
  }

  private fun onTranscodingDone(tempFile: File) {

    val uri = FileProvider.getUriForFile(
        this@MainActivity,
        "com.googlyandroid.videocompressorsample.fileprovider",
        tempFile)
    videoview.setVideoURI(uri)
    videoview.start()

    videoview.setOnCompletionListener {
      it.seekTo(0)
      it.start()
    }

    /*//grant permision for app with package "packegeName", eg. before starting other app via intent
   grantUriPermission(packageName, uri,
       Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

   val resInfoList = packageManager.queryIntentActivities(intent,
       PackageManager.MATCH_DEFAULT_ONLY)
   for (resolveInfo in resInfoList) {
     val packageName = resolveInfo.activityInfo.packageName
     grantUriPermission(packageName, uri,
         Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }

   val intent = Intent(Intent.ACTION_VIEW, uri)
   intent.setDataAndType(uri, "video/mp4")
   startActivity(intent)*/
  }

  override fun onDestroy() {
    super.onDestroy()
    transcodingJob?.cancel()
  }
}
