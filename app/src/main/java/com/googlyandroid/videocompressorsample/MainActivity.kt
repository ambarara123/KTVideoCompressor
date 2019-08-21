package com.googlyandroid.videocompressorsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.googlyandroid.ktvideocompressor.KTMediaTranscoder
import com.googlyandroid.ktvideocompressor.mediaStrategy.NoOpMediaFormatStrategy
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream


class MainActivity : AppCompatActivity() {

  private val job = Job()

  private val transcodingJob = CoroutineScope(Dispatchers.Main + job)

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
          transcodeVideo(videoPath)
          DatabaseUtils.dumpCursor(cursor)
          cursor.close()
        }
      }
    }

  }

  private fun displayVideoThumb(videoPath: String) {
    val videoPath = File(videoPath)

    val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(FileInputStream(videoPath).fd)


    //Create a new Media Player
    val mp = MediaPlayer.create(baseContext, Uri.fromFile(videoPath))

    val millis = mp.duration
    val rev = ArrayList<Bitmap>()

    var i = 1000000
    while (i < millis * 1000) {
      val bitmap = mediaMetadataRetriever.getFrameAtTime(i.toLong(),
          MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      rev.add(bitmap)
      i += 1000000
      mediathumb.setImageBitmap(bitmap)
      break
    }

    mp.release()
  }

  private fun transcodeVideo(videoPath: String?) {
    transcodingJob.launch {
      videoPath?.let {
        val formatStrategy = NoOpMediaFormatStrategy()
        KTMediaTranscoder.transcodeVideo(it, createTempFile().path, formatStrategy)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    transcodingJob.cancel()
  }
}
