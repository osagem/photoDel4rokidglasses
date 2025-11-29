package com.osagem.photoview4rokidglasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewPropertyAnimator

fun View.fadeIn(duration: Long, onEnd: (() -> Unit)? = null): ViewPropertyAnimator {
    alpha = 0f
    visibility = View.VISIBLE
    return animate()
        .alpha(1f)
        .setDuration(duration)
        .withEndAction {
            alpha = 1f
            onEnd?.invoke()
        }
}

/**
 * 从 Emoji 字符串创建一个 Bitmap 图像。
 * 这个函数是一个 Context 的扩展，可以在任何有 Context 的地方调用。
 *
 * @param emojiString 要转换的 Emoji 字符串。
 * @param size 生成的 Bitmap 的正方形尺寸（像素）。
 * @param color Emoji 的颜色，默认为黑色。
 * @return 代表该 Emoji 的 Bitmap 对象。如果 emojiString 为空，则返回一个空的 Bitmap。
 */
fun Context.createBitmapFromEmoji(emojiString: String, size: Int, color: Int = Color.BLACK): Bitmap {
    if (emojiString.isBlank()) {
        // 如果输入为空，返回一个透明的空 Bitmap，避免后续操作出错
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // textSize 通常需要根据 Bitmap 尺寸进行调整以获得最佳视觉效果
        // 这里设置为 Bitmap 尺寸的 80%，可以根据需要调整
        textSize = size * 0.8f
        this.color = color // 使用传入的颜色
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT // 使用系统默认字体来渲染 Emoji
    }

    // 计算绘制文本的基线，使其在垂直方向上居中
    val x = canvas.width / 2f
    val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f

    canvas.drawText(emojiString, x, y, paint)
    return bitmap
}