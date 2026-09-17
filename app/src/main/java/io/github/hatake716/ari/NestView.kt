package io.github.hatake716.ari

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/** A real-time cross section: geometry comes from the nest graph, never from baked artwork. */
class NestView(context: Context) : View(context) {
    var colony: Colony? = null
    var draft: Nest? = null
    var editing = false
    var showLabels = true
    var tool = "queen"
    var onEdit: ((Point) -> Unit)? = null
    var onInspect: ((String,String) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmap = BitmapFactory.decodeStream(context.assets.open("woodland-soil.png"))
    private val rect = RectF()
    private var time = 0.0
    private var nestTexture: Bitmap? = null
    private var textureKey = ""
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX=0f
    private var lastY=0f
    private var downX=0f
    private var downY=0f
    private var moved=false
    private val agents = mutableListOf<VisualAnt>()
    private val antSprites = mutableMapOf<Int,Bitmap>()
    private val spritePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteRect=RectF()
    private val scaleDetector = ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!editing) {
                zoom=(zoom*detector.scaleFactor).coerceIn(1f,3.2f)
                clampPan(); moved=true; invalidate()
            }
            return true
        }
    })
    private data class VisualAnt(val id: Int,var room: Int,var path: List<Int> = emptyList(),var edge: Int=0,var t: Double=0.0,var carrying: Boolean=false)
    init { isClickable=true; contentDescription="アリの巣の断面図。観察中はピンチで拡大、ドラッグで移動できます。" }
    fun resetCamera() { zoom=1f;panX=0f;panY=0f;invalidate() }
    private fun clampPan() {
        val mx=width*(zoom-1)/2; val my=height*(zoom-1)/2
        panX=panX.coerceIn(-mx,mx); panY=panY.coerceIn(-my,my)
    }
    fun animate(seconds: Double, playing: Boolean) {
        if (playing) time += seconds
        val c=colony
        if (c != null && playing && !c.terminal) {
            val outside=min(14,c.foragers)
            val count=min(c.workers-outside,110-outside)
            while (agents.size > count) agents.removeAt(agents.lastIndex)
            while (agents.size < count) {
                val rooms=c.nest.chambers.filter {it.id!=0 && it.built>=.95}
                agents += VisualAnt(agents.size,rooms[agents.size%rooms.size].id)
            }
            agents.forEach { a ->
                if (a.path.isEmpty() || a.edge >= a.path.lastIndex) {
                    a.room=a.path.lastOrNull() ?: a.room
                    if (c.nest.chambers.none { it.id == a.room }) a.room=c.nest.queenRoom
                    val destination=when {
                        c.invader != null && a.id % 10 < 7 -> c.invader!!.route[(c.invader!!.segment+1).coerceAtMost(c.invader!!.route.lastIndex)]
                        a.id % 10 < 3 -> if (a.room == 0) { a.carrying=true;c.nest.queenRoom } else { a.carrying=false;0 }
                        a.id % 10 < 5 -> if (a.room == c.nest.queenRoom) c.nest.chambers.last().id else c.nest.queenRoom
                        else -> c.nest.chambers.filter { it.built >= .95 && it.id != 0 }[(a.id+(time/5).toInt()) % c.nest.chambers.count { it.built >= .95 && it.id != 0 }].id
                    }
                    a.path=c.nest.path(a.room,destination);a.edge=0;a.t=0.0
                }
                if (a.path.size >= 2 && a.edge < a.path.lastIndex) {
                    val from=a.path[a.edge];val to=a.path[a.edge+1]
                    val length=c.nest.room(from).point.distance(c.nest.room(to).point)
                    // Legible representative motion; biological age still follows the exact selected time multiplier.
                    val pace=.085*c.activity*(1+ln(c.speed.coerceAtLeast(1).toDouble())*.13)
                    a.t+=seconds*pace/length*c.nest.localSpeed(from,to,a.t,false)
                    if (a.t>=1) { a.t=0.0;a.edge++ }
                }
            }
        }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nest=draft ?: colony?.nest ?: return
        val w=width.toFloat();val h=height.toFloat()
        canvas.save();canvas.translate(w/2+panX,h/2+panY);canvas.scale(zoom,zoom);canvas.translate(-w/2,-h/2)
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        canvas.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint)
        paint.color=Color.argb(34,15,14,9);canvas.drawRect(0f,0f,w,h,paint)
        // Slightly translucent surface haze, distinct from the dark chambers below.
        paint.shader=LinearGradient(0f,0f,0f,h*.20f,intArrayOf(0x153f5c2e,0x003f5c2e),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h*.20f,paint);paint.shader=null
        drawNestMaterial(canvas,nest,w,h)
        drawDebris(canvas,nest,w,h)
        nest.obstacles.forEach { block ->
            val p=nest.room(block.a).point.mix(nest.room(block.b).point,block.t)
            canvas.save();canvas.translate((p.x*w).toFloat(),(p.y*h).toFloat());canvas.rotate(if (block.kind==ObstacleKind.TWIG) -32f else 18f)
            if (block.kind==ObstacleKind.TWIG) {
                line(canvas,-w*.025f,2f,w*.025f,-2f,0xff25170f.toInt(),w*.015f)
                line(canvas,-w*.025f,0f,w*.025f,-4f,0xff9c744b.toInt(),w*.011f)
                line(canvas,-w*.020f,-2f,w*.02f,-5f,0xffc6a276.toInt(),w*.003f)
                line(canvas,0f,-2f,w*.010f,-w*.018f,0xff8f6946.toInt(),w*.006f)
            } else {
                oval(canvas,-w*.019f,-w*.012f,w*.021f,w*.016f,0xff28281f.toInt())
                paint.shader=RadialGradient(-w*.009f,-w*.006f,w*.036f,intArrayOf(0xffbbb5a0.toInt(),0xff7a796b.toInt(),0xff373d37.toInt()),null,Shader.TileMode.CLAMP)
                canvas.drawOval(-w*.019f,-w*.016f,w*.018f,w*.012f,paint);paint.shader=null
                line(canvas,-w*.012f,-w*.003f,0f,-w*.009f,0x88e0dac7.toInt(),w*.002f)
            }
            canvas.restore()
        }
        val c=colony
        if(c != null) {
            drawBrood(canvas,c,w,h)
            agents.forEach { a ->
                var p=c.nest.room(a.room).point;var angle=0f
                if (a.path.size>=2 && a.edge<a.path.lastIndex) {
                    val from=nest.room(a.path[a.edge]).point;val to=nest.room(a.path[a.edge+1]).point
                    p=from.mix(to,a.t)
                    angle=Math.toDegrees(atan2((to.y-from.y)*h,(to.x-from.x)*w)).toFloat()
                }
                val resting=a.path.size<2 || a.edge>=a.path.lastIndex
                val offset=sin(a.id*13.2)*w*(if(resting).031 else .006)
                drawAnt(canvas,(p.x*w+offset).toFloat(),(p.y*h+cos(a.id*4.7)*w*(if(resting).018 else .004)).toFloat(),w*.0075f,angle,time*11+a.id,false,a.carrying,false)
            }
            // Foraging traffic extends above ground and returns carrying pieces of food.
            repeat(min(14,c.foragers)) { i ->
                val phase=(time*.055*c.activity+i*.179)%1
                val right=i%2==0
                val turn=if(phase<.5) phase*2 else (1-phase)*2
                val x=.5+(if(right)1 else -1)*turn*.40
                val y=nest.room(0).y-.005-sin(turn*PI)*.016
                drawAnt(canvas,(x*w).toFloat(),(y*h).toFloat(),w*.0075f,if((phase<.5)==right)0f else 180f,time*11+i,false,phase>.5,false)
            }
            val queen=nest.room(nest.queenRoom)
            var queenPoint=queen.point;var angle=-12f
            if(c.phase==Phase.ARRIVAL) {
                val route=nest.path(0,nest.queenRoom)
                val progress=(c.day/.25).coerceIn(0.0,.9999)*(route.size-1)
                val index=progress.toInt()
                val from=nest.room(route[index]).point;val to=nest.room(route[min(index+1,route.lastIndex)]).point
                queenPoint=from.mix(to,progress-index);angle=Math.toDegrees(atan2((to.y-from.y)*h,(to.x-from.x)*w)).toFloat()
            }
            if(c.phase==Phase.LOST) {
                // Collapsed soil covers the breached queen chamber after the colony is lost.
                canvas.save()
                val qx=(queen.x*w).toFloat();val qy=(queen.y*h).toFloat();val radius=(queen.radius*w).toFloat()
                val rubble=Path().apply {
                    moveTo(qx-radius,qy+radius*.40f)
                    for(i in 0..12)lineTo(qx-radius+radius*2*i/12,qy+radius*(.10f+(sin(i*2.8)*.18).toFloat()))
                    lineTo(qx+radius,qy+radius);lineTo(qx-radius,qy+radius);close()
                }
                canvas.clipPath(rubble);paint.reset();paint.isFilterBitmap=true
                canvas.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint);canvas.restore();paint.isAntiAlias=true
            }
            if(c.phase!=Phase.LOST) drawAnt(canvas,(queenPoint.x*w).toFloat(),(queenPoint.y*h-w*.010).toFloat(),w*.023f,angle,time*2,true,false,c.phase==Phase.ARRIVAL)
            if(c.youngQueens>0) repeat(min(c.youngQueens,6)) { i ->
                var p=Point(queen.x+.014+i*.008,queen.y+.030)
                if(c.phase==Phase.FLIGHT || c.phase==Phase.CLEARED) {
                    val progress=(c.flightProgress*1.22-i*.035).coerceIn(0.0,1.0)
                    if(progress<.6) {
                        val route=nest.path(nest.queenRoom,0)
                        val t=progress/.6*(route.size-1);val idx=t.toInt().coerceAtMost(route.size-2)
                        p=nest.room(route[idx]).point.mix(nest.room(route[idx+1]).point,t-idx)
                    } else p=Point(.5+(i-2.5)*.12*(progress-.6)/.4,nest.room(0).y-(progress-.6)*.8)
                }
                drawAnt(canvas,(p.x*w).toFloat(),(p.y*h).toFloat(),w*.017f,-85f,time*12+i,true,false,true)
            }
            c.invader?.let { enemy -> c.enemyPoint()?.let { p -> drawEnemy(canvas,enemy,(p.x*w).toFloat(),(p.y*h).toFloat(),w) } }
        }
        if(showLabels) {
            nest.chambers.filter { it.id!=0 && it.built>.8 }.forEach { room ->
                val name=when { room.id==nest.queenRoom -> "女王室";room.id==1 -> "玄関室";room.id==2 -> "育児室";room.id==3 -> "貯蔵室";else -> "新しい部屋" }
                label(canvas,name,(room.x*w).toFloat(),(room.y*h+room.radius*w*.76+15).toFloat(),w*.024f,room.id==nest.queenRoom)
            }
        }
        if(editing) {
            val queen=nest.room(nest.queenRoom)
            paint.color=0xb3dce9a8.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;paint.pathEffect=DashPathEffect(floatArrayOf(5f,5f),0f)
            canvas.drawOval((queen.x*w-queen.radius*w*1.25).toFloat(),(queen.y*h-queen.radius*w*.81).toFloat(),(queen.x*w+queen.radius*w*1.25).toFloat(),(queen.y*h+queen.radius*w*.81).toFloat(),paint)
            paint.pathEffect=null;paint.style=Paint.Style.FILL
        }
        // Depth ruler and scale belong to the scene and zoom with it.
        line(canvas,w*.945f,h*.30f,w*.945f,h*.82f,0x66e5dac5,1f)
        for(i in 0..4) {
            val y=h*(.30f+i*.13f)
            line(canvas,w*.933f,y,w*.947f,y,0x88e5dac5.toInt(),1f)
        }
        label(canvas,"土の断面",w*.86f,h*.93f,w*.022f,false)
        canvas.restore()
        if(zoom>1.02) {
            paint.color=0x990e1711.toInt();canvas.drawRoundRect(w-94,12f,w-12,43f,10f,10f,paint)
            text(canvas,String.format(java.util.Locale.JAPAN,"× %.1f",zoom),w-53,33f,14f,0xffe5e7d7.toInt(),Paint.Align.CENTER)
        }
    }
    private fun drawNestMaterial(c: Canvas,nest: Nest,w: Float,h: Float) {
        val key="${width}:${height}:"+nest.chambers.joinToString { "${it.id}:${it.x}:${it.y}:${it.radius}:${(it.built*20).toInt()}" }+nest.tunnels.toString()
        if(key!=textureKey || nestTexture==null) {
            textureKey=key
            val layer=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val target=Canvas(layer)
            val voids=Path()
            val rimPoints=mutableListOf<Point>()
            // Unite all passages and rooms: connected rooms have open mouths, never circular walls across doors.
            nest.tunnels.forEach { edge ->
                val a=nest.room(edge.a);val b=nest.room(edge.b)
                val end=a.point.mix(b.point,min(a.built,b.built))
                val dx=(end.x-a.x)*w;val dy=(end.y-a.y)*h
                val length=hypot(dx,dy).coerceAtLeast(1.0)
                val nx=-dy/length;val ny=dx/length
                val path=Path()
                val samples=max(12,(length/5).toInt())
                for(side in listOf(1,-1)) {
                    val indices=if(side==1)0..samples else samples downTo 0
                    for(i in indices) {
                        val t=i.toDouble()/samples
                        val width=w*(.021+sin(t*34+edge.b)*.0015+cos(t*61+edge.a)*.0011)
                        val x=a.x*w+dx*t+nx*width*side
                        val y=a.y*h+dy*t+ny*width*side
                        if(side==1 && i==0)path.moveTo(x.toFloat(),y.toFloat())else path.lineTo(x.toFloat(),y.toFloat())
                        rimPoints+=Point(x,y)
                    }
                }
                path.close();voids.op(path,Path.Op.UNION)
            }
            nest.chambers.filter {it.id!=0}.forEach { room ->
                val rx=room.radius*w*sqrt(room.built);val ry=rx*.70
                val path=Path()
                for(i in 0..96) {
                    val a=i*2*PI/96
                    val rough=1+sin(a*5+room.id)*.045+cos(a*11+room.id*2)*.025+sin(a*23)*.012
                    val x=room.x*w+cos(a)*rx*rough;val y=room.y*h+sin(a)*ry*rough
                    if(i==0)path.moveTo(x.toFloat(),y.toFloat())else path.lineTo(x.toFloat(),y.toFloat())
                    rimPoints+=Point(x,y)
                }
                path.close();voids.op(path,Path.Op.UNION)
            }
            paint.reset();paint.isAntiAlias=true;paint.style=Paint.Style.STROKE
            paint.color=0xd31b120c.toInt();paint.strokeWidth=w*.041f
            paint.maskFilter=BlurMaskFilter(w*.012f,BlurMaskFilter.Blur.NORMAL);target.drawPath(voids,paint)
            paint.maskFilter=null
            // Exposed soil lips retain the original granular material instead of a flat cartoon outline.
            paint.style=Paint.Style.FILL;paint.color=0xff36281a.toInt();target.drawPath(voids,paint)
            target.save();target.clipPath(voids)
            paint.colorFilter=ColorMatrixColorFilter(floatArrayOf(.38f,0f,0f,0f,0f, 0f,.36f,0f,0f,0f, 0f,0f,.32f,0f,0f, 0f,0f,0f,1f,0f))
            target.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint);paint.colorFilter=null
            paint.shader=LinearGradient(0f,h*.2f,0f,h,intArrayOf(0x40232318,0x7a090e0a),null,Shader.TileMode.CLAMP)
            target.drawRect(0f,0f,w,h,paint);paint.shader=null
            paint.style=Paint.Style.STROKE;paint.strokeWidth=w*.029f;paint.color=0xec060805.toInt()
            paint.maskFilter=BlurMaskFilter(w*.012f,BlurMaskFilter.Blur.NORMAL);target.drawPath(voids,paint)
            paint.strokeWidth=w*.008f;paint.maskFilter=null;paint.color=0x696e5232
            target.translate(0f,w*.004f);target.drawPath(voids,paint)
            target.restore();paint.style=Paint.Style.FILL
            // Individually lit crumbs soften the silhouette; deterministic geometry survives saves.
            rimPoints.forEachIndexed { index,p ->
                if(index%3==0) {
                    val r=w*(.0008f+(index%5)*.00030f)
                    paint.color=if(index%2==0)0x88775639.toInt()else 0x88433020.toInt()
                    target.drawCircle(p.x.toFloat(),p.y.toFloat(),r,paint)
                }
            }
            nestTexture=layer
        }
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        nestTexture?.let {c.drawBitmap(it,0f,0f,paint)}
    }
    private fun drawDebris(c: Canvas,nest: Nest,w: Float,h: Float) {
        nest.chambers.filter { it.id!=0 }.forEach { r ->
            repeat(24) { i ->
                val angle=i*2.399+r.id;val radius=sqrt((i+.5)/24)*r.radius
                val x=(r.x+cos(angle)*radius*.95)*w;val y=r.y*h+sin(angle)*radius*w*.67
                paint.color=if(i%3==0)0x775e4d36 else 0x666a543c
                c.drawCircle(x.toFloat(),y.toFloat(),w*(.0007f+(i%4)*.0004f),paint)
            }
        }
    }
    private fun drawBrood(c: Canvas,state: Colony,w: Float,h: Float) {
        val nursery=state.nest.room(if(state.nest.chambers.any { it.id==2 })2 else state.nest.queenRoom)
        BroodStage.entries.forEachIndexed { stageIndex,stage ->
            val count=state.count(stage)
            repeat(min(count,18)) { i ->
                val x=(nursery.x*w+(stageIndex-1)*w*.026+sin(i*2.399)*sqrt((i+1)/18.0)*w*.012).toFloat()
                val y=(nursery.y*h+cos(i*2.399)*sqrt((i+1)/18.0)*w*.012).toFloat()
                val radius=w*when(stage){BroodStage.EGG->.0028f;BroodStage.LARVA->.0045f;BroodStage.PUPA->.006f}
                oval(c,x-radius,y-radius*.6f,x+radius,y+radius*.7f,if(stage==BroodStage.PUPA)0xffb69e70.toInt()else 0xffded7b8.toInt())
                line(c,x-radius*.5f,y-radius*.25f,x+radius*.3f,y-radius*.25f,0x99fff3d7.toInt(),.6f)
            }
        }
        val store=state.nest.chambers.firstOrNull { it.id==3 }?:nursery
        repeat(min(22,(state.food/18).toInt())){i ->
            val x=(store.x*w+sin(i*5.77)*w*.033).toFloat();val y=(store.y*h+cos(i*4.13)*w*.014).toFloat()
            oval(c,x-2,y-1,x+3,y+2,if(i%3==0)0xff8b5e36.toInt()else 0xff89915a.toInt())
        }
    }
    private fun drawAnt(c: Canvas,x: Float,y: Float,s: Float,angle: Float,t: Double,queen: Boolean,food: Boolean,wings: Boolean) {
        val gait=((t/(2*PI)*12).toInt()%12+12)%12
        val key=gait+(if(queen)16 else 0)+(if(food)32 else 0)+(if(wings)64 else 0)
        val sprite=antSprites.getOrPut(key) {
            Bitmap.createBitmap(160,128,Bitmap.Config.ARGB_8888).also {
                renderAnt(Canvas(it),80f,64f,32f,0f,gait*2*PI/12,queen,food,wings)
            }
        }
        c.save();c.translate(x,y);c.rotate(angle)
        spriteRect.set(-s*2.5f,-s*2f,s*2.5f,s*2f)
        c.drawBitmap(sprite,null,spriteRect,spritePaint)
        c.restore()
    }
    private fun renderAnt(c: Canvas,x: Float,y: Float,s: Float,angle: Float,t: Double,queen: Boolean,food: Boolean,wings: Boolean) {
        c.save();c.translate(x,y);c.rotate(angle)
        // Six jointed legs connect to the thorax; antennae are elbowed.
        for(side in listOf(-1,1)) for(leg in 0..2) {
            val hip=(-.4f+leg*.37f)*s;val sweep=sin(t+leg*2.2+side)*.33f*s
            val kneeX=hip+(-.65f+leg*.6f)*s+sweep.toFloat()
            line(c,hip,side*s*.22f,kneeX,side*s*.77f,0xff231f16.toInt(),s*.15f)
            line(c,kneeX,side*s*.77f,kneeX-s*.28f+sweep.toFloat(),side*s*1.23f,0xff504a35.toInt(),s*.075f)
        }
        if(wings) {
            paint.color=0x88e4e4be.toInt()
            c.save();c.rotate(-18f+(sin(t*3)*5).toFloat());c.drawOval(-s*1.8f,-s*1.4f,s*.35f,-s*.1f,paint);c.restore()
            c.save();c.rotate(18f-(sin(t*3)*5).toFloat());c.drawOval(-s*1.8f,s*.1f,s*.35f,s*1.4f,paint);c.restore()
        }
        val abdomen=if(queen)1.04f else .77f
        paint.shader=RadialGradient(-s*.85f,-s*.22f,s*1.10f,intArrayOf(0xff716856.toInt(),0xff282c23.toInt(),0xff090e0c.toInt()),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
        c.drawOval(-s*(.65f+abdomen),-s*.55f,-s*.3f,s*.55f,paint);paint.shader=null
        line(c,-s*.7f,-s*.42f,-s*.62f,s*.40f,0x665e604c,s*.07f)
        oval(c,-s*.4f,-s*.20f,-s*.07f,s*.2f,0xff181e17.toInt())
        paint.shader=RadialGradient(s*.13f,-s*.13f,s*.66f,intArrayOf(0xff5b5845.toInt(),0xff151c16.toInt()),null,Shader.TileMode.CLAMP)
        c.drawOval(-s*.13f,-s*.32f,s*.64f,s*.32f,paint);paint.shader=null
        oval(c,s*.60f,-s*.37f,s*1.26f,s*.37f,0xff22291f.toInt())
        oval(c,s*.72f,-s*.31f,s*.88f,-s*.18f,0xffb4af88.toInt())
        for(side in listOf(-1,1)) {
            line(c,s*1.12f,side*s*.20f,s*1.5f,side*s*.55f,0xff7d7860.toInt(),s*.08f)
            line(c,s*1.5f,side*s*.55f,s*1.92f,side*s*.46f,0xff7d7860.toInt(),s*.065f)
        }
        if(food) oval(c,s*1.38f,-s*.33f,s*2.05f,s*.35f,0xff91a762.toInt())
        c.restore()
    }
    private fun drawEnemy(c: Canvas,enemy: Invader,x: Float,y: Float,w: Float) {
        val s=w*.026f
        c.save();c.translate(x,y);c.rotate(78f)
        for(side in listOf(-1,1))for(i in 0..2) {
            val move=sin(time*13+i*2)*s*.25
            line(c,(-.6f+i*.55f)*s,side*s*.35f,((-1.2f+i*.8f)*s+move).toFloat(),side*s*1.3f,0xff806a45.toInt(),s*.14f)
        }
        val body=if(enemy.kind==EnemyKind.BEETLE)0xff34392a.toInt()else 0xff685035.toInt()
        paint.shader=RadialGradient(-s*.3f,-s*.2f,s*2f,intArrayOf(0xffa49c6e.toInt(),body,0xff1d1912.toInt()),null,Shader.TileMode.CLAMP)
        c.drawOval(-s*1.65f,-s*.7f,s*.4f,s*.7f,paint);paint.shader=null
        line(c,-s*1.6f,0f,s*.2f,0f,0xff191b13.toInt(),s*.10f)
        oval(c,s*.25f,-s*.47f,s*1.0f,s*.47f,0xff423624.toInt())
        if(enemy.kind==EnemyKind.EARWIG)for(side in listOf(-1,1))line(c,-s*1.5f,side*s*.4f,-s*2.3f,side*s*.6f,0xffc0a46c.toInt(),s*.12f)
        c.restore()
        val bar=w*.10f
        line(c,x-bar/2,y-s*2,x+bar/2,y-s*2,0xff392a23.toInt(),3f)
        line(c,x-bar/2,y-s*2,x-bar/2+bar*(enemy.hp/enemy.maxHp).coerceIn(0.0,1.0).toFloat(),y-s*2,0xffd69a75.toInt(),3f)
    }
    private fun line(c: Canvas,x1: Float,y1: Float,x2: Float,y2: Float,color: Int,width: Float) {
        paint.color=color;paint.strokeWidth=width;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;c.drawLine(x1,y1,x2,y2,paint);paint.style=Paint.Style.FILL
    }
    private fun oval(c: Canvas,left: Float,top: Float,right: Float,bottom: Float,color: Int) { paint.color=color;paint.style=Paint.Style.FILL;rect.set(left,top,right,bottom);c.drawOval(rect,paint) }
    private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,color: Int,align: Paint.Align=Paint.Align.LEFT) {
        paint.color=color;paint.textSize=size;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textAlign=align;c.drawText(value,x,y,paint)
    }
    private fun label(c: Canvas,value: String,x: Float,y: Float,size: Float,accent: Boolean) {
        paint.textSize=size
        val half=paint.measureText(value)/2+size*.65f
        paint.color=0xc01b2018.toInt();c.drawRoundRect(x-half,y-size*1.05f,x+half,y+size*.34f,8f,8f,paint)
        text(c,value,x,y,size,if(accent)0xffd8dfa8.toInt()else 0xffc8c0a7.toInt(),Paint.Align.CENTER)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {lastX=event.x;lastY=event.y;downX=event.x;downY=event.y;moved=false;return true}
            MotionEvent.ACTION_MOVE -> {
                if(hypot(event.x-downX,event.y-downY)>12) moved=true
                if(!editing && !scaleDetector.isInProgress && moved) {
                    panX+=event.x-lastX;panY+=event.y-lastY;clampPan();invalidate()
                }
                lastX=event.x;lastY=event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if(!moved && !scaleDetector.isInProgress) {
                    performClick()
                    val point=Point(((event.x-width/2-panX)/zoom+width/2).toDouble()/width,((event.y-height/2-panY)/zoom+height/2).toDouble()/height)
                    if(editing) onEdit?.invoke(point) else inspect(point)
                }
                return true
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    private fun inspect(point: Point) {
        val c=colony ?: return
        val room=c.nest.chambers.filter { it.id!=0 }.minByOrNull { it.point.distance(point) } ?: return
        if(room.point.distance(point)>.10)return
        if(room.id==c.nest.queenRoom) onInspect?.invoke("女王室","女王は卵を産み、群れを次の世代へつなぎます。創設期には、自分の体に蓄えた栄養で最初の働きアリを育てます。\n\n女王の健康 ${c.queenHealth.toInt()}%\n子女王 ${c.youngQueens}匹\nここへの最短侵入経路には ${c.nest.path(0,c.nest.queenRoom).zipWithNext().sumOf { c.nest.blocks(it.first,it.second).size }}個の障害物があります。")
        else onInspect?.invoke("巣の観察","卵 ${c.count(BroodStage.EGG)}個 → 幼虫 ${c.count(BroodStage.LARVA)}匹 → 蛹 ${c.count(BroodStage.PUPA)}匹\n\n採餌 ${c.foragers}匹 / 掘削 ${c.builders}匹 / 防衛 ${c.guards}匹\n残りの働きアリは育児と巣の手入れを担います。巣が成長すると、画面のアリ1匹が複数の働きアリを代表します。")
    }
}
