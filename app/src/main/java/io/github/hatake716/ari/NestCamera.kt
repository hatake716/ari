package io.github.hatake716.ari

import kotlin.math.max

/** Fit the complete, expanding cross section; all hit testing uses this same inverse. */
class NestCamera {
    var zoom=1f
    var panX=0f
    var panY=0f
    var depth=1.0
    val scale get()=(zoom/depth).toFloat()
    fun reset(){zoom=1f;panX=0f;panY=0f}
    fun clamp(width:Int,height:Int) {
        val mx=max(0f,(width*scale-width)/2)
        val my=max(0f,(height*depth*scale-height).toFloat()/2)
        panX=panX.coerceIn(-mx,mx);panY=panY.coerceIn(-my,my)
    }
    fun fromScreen(x:Float,y:Float,width:Int,height:Int)=Point(
        ((x-width/2f-panX)/scale+width/2f)/width.toDouble(),
        ((y-height/2f-panY)/scale+height*depth/2)/height)
    fun toScreen(p:Point,width:Int,height:Int)=Point(
        width/2.0+panX+(p.x*width-width/2.0)*scale,
        height/2.0+panY+(p.y*height-height*depth/2)*scale)
}
