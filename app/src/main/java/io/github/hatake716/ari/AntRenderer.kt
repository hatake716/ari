package io.github.hatake716.ari

import android.graphics.*
import kotlin.math.*

/** Authored Camponotus-inspired dorsal models. No specimen photographs are bundled. */
class AntRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sprites = mutableMapOf<Int, Bitmap>()
    private val sensorySprites = mutableMapOf<Int, Bitmap>()
    private val destination = RectF()

    fun draw(c: Canvas, x: Float, y: Float, scale: Float, heading: Float, phase: Double,
             queen: Boolean = false, food: Boolean = false, wings: Boolean = false,
             antennaTime: Double = phase) {
        val frame = ((phase / (2 * PI) * 24).toInt() % 24 + 24) % 24
        val key = frame + (if(queen) 32 else 0) + (if(wings) 64 else 0)
        val sprite = sprites.getOrPut(key) {
            Bitmap.createBitmap(320, 256, Bitmap.Config.ARGB_8888).also {
                val target = Canvas(it)
                target.translate(160f, 128f); target.scale(48f, 48f)
                model(target, frame * 2 * PI / 24, queen, wings)
            }
        }
        c.save();c.translate(x,y);c.rotate(heading)
        p.shader=null;p.color=Color.WHITE;p.style=Paint.Style.FILL
        destination.set(-scale*160/48,-scale*128/48,scale*160/48,scale*128/48)
        c.drawBitmap(sprite,null,destination,p)
        val sensoryFrame=((antennaTime*2.3/(2*PI)*16).toInt()%16+16)%16
        val sensoryKey=sensoryFrame+(if(food)16 else 0)
        val sensory=sensorySprites.getOrPut(sensoryKey) {
            Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also {
                val target=Canvas(it);target.translate(-48f,48f);target.scale(48f,48f)
                sensory(target,sensoryFrame*2*PI/16/2.3,food)
            }
        }
        p.shader=null;p.color=Color.WHITE;p.style=Paint.Style.FILL
        destination.set(scale,-scale,scale*3,scale)
        c.drawBitmap(sensory,null,destination,p)
        c.restore()
    }

    private fun sensory(c:Canvas,antennaTime:Double,food:Boolean) {
        // Sensory sweeping continues while the legs are at rest, but freezes on pause.
        for (side in intArrayOf(-1,1)) {
            val sweep=sin(antennaTime*2.3+side*.8).toFloat()*.11f
            val ex=1.78f+sweep;val ey=side*(.66f+sweep)
            stroke(c,1.22f,side*.20f,ex,ey,.048f,0xff544232.toInt())
            stroke(c,1.22f,side*.19f,ex,ey-.014f,.015f,0xffa18b65.toInt())
            val tipX=2.46f+sweep;val tipY=side*(.42f-sweep)
            stroke(c,ex,ey,tipX,tipY,.043f,0xff7c6649.toInt())
            for(i in 1..8) {
                val f=i/9f;val xx=ex+(tipX-ex)*f;val yy=ey+(tipY-ey)*f
                stroke(c,xx,yy-.022f,xx,yy+.022f,.014f,0xff211a15.toInt())
            }
        }
        if(food) {
            // An amber piece of prey held by the mandibles, not a leaf-cutter caste.
            shell(c,ovalPath(1.48f,-.20f,2.12f,.23f),1.66f,-.08f,.53f,
                intArrayOf(0xffd5b56e.toInt(),0xff9b6933.toInt(),0xff3c2516.toInt()))
            for(i in 0..2)stroke(c,1.68f+i*.13f,-.16f,1.61f+i*.13f,.16f,.022f,0xff64401f.toInt())
        }
    }

    private fun model(c: Canvas, phase: Double, queen: Boolean, wings: Boolean) {
        // Six legs attach only to the mesosoma. Coxa, femur, tibia and segmented tarsus.
        for(side in intArrayOf(-1,1)) for(leg in 0..2) {
            val hipX=.45f-leg*.31f
            val q=AntGait.legPhase(phase,side,leg)
            val sweep=cos(q).toFloat()*.25f
            val lift=max(0.0,sin(q)).toFloat()*.10f
            val kneeX=floatArrayOf(.87f,-.08f,-.82f)[leg]+sweep*.4f
            val kneeY=side*(.78f-lift)
            val footX=floatArrayOf(1.32f,-.23f,-1.32f)[leg]+sweep
            val footY=side*(1.27f-lift)
            stroke(c,hipX,side*.20f,hipX+.08f,kneeY*.44f,.12f,0xff2c211a.toInt())
            stroke(c,hipX+.08f,kneeY*.44f,kneeX,kneeY,.105f,0xff33271d.toInt())
            stroke(c,hipX+.07f,kneeY*.43f,kneeX,kneeY-.018f,.029f,0xff957b55.toInt())
            stroke(c,kneeX,kneeY,footX,footY,.066f,0xff57422b.toInt())
            stroke(c,kneeX,kneeY-.02f,footX,footY-.015f,.018f,0xffb69a69.toInt())
            val toeX=footX+.17f;val toeY=footY+side*.16f
            stroke(c,footX,footY,toeX,toeY,.04f,0xff766044.toInt())
            repeat(4) { i ->
                val f=i/4f;val tx=footX+(toeX-footX)*f;val ty=footY+(toeY-footY)*f
                stroke(c,tx-.022f,ty,tx+.025f,ty,.014f,0xff282018.toInt())
            }
            stroke(c,toeX,toeY,toeX+.07f,toeY-side*.025f,.018f,0xffb49d74.toInt())
        }
        val gasterLeft=if(queen)-2.18f else -1.94f
        val gasterHeight=if(queen).69f else .53f
        val gaster=Path().apply {
            moveTo(-.76f,0f)
            cubicTo(-.85f,-gasterHeight,gasterLeft+.22f,-gasterHeight*1.18f,gasterLeft,-.10f)
            cubicTo(gasterLeft-.07f,gasterHeight*.84f,-1.08f,gasterHeight*1.20f,-.80f,.21f)
            cubicTo(-.75f,.15f,-.72f,.07f,-.76f,0f);close()
        }
        shell(c,gaster,-1.54f,-gasterHeight*.45f,1.30f,
            intArrayOf(0xff878175.toInt(),0xff302e2a.toInt(),0xff100e0c.toInt()))
        c.save();c.clipPath(gaster)
        // Curved overlapping gastral tergites; subdued warm membranes between plates.
        for(i in 0..3) {
            val xx=gasterLeft+.27f+i*.285f
            val seam=Path().apply {moveTo(xx,-gasterHeight);cubicTo(xx-.24f,-.20f,xx-.22f,.25f,xx+.02f,gasterHeight)}
            pathStroke(c,seam,.045f,0xff0a0908.toInt())
            c.save();c.translate(.04f,0f);pathStroke(c,seam,.018f,0xff9b8460.toInt());c.restore()
        }
        // Fine setae follow the surface, kept subtle enough to read as a cuticle.
        repeat(48) { i ->
            val xx=gasterLeft+.10f+(i%12)*.10f;val yy=-gasterHeight*.68f+(i/12)*gasterHeight*.43f
            stroke(c,xx,yy,xx-.035f,yy+.024f,.007f,0x807d7564.toInt())
        }
        c.restore()
        surfaceGrain(c,gaster,gasterLeft,-gasterHeight,1.5f,gasterHeight*2,130)
        // Single upright petiolar node with a narrow articulation on each side.
        stroke(c,-.90f,0f,-.31f,0f,.12f,0xff241a12.toInt())
        shell(c,ovalPath(-.69f,-.25f,-.43f,.25f),-.59f,-.12f,.34f)
        val thorax=Path().apply {
            moveTo(-.40f,-.13f)
            cubicTo(-.23f,-.23f,-.15f,if(queen)-.47f else -.31f,.22f,if(queen)-.45f else -.29f)
            cubicTo(.64f,-.39f,.75f,-.21f,.66f,.08f)
            cubicTo(.68f,.42f,.21f,if(queen).48f else .32f,-.02f,.25f)
            cubicTo(-.20f,.19f,-.30f,.20f,-.40f,.13f);close()
        }
        shell(c,thorax,.15f,-.22f,.84f)
        surfaceGrain(c,thorax,-.4f,-.4f,1.1f,.8f,70)
        val pronotum=Path().apply {moveTo(.40f,-.34f);cubicTo(.30f,-.11f,.30f,.15f,.42f,.32f)}
        pathStroke(c,pronotum,.025f,0xff090808.toInt())
        val suture=Path().apply {moveTo(-.05f,-.26f);quadTo(.04f,.02f,-.09f,.25f)}
        pathStroke(c,suture,.023f,0xff17120e.toInt())
        if(queen)for(side in intArrayOf(-1,1)) {
            // Enlarged flight-muscle thorax, wing bases remain visible after dealation.
            shell(c,ovalPath(-.11f,side*.31f-.05f,.12f,side*.31f+.06f),0f,side*.30f,.20f)
        }
        stroke(c,.57f,0f,.85f,0f,.19f,0xff211910.toInt())
        val head=Path().apply {
            moveTo(.77f,-.27f);cubicTo(.80f,-.47f,1.08f,-.47f,1.36f,-.29f)
            quadTo(1.45f,-.05f,1.36f,.28f);cubicTo(1.02f,.49f,.77f,.40f,.75f,.20f)
            quadTo(.67f,0f,.77f,-.27f);close()
        }
        shell(c,head,.93f,-.22f,.70f)
        surfaceGrain(c,head,.75f,-.4f,.65f,.8f,60)
        // Paired compound eyes rather than a single decorative highlight.
        for(side in intArrayOf(-1,1)) {
            shell(c,ovalPath(.85f,side*.32f-.09f,1.03f,side*.32f+.09f),.89f,side*.32f-.03f,.17f,
                intArrayOf(0xffa29478.toInt(),0xff332d24.toInt(),0xff050504.toInt()))
            repeat(3) {i->stroke(c,.88f+i*.04f,side*.32f-.05f,.89f+i*.04f,side*.32f+.05f,.01f,0xff15110c.toInt())}
            val jaw=Path().apply {
                moveTo(1.27f,side*.23f);quadTo(1.62f,side*.25f,1.64f,side*.035f)
                lineTo(1.51f,side*.085f);lineTo(1.47f,side*.055f);lineTo(1.42f,side*.11f)
                lineTo(1.35f,side*.07f);close()
            }
            shell(c,jaw,1.40f,side*.17f,.35f,intArrayOf(0xffa78a56.toInt(),0xff665030.toInt(),0xff24190f.toInt()))
        }
        if(queen) repeat(3) {i->
            p.shader=null;p.color=0xffa59777.toInt();c.drawCircle(.93f+(i%2)*.13f,(i-1)*.075f,.023f,p)
        }
        if(wings) {
            // Two pairs: smaller hindwings and longer forewings with visible venation.
            for(side in intArrayOf(-1,1)) for(pair in 0..1) {
                val reach=if(pair==0)1.70f else 2.38f
                val spread=if(pair==0).93f else 1.47f
                val wing=Path().apply {
                    moveTo(.16f,side*.24f)
                    cubicTo(-.18f,side*.72f,-reach,side*(spread+.37f),-reach,side*spread)
                    cubicTo(-reach,side*.65f,-.36f,side*.21f,.16f,side*.24f);close()
                }
                p.shader=LinearGradient(0f,0f,-reach,side*spread,intArrayOf(0x42d8d5c2,0x998e9994.toInt(),0x67e4e9df),null,Shader.TileMode.CLAMP)
                p.style=Paint.Style.FILL;c.drawPath(wing,p);p.shader=null
                pathStroke(c,wing,.013f,0x997a7764.toInt())
                for(v in 0..2) {
                    val vein=Path().apply {moveTo(.12f,side*.25f);quadTo(-reach*.43f,side*(.45f+v*.14f),-reach*.91f,side*(spread-.16f+v*.075f))}
                    pathStroke(c,vein,.012f,0x95726e55.toInt())
                }
                stroke(c,-reach*.45f,side*.58f,-reach*.63f,side*(spread-.07f),.011f,0x95726e55.toInt())
            }
        }
    }
    private fun surfaceGrain(c:Canvas,shape:Path,left:Float,top:Float,width:Float,height:Float,count:Int) {
        c.save();c.clipPath(shape)
        for(i in 0 until count) {
            val xx=left+((i*.6180339887)%1).toFloat()*width
            val yy=top+((i*.4142135623)%1).toFloat()*height
            p.shader=null;p.style=Paint.Style.FILL;p.color=if(i%3==0)0x55726959 else 0x48100c08
            c.drawCircle(xx,yy,if(i%3==0).009f else .007f,p)
            if(i%5==0)stroke(c,xx,yy,xx-.041f,yy+.016f,.006f,0x9b9e9075.toInt())
        }
        c.restore()
    }
    private fun ovalPath(l:Float,t:Float,r:Float,b:Float)=Path().apply {addOval(l,t,r,b,Path.Direction.CW)}
    private fun shell(c:Canvas,path:Path,x:Float,y:Float,r:Float,colors:IntArray=intArrayOf(0xff787065.toInt(),0xff342e27.toInt(),0xff100d0a.toInt())) {
        p.style=Paint.Style.FILL;p.shader=RadialGradient(x,y,r,colors,floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP)
        c.drawPath(path,p);p.shader=null;pathStroke(c,path,.014f,0xff120f0c.toInt())
    }
    private fun pathStroke(c:Canvas,path:Path,width:Float,color:Int) {
        p.shader=null;p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeJoin=Paint.Join.ROUND
        c.drawPath(path,p);p.style=Paint.Style.FILL
    }
    private fun stroke(c:Canvas,x:Float,y:Float,xx:Float,yy:Float,width:Float,color:Int) {
        p.shader=null;p.color=color;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;p.style=Paint.Style.STROKE
        c.drawLine(x,y,xx,yy,p);p.style=Paint.Style.FILL
    }
}
