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
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class VisualSeasonTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun write(bitmap:Bitmap,name:String) {
        val dir=File(context.getExternalFilesDir(null),"visual-v1.1").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun allTwelveMonthlyImagesAreDistinctAndCacheIsBounded() {
        val bg=SeasonalBackground(context)
        val signatures=mutableSetOf<Long>()
        val sheet=Bitmap.createBitmap(1200,1200,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xff131b15.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=26f;color=0xffeef0df.toInt()}
        for(month in 1..12) {
            val frame=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
            bg.draw(Canvas(frame),SeasonDate(month,1),600f,900f)
            assertEquals(month,bg.currentMonth);assertTrue(bg.cachedMonths<=2)
            val pixels=IntArray(600*150);frame.getPixels(pixels,0,600,0,0,600,150)
            val crc=CRC32();pixels.forEach {color->repeat(4){crc.update(color ushr (it*8))}}
            assertTrue("Month $month repeats another image",signatures.add(crc.value))
            val x=((month-1)%3)*400f;val y=((month-1)/3)*300f
            canvas.drawBitmap(frame,Rect(0,0,600,375),RectF(x,y+40,x+400,y+290),null)
            canvas.drawText("${month}月",x+14,y+30,paint)
            frame.recycle()
        }
        assertEquals(12,signatures.size);write(sheet,"twelve-months")
    }
    @Test fun renderedAntModelsShowCastesAndAllGaitFramesWithoutClipping() {
        val renderer=AntRenderer()
        val sheet=Bitmap.createBitmap(1500,1320,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xffc5b798.toInt())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=30f;color=0xff29251c.toInt()}
        val names=listOf("働きアリ  /  worker","女王  /  queen","翅のある子女王  /  alate")
        for(row in 0..2) {
            canvas.drawText(names[row],50f,65f+row*420,p)
            renderer.draw(canvas,330f,270f+row*420,100f,0f,.8,row>0,false,row==2)
            renderer.draw(canvas,940f,270f+row*420,64f,-30f,2.7,row>0,row==0,row==2)
        }
        write(sheet,"ant-models")
        val hashes=mutableSetOf<Int>()
        for(frame in 0..23) {
            val image=Bitmap.createBitmap(640,520,Bitmap.Config.ARGB_8888)
            renderer.draw(Canvas(image),320f,260f,96f,0f,frame*2*Math.PI/24,true,false,true)
            val pixels=IntArray(640*520);image.getPixels(pixels,0,640,0,0,640,520)
            assertTrue(pixels.count {Color.alpha(it)>0}>10000)
            assertTrue(pixels.take(640).all {it==0})
            hashes+=pixels.contentHashCode();image.recycle()
        }
        assertTrue(hashes.size>=20)
    }
    @Test fun pauseAndTerminalStateFreezeBothBodyAndAntennae() {
        instrumentation.runOnMainSync {
            val state=Colony(Nest.create(),44).apply {phase=Phase.GROWING;day=60.0;adults+=WorkerCohort(50)}
            val view=NestView(context).apply {colony=state;layout(0,0,600,900)}
            fun pixels():IntArray {
                val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                return IntArray(600*900).also {bitmap.getPixels(it,0,600,0,0,600,900);bitmap.recycle()}
            }
            repeat(10){view.animate(.1,true)}
            val before=pixels();view.animate(.2,false)
            assertArrayEquals(before,pixels())
            view.animate(.15,true)
            assertFalse(before.contentEquals(pixels()))
            state.phase=Phase.CLEARED
            val ended=pixels();view.animate(.2,true)
            assertArrayEquals(ended,pixels())
        }
    }
    @Test fun monthChangesDuringFastForwardAndWinterSaveRestoresItsBackground() {
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().setWaitForIdleTimeout(150).setWaitForSelectorTimeout(1500)
        val store=SaveStore(context)
        for(i in 0..2)store.delete(i)
        context.getSharedPreferences("audio",Context.MODE_PRIVATE).edit().putBoolean("enabled",false).commit()
        val spring=Colony(Nest.create(),33).apply {day=30.90;phase=Phase.GROWING;speed=0;nextRaid=1e9;food=500.0;adults+=WorkerCohort(80)}
        val winter=StateCodec.decode(StateCodec.encode(spring)).apply {day=259.0}
        store.write(0,spring);store.write(1,winter)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            assertNotNull(device.wait(Until.findObject(By.textStartsWith("01     ")),5000))
            device.findObject(By.textStartsWith("01     ")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("5月31日")),4000))
            device.findObject(By.text("1日/秒")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("6月1日")),4000))
            device.findObject(By.text("Ⅱ")).click()
            val june=File(context.getExternalFilesDir(null),"visual-v1.1/june-ui.png")
            june.parentFile!!.mkdirs();assertTrue(device.takeScreenshot(june))
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.textStartsWith("02     ")),4000))
            device.findObject(By.textStartsWith("02     ")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("1月15日")),4000))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.textContains("1月15日")),4000))
            device.waitForIdle(500)
            assertTrue(device.takeScreenshot(File(june.parentFile,"winter-ui.png")))
            assertEquals(SeasonDate(1,15),SeasonCalendar.date((store.read(1) as Slot.Saved).colony.day))
        } finally {device.pressHome();scenario.close()}
    }
}
