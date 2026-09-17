package io.github.hatake716.ari

import kotlin.math.max

/** Fit both lateral branches and depth; close-up resolution does not shrink as a colony grows. */
class NestCamera {
    var zoom=1f
    var panX=0f
    var panY=0f
    var depth=1.0
    var left=0.0
    var right=1.0
    private val extent get()=max(depth,right-left)
    val scale get()=(zoom/extent).toFloat()
    val maxZoom get()=(extent*10).toFloat()
    fun fit(nest:Nest){depth=nest.depth;left=nest.left;right=nest.right}
    fun reset(){zoom=1f;panX=0f;panY=0f}
    fun clamp(width:Int,height:Int) {
        val mx=max(0f,(width*(right-left)*scale-width).toFloat()/2)
        val my=max(0f,(height*depth*scale-height).toFloat()/2)
        panX=panX.coerceIn(-mx,mx);panY=panY.coerceIn(-my,my)
    }
    fun fromScreen(x:Float,y:Float,width:Int,height:Int)=Point(
        ((x-width/2f-panX)/scale+width*(left+right)/2)/width,
        ((y-height/2f-panY)/scale+height*depth/2)/height)
    fun toScreen(p:Point,width:Int,height:Int)=Point(
        width/2.0+panX+(p.x*width-width*(left+right)/2)*scale,
        height/2.0+panY+(p.y*height-height*depth/2)*scale)
}
