package com.googlyandroid.videocompressorsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import com.googlyandroid.ktvideocompressor.KTMediaTranscoder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import navigation


class MainActivity : AppCompatActivity() {

  private val REQUEST_READ_STORAGE: Int = 1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
          REQUEST_READ_STORAGE)
    } else {
      pickVideo()

    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
      grantResults: IntArray) {
    if (requestCode == REQUEST_READ_STORAGE) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        pickVideo()
      } else {
        // Permission denied
        Toast.makeText(this, "Transcoder is useless without access to external storage :/",
            Toast.LENGTH_SHORT).show();
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private fun pickVideo() {
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
          transcodeVideo(videoPath)
          DatabaseUtils.dumpCursor(cursor)
          cursor.close()
        }
      }
    }

  }

  private fun transcodeVideo(videoPath: String?) {
    CoroutineScope(Dispatchers.Main).launch {
      videoPath?.let { KTMediaTranscoder.transcodeVideo(it, createTempFile().path) }
    }
  }
}
