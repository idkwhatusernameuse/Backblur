package dev.idkwuu.backblur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtil {
    @Throws(IllegalArgumentException::class)
    fun convertToBitmap(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(
            base64Str.substring(base64Str.indexOf(",") + 1),
            Base64.DEFAULT
        )
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    fun convertToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(
            outputStream.toByteArray(),
            Base64.DEFAULT
        )
    }
}