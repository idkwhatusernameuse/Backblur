package idkwuu.backblur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import jp.wasabeef.blurry.internal.Blur
import jp.wasabeef.blurry.internal.BlurFactor
import kotlin.math.roundToInt

class Blur {
    fun blur(context: Context, image: Bitmap, blurRadius: Int, outputWidth: Int, outputHeight: Int): Bitmap {
        val blurredBitmap = Blur.of(context, image, BlurFactor().apply {
            width = image.width
            height = image.height
            radius = blurRadius
        })

        val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, image.config)

        val canvas = Canvas(outputBitmap)

        val blurredScaleRatio: Float
        val imageScaleRatio: Float
        val alignBlurX: Float
        val alignBlurY: Float
        val alignImageX: Float
        val alignImageY: Float

        if (image.width > image.height) {
            imageScaleRatio = outputWidth.toFloat() / image.width.toFloat()
            blurredScaleRatio = outputHeight.toFloat() / image.height.toFloat()
            alignImageX = 0f
            alignImageY = (outputHeight - (image.height * imageScaleRatio)) / 2f
            alignBlurX = (outputWidth - (image.width * blurredScaleRatio)) / 2f
            alignBlurY = 0f
        } else {
            imageScaleRatio = outputHeight.toFloat() / image.height.toFloat()
            blurredScaleRatio = outputWidth.toFloat() / image.width.toFloat()
            alignImageX = (outputWidth - (image.width * imageScaleRatio)) / 2f
            alignImageY = 0f
            alignBlurX = 0f
            alignBlurY = -(outputWidth / 2f)
        }

        // Image adjustments for blurred image
        val scaledBlurredImage = Bitmap.createScaledBitmap(
            blurredBitmap,
            (blurredBitmap.width * blurredScaleRatio).roundToInt(),
            (blurredBitmap.height * blurredScaleRatio).roundToInt(),
            false
        )
        canvas.drawBitmap(scaledBlurredImage, alignBlurX, alignBlurY, null)

        // Image adjustments for front image
        val scaledImage = Bitmap.createScaledBitmap(
            image,
            (image.width * imageScaleRatio).roundToInt(),
            (image.height * imageScaleRatio).roundToInt(),
            false
        )
        canvas.drawBitmap(scaledImage, alignImageX, alignImageY, null)

        return outputBitmap
    }
}