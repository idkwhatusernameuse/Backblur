package dev.idkwuu.backblur

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.core.content.ContextCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private var outputWidth = 2048
    private var outputHeight = 2048
    private var originalImage: Bitmap? = null
    private var blurredImage: Bitmap? = null
    private var isOpeningNewActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionSetup()
        setButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (originalImage != null && !isOpeningNewActivity) {
            outState.putString("originalImage", ImageUtil.convertToBase64(originalImage!!))
            outState.putString("blurredImage", ImageUtil.convertToBase64(blurredImage!!))
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (!isOpeningNewActivity) {
            try {
                originalImage = ImageUtil.convertToBitmap(savedInstanceState.get("originalImage").toString())
                blurredImage = ImageUtil.convertToBitmap(savedInstanceState.get("blurredImage").toString())
                findViewById<ImageView>(R.id.image).setImageBitmap(blurredImage)
                findViewById<View>(R.id.bottom).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.image).visibility = View.VISIBLE
            } catch (e: Exception) {
                // idfc k thx
            }
        }
        isOpeningNewActivity = false
    }
    companion object {
        private const val IMAGE_PICK_CODE = 1000
    }

    private var blurRadius = 50
    private var imageSizes = arrayOf("Square (1:1)", "16:9", "9:16")
    private var selectedSize = 0

    private fun setButtons() {
        val topButtons = findViewById<View>(R.id.top)

        topButtons.findViewById<Button>(R.id.chooseImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        topButtons.findViewById<ImageButton>(R.id.licenses).setOnClickListener {
            isOpeningNewActivity = true
            startActivity(Intent(this, OssLicensesMenuActivity::class.java))
        }

        val bottomButtons = findViewById<View>(R.id.bottom)

        bottomButtons.findViewById<Button>(R.id.imageSize).setOnClickListener {
            openImageSizeDialog()
        }

        bottomButtons.findViewById<Button>(R.id.blurRadius).setOnClickListener {
            openBlurSlider()
        }

        bottomButtons.findViewById<Button>(R.id.save).setOnClickListener {
            permissionSetup()
            saveImage()
        }
    }

    @SuppressLint("SetTextI18n")
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
                if (progress == 0) {
                    progressText.setTextColor(ContextCompat.getColor(applicationContext, R.color.redText))
                    select.isEnabled = false
                } else {
                    progressText.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.white))
                    progressBlur = progress
                    select.isEnabled = true
                }
                progressText.text = "$progress%"
            }
            override fun onStartTrackingTouch(seek: SeekBar) { }
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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE){
            val inputStream = data?.data?.let { contentResolver.openInputStream(it) }
            originalImage = BitmapFactory.decodeStream(inputStream)
            previewImage()
            findViewById<ImageView>(R.id.image).visibility = View.VISIBLE
            findViewById<View>(R.id.bottom).visibility = View.VISIBLE
            inputStream!!.close()
        }
    }

    private fun previewImage() {
        blurredImage = Blur().blur(this@MainActivity, originalImage!!, blurRadius, outputWidth, outputHeight)
        findViewById<ImageView>(R.id.image).setImageBitmap(blurredImage)
    }

    @Suppress("DEPRECATION")
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
                        blurredImage!!.compress(Bitmap.CompressFormat.PNG, 100, out)
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

                if (!blurredImage!!.compress(Bitmap.CompressFormat.PNG, 100, stream))
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
        // Only ask for permission on versions between Android 6.0 and 9.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            && checkSelfPermissionCompat(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        if (shouldShowRequestPermissionRationaleCompat(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
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