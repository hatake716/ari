package io.github.hatake716.ari

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GrowthVisualTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val dir get()=File(context.getExternalFilesDir(null),"visual-v1.2").apply {mkdirs()}
    private fun write(bitmap:Bitmap,name:String) {
        File(dir,"$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun allPredatorAnatomiesHaveUnclippedDistinctGaitPoses() {
        val renderer=EnemyRenderer()
        val sheet=Bitmap.createBitmap(1600,1320,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xffc5b798.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=32f;color=0xff29251c.toInt()}
        EnemyKind.entries.forEachIndexed {row,kind->
            canvas.drawText(kind.label,48f,55f+row*440,p)
            renderer.draw(canvas,445f,250f+row*440,78f,0f,.8,kind)
        }
        write(sheet,"enemy-models")
    }
    private fun EnemyRenderer.draw(c:Canvas,x:Float,y:Float,s:Float,h:Float,phase:Double,kind:EnemyKind) {
        draw(c,kind,x,y,s,h,phase)
        draw(c,kind,1250f,y,48f,28f,2.4)
        val hashes=mutableSetOf<Int>()
        for(frame in 0..23) {
            val image=Bitmap.createBitmap(720,448,Bitmap.Config.ARGB_8888)
            draw(Canvas(image),kind,360f,224f,72f,0f,frame*2*Math.PI/24)
            val pixels=IntArray(720*448);image.getPixels(pixels,0,720,0,0,720,448)
            assertTrue(pixels.count {Color.alpha(it)>0}>8000)
            assertTrue(pixels.take(720).all {it==0});assertTrue(pixels.takeLast(720).all {it==0})
            for(yEdge in 0 until 448){assertEquals(0,pixels[yEdge*720]);assertEquals(0,pixels[yEdge*720+719])}
            hashes+=pixels.contentHashCode();image.recycle()
        }
        assertTrue("${kind.name} gait",hashes.size>=20)
    }
    @Test fun naturalGrowthRendersConnectedOccupiedRoomsAcrossMultipleYears() {
        val state=Colony(Nest.create(),812).apply {
            nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.TWIG)
            nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
        }
        val sheet=Bitmap.createBitmap(1800,1280,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xff141a17.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xffdedfc4.toInt();textSize=23f}
        for((index,day) in listOf(100,400,600).withIndex()) {
            state.advanceDays(day-state.day)
            val frozen=StateCodec.decode(StateCodec.encode(state)).apply {speed=0}
            File(dir,"growth-day-$day.json").writeText(StateCodec.encode(frozen))
            instrumentation.runOnMainSync {
                val view=NestView(context).apply {colony=frozen;showLabels=false;layout(0,0,600,1150)}
                repeat(40){view.animate(.1,true)}
                canvas.save();canvas.translate(index*600f,0f)
                canvas.drawText("${day}日 / 働きアリ ${state.workers}匹",22f,43f,p)
                canvas.drawText("完成 ${state.nest.completedRooms}室 · 群れ ${state.population}匹",22f,79f,p)
                canvas.translate(0f,110f);canvas.clipRect(0f,0f,600f,1150f);view.draw(canvas);canvas.restore()
            }
        }
        assertEquals(48,state.nest.completedRooms);write(sheet,"colony-growth")
    }
    @Test fun expandedNestCanBeInspectedZoomedAndRestoredFromSave() {
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().setWaitForIdleTimeout(150).setWaitForSelectorTimeout(1500)
        val store=SaveStore(context)
        for(i in 0..2)store.delete(i)
        context.getSharedPreferences("audio",Context.MODE_PRIVATE).edit().putBoolean("enabled",false).commit()
        val state=Colony(Nest.create(),33).apply {
            phase=Phase.GROWING;day=420.0;speed=0;nextRaid=1e9;food=1700.0;adults+=WorkerCohort(600)
            brood+=Brood(170,12.0);brood+=Brood(130,30.0);brood+=Brood(100,50.0)
        }
        repeat(28){NestGrowth.plan(state.nest)!!.built=1.0}
        val site=NestGrowth.plan(state.nest)!!.apply {built=.78}
        store.write(0,state)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            device.wait(Until.findObject(By.textStartsWith("01     ")),5000)!!.click()
            assertTrue(device.wait(Until.hasObject(By.textContains("巣 32室")),4000))
            assertTrue(device.hasObject(By.textContains("掘削中 78%")))
            assertTrue(device.takeScreenshot(File(dir,"expanded-ui.png")))
            var view=device.findObject(By.descStartsWith("アリの巣の断面図"))
            val bounds=view.visibleBounds
            val camera=NestCamera().apply {depth=state.nest.depth}
            val p=camera.toScreen(site.point,bounds.width(),bounds.height())
            device.click(bounds.left+p.x.toInt(),bounds.top+p.y.toInt())
            assertTrue(device.wait(Until.hasObject(By.text("${state.nest.roomName(site)}を掘削中")),3000))
            device.findObject(By.text("観察に戻る")).click()
            view=device.wait(Until.findObject(By.descStartsWith("アリの巣の断面図")),3000)!!
            view.setGestureMargin(70);view.pinchOpen(.65f)
            assertTrue("Pinch must change the rendered camera",device.wait(Until.gone(By.descContains("拡大率 1.0倍")),3000))
            assertNotNull(device.findObject(By.descStartsWith("アリの巣の断面図")))
            device.waitForIdle(500)
            assertTrue(device.takeScreenshot(File(dir,"expanded-zoom.png")))
            device.findObject(By.text("全体")).click()
            assertTrue(device.wait(Until.hasObject(By.descContains("拡大率 1.0倍")),3000))
            device.pressBack();device.wait(Until.findObject(By.textStartsWith("01     ")),4000)!!.click()
            assertTrue(device.wait(Until.hasObject(By.textContains("巣 32室")),4000))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.textContains("掘削中 78%")),4000))
            val saved=(store.read(0) as Slot.Saved).colony
            assertEquals(34,saved.nest.chambers.size);assertEquals(.78,saved.nest.room(site.id).built,0.0)
        } finally {device.pressHome();scenario.close()}
    }
}
