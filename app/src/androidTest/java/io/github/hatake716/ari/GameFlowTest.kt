package io.github.hatake716.ari

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameFlowTest {
    private lateinit var context: Context
    private lateinit var store: SaveStore
    private lateinit var device: UiDevice
    private var activity: ActivityScenario<MainActivity>?=null
    @Before fun setUp() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        context=instrumentation.targetContext;store=SaveStore(context);device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().setWaitForIdleTimeout(150).setWaitForSelectorTimeout(1500)
        // These tests run only on the dedicated emulator and touch only this app's newly created data.
        for(i in 0..2)store.delete(i)
        context.getSharedPreferences("audio",Context.MODE_PRIVATE).edit().putBoolean("enabled",false).commit()
    }
    @After fun close() {device.pressHome();activity?.close()}
    private fun launch() {
        activity=ActivityScenario.launch(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.textContains("新しい巣をつくる")),5000))
    }
    private fun click(text: String) {
        val view=device.wait(Until.findObject(By.text(text)),4000)
        assertNotNull("Missing control: $text",view);view.click();device.waitForIdle(1500)
    }
    private fun newSlot(index: Int) {
        val card=device.wait(Until.findObject(By.textStartsWith("0${index+1}     ")),4000)
        assertNotNull("Missing save slot ${index+1}",card)
        card.click();device.waitForIdle(1500)
    }
    private fun tapScene(x: Double,y: Double) {
        val rect=device.findObject(By.descStartsWith("アリの巣の断面図")).visibleBounds
        device.click((rect.left+x*rect.width()).toInt(),(rect.top+y*rect.height()).toInt());device.waitForIdle(800)
    }
    private fun screenshot(name: String) {
        val dir=File(context.getExternalFilesDir(null),"validation").apply {mkdirs()}
        assertTrue(device.takeScreenshot(File(dir,"$name.png")))
    }
    @Test fun createPlaceBothObstaclesObserveAndReload() {
        launch();screenshot("01-home")
        newSlot(0);click("小枝");tapScene(.495,.2225)
        assertTrue(device.hasObject(By.textContains("障害物 1/8")))
        click("小石");tapScene(.345,.59)
        assertTrue(device.hasObject(By.textContains("障害物 2/8")))
        screenshot("02-editor")
        click("この巣に、女王を迎える")
        assertTrue(device.hasObject(By.text("観察のみ  ·  自動保存")))
        assertFalse(device.hasObject(By.text("女王室"))) // no edit button in observation UI
        click("1倍");screenshot("03-queen-arrival")
        device.pressBack();device.waitForIdle(1500)
        val saved=store.read(0) as Slot.Saved
        assertEquals(2,saved.colony.nest.obstacles.size)
        assertEquals(0,saved.colony.workers)
        newSlot(0)
        assertTrue(device.hasObject(By.text("観察のみ  ·  自動保存")))
        assertFalse(device.hasObject(By.text("この巣に、女王を迎える")))
    }
    @Test fun threeSaveSlotsRemainIndependentAcrossRecreation() {
        for(i in 0..2)store.write(i,Colony(Nest.create(i,i),100L+i).apply {day=10.0+i*20;phase=Phase.FOUNDING;speed=0})
        activity=ActivityScenario.launch(MainActivity::class.java)
        for(i in 0..2) assertTrue(device.wait(Until.hasObject(By.textStartsWith("0${i+1}     ")),3000))
        newSlot(1);activity!!.recreate();device.waitForIdle(2000)
        assertTrue(device.hasObject(By.textContains("巣 02")))
        device.pressBack();device.waitForIdle(1000)
        for(i in 0..2) {
            val state=(store.read(i) as Slot.Saved).colony
            assertEquals(10.0+i*20,state.day,0.0);assertEquals(i,state.nest.shape)
        }
        screenshot("04-three-saves")
    }
    @Test fun editorCanAddRoomChangeQueenAndRemoveOnlySafeElements() {
        launch();newSlot(0)
        click("部屋＋");tapScene(.78,.80)
        click("女王室");tapScene(.78,.80)
        click("この巣に、女王を迎える");click("1倍");device.pressBack();device.waitForIdle(1000)
        val state=(store.read(0) as Slot.Saved).colony
        assertEquals(6,state.nest.chambers.size);assertEquals(5,state.nest.queenRoom)
        assertTrue(state.nest.path(0,5).isNotEmpty())
    }
    @Test fun matureColonyShowsMotionZoomAndJournal() {
        val c=Colony(Nest.create(),817).apply {
            phase=Phase.GROWING;day=190.0;adults+=WorkerCohort(140);brood+=Brood(30,10.0);brood+=Brood(24,27.0);brood+=Brood(18,49.0)
            food=250.0;speed=1;nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
            record("働きアリが餌を持ち帰り、幼虫たちの世話をしています。")
        }
        store.write(0,c);activity=ActivityScenario.launch(MainActivity::class.java);device.waitForIdle(2000);newSlot(0)
        assertTrue(device.hasObject(By.text("140")))
        screenshot("05-colony")
        val view=device.findObject(By.descStartsWith("アリの巣の断面図"))
        view.setGestureMargin(70);view.pinchOpen(.50f);device.waitForIdle(1000);screenshot("06-close-up")
        click("観察記録");assertTrue(device.hasObject(By.textContains("幼虫たちの世話")));click("観察に戻る")
    }
    @Test fun exposedQueenIsEatenAndTerminalStateIsSaved() {
        val c=Colony(Nest.create(),9).apply {phase=Phase.GROWING;day=170.0;speed=3600}
        c.spawnRaid(EnemyKind.BEETLE)
        c.invader!!.segment=c.invader!!.route.lastIndex-1;c.invader!!.progress=.94
        store.write(0,c);activity=ActivityScenario.launch(MainActivity::class.java);device.waitForIdle(1500);newSlot(0)
        assertTrue(device.wait(Until.hasObject(By.text("巣の灯が消えました。")),12000))
        screenshot("07-loss")
        click("新たな女王アリを迎える巣を作る")
        assertTrue(device.wait(Until.hasObject(By.text("この巣に、女王を迎える")),3000))
        assertEquals(Phase.LOST,(store.read(0) as Slot.Saved).colony.phase)
        click("小枝");tapScene(.495,.2225)
        click("この巣に、女王を迎える")
        val restarted=(store.read(0) as Slot.Saved).colony
        assertEquals(Phase.ARRIVAL,restarted.phase);assertEquals(0,restarted.workers)
        assertEquals(100.0,restarted.queenHealth,0.0);assertEquals(1,restarted.nest.obstacles.size)
        assertEquals(0,restarted.repelled);assertEquals(0,restarted.losses)
    }
    @Test fun childQueenDepartureShowsClearAndPreservesMotherQueen() {
        val c=Colony(Nest.create(),12).apply {
            phase=Phase.FLIGHT;day=1180.0;youngQueens=4;males=8;royalLaid=true;royalAdultDay=1160.0
            flightProgress=.90;speed=3600;adults+=WorkerCohort(180);nextRaid=2000.0
        }
        store.write(0,c);activity=ActivityScenario.launch(MainActivity::class.java);device.waitForIdle(1500);newSlot(0)
        assertTrue(device.wait(Until.hasObject(By.text("命は、次の巣へ。")),12000))
        screenshot("08-clear")
        assertFalse(device.hasObject(By.text("観察に戻る")))
        click("新たな女王アリを迎える巣を作る")
        assertTrue(device.wait(Until.hasObject(By.text("巣をつくる")),3000))
        assertTrue(device.hasObject(By.text("女王室")))
        val saved=(store.read(0) as Slot.Saved).colony
        assertEquals(Phase.CLEARED,saved.phase);assertTrue(saved.queenHealth>0)
        screenshot("08-next-nest-editor")
    }
    @Test fun corruptSaveIsNotSilentlyOverwritten() {
        val file=File(context.filesDir,"colonies/colony-1.json")
        file.parentFile!!.mkdirs();file.writeText("{broken save")
        activity=ActivityScenario.launch(MainActivity::class.java);device.waitForIdle(1500)
        assertTrue(device.hasObject(By.textContains("保存データの確認")))
        newSlot(0);assertTrue(device.wait(Until.hasObject(By.text("保存データを保護しました")),4000))
        assertEquals("{broken save",file.readText())
    }
    @Test fun musicSettingPersistsAndAppReturnsAfterBackgrounding() {
        launch();click("BGM OFF");assertTrue(device.hasObject(By.text("BGM ON")))
        assertTrue(context.getSharedPreferences("audio",Context.MODE_PRIVATE).getBoolean("enabled",false))
        newSlot(0);click("この巣に、女王を迎える");click("1倍")
        device.pressHome()
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        assertTrue(device.wait(Until.hasObject(By.text("観察のみ  ·  自動保存")),5000))
        assertTrue(store.read(0) is Slot.Saved)
    }
    @Test fun invasionIsVisibleAndSpeedChoiceDoesNotSkipIt() {
        val c=Colony(Nest.create(),21).apply {phase=Phase.GROWING;day=800.0;speed=604800;adults+=WorkerCohort(40)}
        c.spawnRaid(EnemyKind.BEETLE,2.0)
        c.nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
        store.write(0,c);activity=ActivityScenario.launch(MainActivity::class.java);device.waitForIdle(1500);newSlot(0)
        assertTrue(device.wait(Until.hasObject(By.text("オサムシが侵入中")),3000))
        assertTrue(device.hasObject(By.textContains("一時的に最大1時間/秒")))
        screenshot("09-invasion")
    }
    @Test fun naturalColonyFromEditorReachesFlightWithoutPlayerIntervention() {
        launch();newSlot(0)
        click("小枝");tapScene(.495,.2225)
        click("小石");tapScene(.345,.59)
        click("この巣に、女王を迎える")
        click("7日/秒")
        assertTrue("A healthy, protected founding colony should reach the next generation",
            device.wait(Until.hasObject(By.text("命は、次の巣へ。")),330000))
        screenshot("10-natural-completion")
        click("新たな女王アリを迎える巣を作る")
        assertTrue(device.wait(Until.hasObject(By.text("巣をつくる")),3000))
        val saved=(store.read(0) as Slot.Saved).colony
        assertEquals(Phase.CLEARED,saved.phase)
        assertTrue(saved.workers>80);assertTrue(saved.youngQueens>0);assertTrue(saved.repelled>0)
        assertTrue(saved.nest.capacity>Nest.create().capacity)
        screenshot("11-next-nest-editor")
    }

    @Test fun reopeningEndedSlotCanStartAgainAndCancellingPreservesAllThreeSaves() {
        for(i in 0..2)store.write(i,Colony(Nest.create(),70L+i).apply {
            phase=if(i==1)Phase.CLEARED else Phase.GROWING
            day=400.0+i;speed=0;adults+=WorkerCohort(50+i)
            if(i==1){youngQueens=3;flightProgress=1.0}
        })
        val otherSaves=listOf(0,2).map {StateCodec.encode((store.read(it) as Slot.Saved).colony)}
        activity=ActivityScenario.launch(MainActivity::class.java)
        assertNotNull(device.wait(Until.findObject(By.textStartsWith("02     ")),5000))
        newSlot(1)
        assertTrue(device.wait(Until.hasObject(By.text("命は、次の巣へ。")),3000))
        click("新たな女王アリを迎える巣を作る")
        assertTrue(device.wait(Until.hasObject(By.text("巣をつくる")),3000))
        assertTrue(device.hasObject(By.text("巣 02  /  はじめの一度だけ")))
        click("部屋＋");tapScene(.78,.80)
        device.pressBack();click("一覧に戻る")
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("02     ")),3000))
        val finished=(store.read(1) as Slot.Saved).colony
        assertEquals(Phase.CLEARED,finished.phase);assertEquals(3,finished.youngQueens)
        assertEquals(5,finished.nest.chambers.size)
        assertEquals(otherSaves,listOf(0,2).map {StateCodec.encode((store.read(it) as Slot.Saved).colony)})
        click("遊び方と生態")
        assertTrue(device.wait(Until.hasObject(By.text("観察に戻る")),3000))
        click("観察に戻る")
        newSlot(1);click("新たな女王アリを迎える巣を作る")
        click("この巣に、女王を迎える")
        assertTrue(device.wait(Until.hasObject(By.text("観察のみ  ·  自動保存")),3000))
        assertEquals(Phase.ARRIVAL,(store.read(1) as Slot.Saved).colony.phase)
        assertEquals(otherSaves,listOf(0,2).map {StateCodec.encode((store.read(it) as Slot.Saved).colony)})
    }

}
