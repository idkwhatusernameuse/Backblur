package dev.idkwuu.backblur

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private var outputWidth = 2048
    private var outputHeight = 2048

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionSetup()
        setButtons()
    }

    companion object {
        private const val IMAGE_PICK_CODE = 1000
    }

    private var blurRadius = 50
    private var imageSizes = arrayOf("Square (1:1)", "16:9", "9:16")
    private var selectedSize = 0

    private fun setButtons() {
        val chooseImage = findViewById<Button>(R.id.chooseImage)
        chooseImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        val imageSize = findViewById<Button>(R.id.imageSize)
        imageSize.setOnClickListener {
            openImageSizeDialog()
        }

        val blurRadius = findViewById<Button>(R.id.blurRadius)
        blurRadius.setOnClickListener {
            openBlurSlider()
        }

        val save = findViewById<Button>(R.id.save)
        save.setOnClickListener {
            permissionSetup()
            saveImage()
        }

        val licenses = findViewById<Button>(R.id.licenses)
        licenses.setOnClickListener {
            startActivity(Intent(this, OssLicensesMenuActivity::class.java))
        }
    }

    private fun openBlurSlider() {
        val dialog = Dialog(this)
        dialog.setTitle(R.string.blur_radius)
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layout = inflater.inflate(R.layout.layout_slider, findViewById<LinearLayout>(R.id.sliderConstraintLayout))
        dialog.setContentView(layout)

        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        dialog.window?.attributes = layoutParams

        val seekBar = layout.findViewById<SeekBar>(R.id.seekBar)
        val progressText = layout.findViewById<TextView>(R.id.progress)
        val select = layout.findViewById<Button>(R.id.select)
        val cancel = layout.findViewById<Button>(R.id.cancel)

        var progressBlur = blurRadius

        progressText.text = "$blurRadius%"
        seekBar.progress = blurRadius

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                progressText.text = "$progress%"
                progressBlur = progress
            }
            override fun onStartTrackingTouch(seek: SeekBar) {}
            override fun onStopTrackingTouch(seek: SeekBar) {}
        })

        select.setOnClickListener {
            blurRadius = progressBlur
            previewImage()
            dialog.dismiss()
        }

        cancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun openImageSizeDialog() {
        permissionSetup()
        var selectedOption = selectedSize
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.image_size)
        dialogBuilder.setSingleChoiceItems(imageSizes, selectedOption) { _, selection->
            selectedOption = selection
        }
        dialogBuilder.setPositiveButton(R.string.select) { dialog, _ ->
            selectedSize = selectedOption
            when (selectedOption) {
                0 -> {
                    outputWidth = 2048
                    outputHeight = 2048
                }
                1 -> {
                    outputWidth = 2048
                    outputHeight = 1152
                }
                2 -> {
                    outputWidth = 1152
                    outputHeight = 2048
                }
            }
            previewImage()
            dialog.dismiss()
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { _, _ -> }
        dialogBuilder.create().show()
    }

    private lateinit var originalImage: Bitmap

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE){
            val inputStream = data?.data?.let { contentResolver.openInputStream(it) }
            originalImage = BitmapFactory.decodeStream(inputStream)
            previewImage()
            val imageEditor = findViewById<ConstraintLayout>(R.id.imageEditor)
            imageEditor.visibility = View.VISIBLE
            inputStream!!.close()
        }
    }

    private lateinit var exportBitmap: Bitmap

    private fun previewImage() {
        exportBitmap = Blur().blur(this@MainActivity, originalImage, blurRadius, outputWidth, outputHeight)
        val preview = findViewById<ImageView>(R.id.image)
        preview.setImageBitmap(exportBitmap)
    }

    @Throws(IOException::class)
    private fun saveImage(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Pre-Android 10
            val dir = File("${Environment.getExternalStorageDirectory()}/${Environment.DIRECTORY_PICTURES}/Backblur/")
            try {
                dir.mkdirs()
                val file = File(dir, "Backblur_${System.currentTimeMillis()/100}.png")
                val out = FileOutputStream(file)
                try {
                    try {
                        file.createNewFile()
                        exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } catch (e: Exception) {
                        openErrorSaving()
                    }
                } catch (e: Exception) {
                    openErrorSaving()
                }
            } catch (e: Exception) {
                openErrorSaving()
            }
        } else {
            // Post-Android 10
            val name = "Backblur_${System.currentTimeMillis()/100}"
            val relativeLocation = "${Environment.DIRECTORY_PICTURES}/Backblur"

            val contentValues  = ContentValues().apply {
                put(MediaStore.Images.ImageColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.ImageColumns.RELATIVE_PATH, relativeLocation)
                }
            }

            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            var stream: OutputStream? = null
            var uri: Uri? = null

            try {
                uri = contentResolver.insert(contentUri, contentValues)
                if (uri == null)
                {
                    throw IOException("Failed to create new MediaStore record.")
                }

                stream = contentResolver.openOutputStream(uri)

                if (stream == null)
                {
                    throw IOException("Failed to get output stream.")
                }

                if (!exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                {
                    throw IOException("Failed to save bitmap.")
                }

                val view = findViewById<ConstraintLayout>(R.id.mainConstraintLayout)
                Snackbar.make(view, R.string.saved_image, Snackbar.LENGTH_INDEFINITE).setAction(R.string.open) {
                    val intent = Intent()
                    intent.type = "image/*"
                    intent.action = Intent.ACTION_VIEW
                    intent.data = contentUri
                    startActivity(Intent.createChooser(intent, getString(R.string.select_gallery_app)))
                }.show()

            } catch(e: IOException) {
                if (uri != null)
                {
                    contentResolver.delete(uri, null, null)
                }
                openErrorSaving()
                throw IOException(e)
            }
            finally {
                stream?.close()
            }
        }
    }

    private fun openErrorSaving() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.error_title)
        dialogBuilder.setMessage(R.string.error_content)
        dialogBuilder.setPositiveButton(R.string.ok) { dialogInterface: DialogInterface, _ ->
            dialogInterface.dismiss()
        }
        dialogBuilder.show()
    }

    private fun permissionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermissionCompat(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        if (shouldShowRequestPermissionRationaleCompat(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissionsCompat(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        } else {
            requestPermissionsCompat(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == 1) {
            if (grantResults.size == 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        }
    }
}