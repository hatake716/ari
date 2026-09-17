package io.github.hatake716.ari

import android.graphics.*
import kotlin.math.*

/** Authored dorsal anatomy studies. 24 distance-driven poses; no per-frame path tessellation. */
class EnemyRenderer {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frames=object:LinkedHashMap<Pair<EnemyKind,Int>,Bitmap>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Pair<EnemyKind,Int>,Bitmap>?)=size>24
    }
    fun draw(c:Canvas,kind:EnemyKind,x:Float,y:Float,size:Float,heading:Float,phase:Double) {
        val pose=((phase/(2*PI)*24).toInt().mod(24))
        val bitmap=frames.getOrPut(kind to pose) {
            Bitmap.createBitmap(360,224,Bitmap.Config.ARGB_8888).also {
                val target=Canvas(it);target.translate(180f,112f);target.scale(36f,36f)
                anatomy(target,kind,pose*2*PI/24)
            }
        }
        c.save();c.translate(x,y);c.rotate(heading)
        paint.shader=null;paint.style=Paint.Style.FILL;paint.alpha=255
        c.drawBitmap(bitmap,null,RectF(-5*size,-112f/36*size,5*size,112f/36*size),paint)
        c.restore()
    }
    private fun anatomy(c:Canvas,kind:EnemyKind,phase:Double) {
        val beetle=kind==EnemyKind.BEETLE
        val earwig=kind==EnemyKind.EARWIG
        val amber=intArrayOf(0xffae8050.toInt(),0xff624327.toInt(),0xff20150f.toInt())
        val black=intArrayOf(0xff7d8880.toInt(),0xff303e37.toInt(),0xff090e0e.toInt())
        val shell=if(beetle)black else amber
        // All six legs arise on the three thoracic segments, with coxa, femur, tibia and tarsal claws.
        for(side in intArrayOf(-1,1))for(leg in 0..2) {
            val step=sin(AntGait.legPhase(phase,side,leg)).toFloat()
            val lift=max(0f,cos(AntGait.legPhase(phase,side,leg)).toFloat())
            val hipX=1.18f-leg*.61f
            val kneeX=hipX+(1-leg)*.48f+step*.13f
            val kneeY=side*(1.10f-lift*.09f)
            val footX=hipX+(1-leg)*.96f+step*.39f
            val footY=side*(2.02f-lift*.22f)
            stroke(c,hipX,side*.38f,kneeX,kneeY,.19f,0xff181811.toInt())
            stroke(c,hipX,side*.37f,kneeX-.02f,kneeY-.04f,.10f,if(beetle)0xff677262.toInt()else 0xff99744a.toInt())
            stroke(c,kneeX,kneeY,footX,footY,.095f,if(beetle)0xff4c5547.toInt()else 0xff80613e.toInt())
            for(t in 1..4) {
                val endX=footX+.075f*t;val endY=footY+side*.068f*t
                stroke(c,endX-.075f,endY-side*.068f,endX,endY,.062f-t*.008f,0xff7f775b.toInt())
            }
            stroke(c,footX+.28f,footY+side*.25f,footX+.40f,footY+side*.22f,.025f,0xffc5b286.toInt())
            for(spine in 0..3) {
                val t=(spine+1)/5f;val xx=kneeX+(footX-kneeX)*t;val yy=kneeY+(footY-kneeY)*t
                stroke(c,xx,yy,xx-.10f,yy+side*.12f,.016f,0xffb3a278.toInt())
            }
        }
        if(beetle) {
            // Convex paired elytra, median suture and longitudinal rows of punctures.
            val body=ellipse(-2.62f,-1.04f,.43f,1.04f)
            shade(c,body,-1.02f,-.60f,2.16f,black)
            c.save();c.clipPath(body)
            for(side in intArrayOf(-1,1))for(ridge in 1..6) {
                val yy=side*ridge*.145f
                val line=Path().apply {moveTo(.31f,yy*.70f);cubicTo(-.55f,yy*1.18f,-1.72f,yy*1.13f,-2.58f,yy*.38f)}
                outline(c,line,.026f,0xff101c1a.toInt())
                for(i in 0..17) {
                    val xx=.16f-i*.147f
                    paint.color=0x997c8870.toInt();c.drawCircle(xx,yy*(.75f+.25f*sin((xx+2.5f)*PI/2.9f)).toFloat(),.014f,paint)
                }
            }
            stroke(c,.30f,0f,-2.57f,0f,.036f,0xff09110f.toInt());c.restore()
        } else {
            // Rove beetle and earwig abdomen remain exposed beyond their abbreviated forewings.
            for(segment in 0..6) {
                val xx=-.25f-segment*.33f
                val half=if(earwig).61f-segment*.033f else .54f-segment*.035f
                val flex=sin(phase*.5).toFloat()*segment*segment*.0028f
                val plate=Path().apply {
                    moveTo(xx+.23f,-half+flex);quadTo(xx-.05f,-half-.10f+flex,xx-.32f,-half*.86f+flex)
                    lineTo(xx-.35f,half*.86f+flex);quadTo(xx-.04f,half+.10f+flex,xx+.23f,half+flex);close()
                }
                shade(c,plate,xx,-half*.6f+flex,.92f,if(earwig)amber else intArrayOf(0xff888376.toInt(),0xff42382d.toInt(),0xff141513.toInt()))
                for(side in intArrayOf(-1,1)) {
                    stroke(c,xx-.21f,side*half+flex,xx-.39f,side*(half+.15f)+flex,.014f,0xffbbac88.toInt())
                    paint.color=0xff26261e.toInt();c.drawCircle(xx-.1f,side*half*.73f+flex,.033f,paint)
                }
            }
            val wing=ellipse(-.90f,-.68f,.65f,.68f)
            shade(c,wing,-.1f,-.4f,1.1f,if(earwig)amber else intArrayOf(0xff987f5c.toInt(),0xff544330.toInt(),0xff24221b.toInt()))
            stroke(c,.53f,0f,-.86f,0f,.035f,0xff292018.toInt())
            for(i in 0..70) {
                val x=-.77f+((i*.618)%1).toFloat()*1.20f;val y=-.54f+((i*.414)%1).toFloat()*1.08f
                paint.color=0x79544e38;c.drawCircle(x,y,.014f,paint)
            }
            if(earwig)for(side in intArrayOf(-1,1)) {
                val spread=.05f*sin(phase).toFloat()
                val forceps=Path().apply {
                    moveTo(-2.31f,side*.32f);cubicTo(-2.97f,side*(.91f+spread),-3.84f,side*.94f,-4.02f,side*.08f)
                    cubicTo(-3.43f,side*.60f,-3.09f,side*.37f,-2.83f,side*.30f)
                    lineTo(-2.98f,side*.19f);lineTo(-2.72f,side*.22f);lineTo(-2.39f,side*.12f);close()
                }
                shade(c,forceps,-2.79f,side*.43f,1.24f,amber)
            }
        }
        val pronotum=Path().apply {
            moveTo(.18f,-.59f);quadTo(.77f,-.87f,1.36f,-.47f);quadTo(1.49f,0f,1.36f,.47f)
            quadTo(.77f,.87f,.18f,.59f);quadTo(.38f,0f,.18f,-.59f);close()
        }
        shade(c,pronotum,.80f,-.39f,1.1f,shell)
        outline(c,Path().apply {moveTo(.29f,-.52f);quadTo(.8f,-.7f,1.31f,-.40f)},.021f,0xffa19871.toInt())
        val head=Path().apply {
            moveTo(1.26f,-.28f);quadTo(1.43f,-.53f,1.77f,-.45f);lineTo(2.14f,-.30f)
            quadTo(2.21f,0f,2.14f,.30f);lineTo(1.77f,.45f);quadTo(1.43f,.53f,1.26f,.28f);close()
        }
        shade(c,head,1.58f,-.29f,.90f,if(earwig)amber else black)
        for(i in 0..50) {
            val xx=1.38f+((i*.618)%1).toFloat()*.64f;val yy=-.29f+((i*.414)%1).toFloat()*.58f
            paint.color=0x79686649;c.drawCircle(xx,yy,.012f,paint)
        }
        for(side in intArrayOf(-1,1))for(i in 0..9) {
            val xx=.3f+i*.09f;val yy=side*(.53f+sin(i*.3f)*.06f)
            stroke(c,xx,yy,xx-.10f,yy+side*.12f,.009f,0xffaa9874.toInt())
        }
        for(side in intArrayOf(-1,1)) {
            shade(c,ellipse(1.51f,side*.40f-.12f,1.82f,side*.40f+.12f),1.6f,side*.4f-.05f,.24f,black)
            // Curved biting mandibles; antennae are filiform, not the ants' elbowed antennae.
            val reach=if(earwig).39f else if(beetle).72f else .53f
            val jaw=Path().apply {
                moveTo(2.01f,side*.30f);quadTo(2.01f+reach*.86f,side*.36f,2.01f+reach,side*.02f)
                lineTo(2.01f+reach*.64f,side*.13f);lineTo(2.01f+reach*.5f,side*.08f);lineTo(2.16f,side*.16f);close()
            }
            shade(c,jaw,2.25f,side*.25f,.52f,amber)
            var ax=1.92f;var ay=side*.35f
            for(segment in 1..11) {
                val t=segment/11f
                val bx=1.92f+2.15f*t
                val by=side*(.35f+1.05f*t+.48f*t*t+sin(phase+side).toFloat()*.13f*t)
                stroke(c,ax,ay,bx,by,.080f-.035f*t,if(segment%2==0)0xffbc9c68.toInt()else 0xff5f5138.toInt())
                paint.color=0xff302a20.toInt();c.drawCircle(bx,by,.04f-.012f*t,paint)
                ax=bx;ay=by
            }
        }
    }
    private fun ellipse(l:Float,t:Float,r:Float,b:Float)=Path().apply {addOval(l,t,r,b,Path.Direction.CW)}
    private fun shade(c:Canvas,path:Path,x:Float,y:Float,r:Float,colors:IntArray) {
        paint.style=Paint.Style.FILL;paint.shader=RadialGradient(x,y,r,colors,floatArrayOf(0f,.4f,1f),Shader.TileMode.CLAMP)
        c.drawPath(path,paint);paint.shader=null;outline(c,path,.026f,0xff181610.toInt())
    }
    private fun outline(c:Canvas,path:Path,width:Float,color:Int) {
        paint.shader=null;paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=width;paint.strokeJoin=Paint.Join.ROUND
        c.drawPath(path,paint);paint.style=Paint.Style.FILL
    }
    private fun stroke(c:Canvas,x:Float,y:Float,xx:Float,yy:Float,width:Float,color:Int) {
        paint.shader=null;paint.color=color;paint.strokeWidth=width;paint.strokeCap=Paint.Cap.ROUND;paint.style=Paint.Style.STROKE
        c.drawLine(x,y,xx,yy,paint);paint.style=Paint.Style.FILL
    }
}
