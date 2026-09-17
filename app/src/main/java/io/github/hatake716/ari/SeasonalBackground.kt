package io.github.hatake716.ari

import android.content.Context
import android.graphics.*

/** Two full-resolution months at most; never decode the twelve images on every frame. */
class SeasonalBackground(private val context: Context) {
    private val cache=object: LinkedHashMap<Int,Bitmap>(3,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int,Bitmap>?) = size>2
    }
    private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds=RectF()
    var currentMonth: Int = 0
        private set
    val cachedMonths: Int get() = cache.size

    fun draw(canvas: Canvas,date: SeasonDate,width: Float,height: Float) {
        val bitmap=cache.getOrPut(date.month) {
            context.assets.open(date.asset).use { requireNotNull(BitmapFactory.decodeStream(it)) }
        }
        currentMonth=date.month
        bounds.set(0f,0f,width,height)
        canvas.drawBitmap(bitmap,null,bounds,paint)
    }
}
