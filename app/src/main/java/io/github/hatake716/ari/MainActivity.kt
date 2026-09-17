package io.github.hatake716.ari

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val ink=Color.rgb(20,26,23)
    private val cream=Color.rgb(234,235,217)
    private val muted=Color.rgb(165,178,157)
    private val accent=Color.rgb(203,217,156)
    private val panel=Color.rgb(32,41,34)
    private lateinit var root: FrameLayout
    private lateinit var store: SaveStore
    private lateinit var music: AmbientMusic
    private val handler=Handler(Looper.getMainLooper())
    private var active=false
    private var page="home"
    private var scene: NestView?=null
    private var colony: Colony?=null
    private var draft: Nest?=null
    private var slot=0
    private var editorTool="queen"
    private var status: TextView?=null
    private var phaseTitle: TextView?=null
    private var progressText: TextView?=null
    private var growthText: TextView?=null
    private var dateLabel: TextView?=null
    private var metrics: List<TextView> = emptyList()
    private var pauseButton: Button?=null
    private var speedButtons=mutableListOf<Button>()
    private var endShown=false
    private var lastFrame=0L
    private var lastHud=0L
    private var lastHudKey=""
    private var lastSave=0L
    private var dialogCount=0
    private var saveError=false
    private var resumedFromBackground=false
    private var homeDemo: Colony?=null
    private val frame=object: Runnable {
        override fun run() {
            if(!active)return
            val now=SystemClock.elapsedRealtime()
            val elapsed=if(lastFrame==0L)0.0 else ((now-lastFrame)/1000.0).coerceAtLeast(0.0)
            lastFrame=now
            val c=colony
            if(page=="game" && c!=null && dialogCount==0 && c.speed>0 && !c.terminal) {
                val actual=when { c.phase==Phase.ARRIVAL -> min(c.speed,3600);c.invader!=null || c.phase==Phase.FLIGHT -> min(c.speed,3600);else -> c.speed }
                c.advanceDays(elapsed*actual/86400,stopAtEvents=true)
                if(c.terminal && !endShown) { endShown=true;saveCurrent();handler.post { if(page=="game")showEnding(c) } }
            }
            scene?.animate(elapsed.coerceAtMost(.20),dialogCount==0 && (page=="home" || (page=="game" && (c?.speed ?: 0)>0)))
            if(now-lastHud>200) { updateHud();lastHud=now }
            if(page=="game" && now-lastSave>10_000) { saveCurrent();lastSave=now }
            handler.postDelayed(this,33)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=SaveStore(this);music=AmbientMusic(this)
        root=FrameLayout(this).apply {setBackgroundColor(ink)}
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){view,insets ->
            val padding=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(padding.left,padding.top,padding.right,padding.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { when(page) { "game" -> {saveCurrent();showHome()};"editor" -> confirmLeaveEditor();else -> finish() } }
        })
        // Restore the current game after process death, while keeping the three files authoritative.
        val restoreSlot=savedInstanceState?.getInt("slot",-1)?:-1
        if(restoreSlot in 0..2 && savedInstanceState?.getString("page")=="game") openSlot(restoreSlot) else showHome()
    }
    override fun onSaveInstanceState(outState: Bundle) { saveCurrent();outState.putInt("slot",slot);outState.putString("page",page);super.onSaveInstanceState(outState) }
    override fun onResume() {
        super.onResume();active=true
        if(resumedFromBackground && page=="game") colony?.let { c ->
            val days=StateCodec.offlineDays(c,System.currentTimeMillis())
            c.advanceDays(days);saveCurrent()
            if(c.terminal && !endShown) {endShown=true;handler.post {showEnding(c)}}
        }
        resumedFromBackground=false;lastFrame=0;handler.post(frame);music.resume()
    }
    override fun onPause() {
        saveCurrent();active=false;handler.removeCallbacks(frame);music.pause();resumedFromBackground=true;super.onPause()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);music.release();super.onDestroy() }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun text(value: String,size: Float=14f,color: Int=cream,bold: Boolean=false) = TextView(this).apply {
        text=value;textSize=size;setTextColor(color);includeFontPadding=false
        if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    private fun background(color: Int=panel,stroke: Boolean=false,radius: Float=16f) = GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius.toInt()).toFloat();if(stroke)setStroke(dp(1),0x446f8568)
    }
    private fun button(label: String,filled: Boolean=false,action: ()->Unit) = Button(this).apply {
        text=label;textSize=13f;isAllCaps=false;setTextColor(if(filled)ink else cream)
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        background=background(if(filled)accent else panel,true,12f)
        minHeight=dp(48);minimumHeight=dp(48);minWidth=0;minimumWidth=0
        setPadding(dp(10),dp(8),dp(10),dp(8));setOnClickListener {action()}
        contentDescription=label.replace("\n","、")
    }
    private fun vertical()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun gap(parent: LinearLayout,height: Int) {parent.addView(View(this),LinearLayout.LayoutParams(1,dp(height)))}
    private fun add(parent: LinearLayout,view: View,height: Int=ViewGroup.LayoutParams.WRAP_CONTENT) {
        parent.addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,if(height<0)height else dp(height)))
    }
    private fun weighted(parent: LinearLayout,view: View,height: Int=48,margin: Int=3) {
        parent.addView(view,LinearLayout.LayoutParams(0,dp(height),1f).apply {setMargins(dp(margin),0,dp(margin),0)})
    }
    private fun base(): LinearLayout {
        root.removeAllViews();scene=null;status=null;dateLabel=null;phaseTitle=null;progressText=null;growthText=null;metrics=emptyList();speedButtons.clear();lastHudKey=""
        return vertical().also { root.addView(it,FrameLayout.LayoutParams(-1,-1)) }
    }
    private fun toolbar(title: String,subtitle: String,onBack: ()->Unit): LinearLayout = row().apply {
        setPadding(dp(16),dp(10),dp(16),dp(8))
        addView(button("‹",false,onBack).apply {textSize=26f;contentDescription="巣の一覧に戻る"},LinearLayout.LayoutParams(dp(48),dp(48)))
        val titles=vertical().apply {setPadding(dp(14),0,0,0);addView(text(title,20f,cream,true));gap(this,5);addView(text(subtitle,11f,muted))}
        addView(titles,LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun showHome() {
        page="home";colony=null;draft=null
        base()
        val demo=Colony(Nest.create(),123).apply {phase=Phase.GROWING;day=230.0;adults+=WorkerCohort(48);brood+=Brood(18,12.0);brood+=Brood(15,30.0);brood+=Brood(10,50.0)}
        homeDemo=demo
        val view=NestView(this).apply {colony=demo;showLabels=false}
        scene=view;root.addView(view,0,FrameLayout.LayoutParams(-1,-1))
        val shade=View(this).apply {background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x7a0d1912,0x120d1912,0xef141a17.toInt(),ink))}
        root.addView(shade,FrameLayout.LayoutParams(-1,-1))
        val content=vertical().apply {setPadding(dp(26),dp(30),dp(26),dp(18))}
        root.addView(content,FrameLayout.LayoutParams(-1,-1))
        add(content,text("A R I   /   A COLONY'S STORY",11f,accent,true))
        gap(content,16)
        add(content,text("アリの巣",44f,cream).apply {typeface=Typeface.create("serif",Typeface.NORMAL);letterSpacing=.09f})
        gap(content,9)
        add(content,text("土の下で、命はつづく。",15f,cream))
        gap(content,12)
        add(content,text("巣をつくる。女王を迎える。\nあとは、小さな世界を見守るだけ。",13f,0xffd2d3ba.toInt()).apply {setLineSpacing(dp(5).toFloat(),1f)})
        content.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        add(content,text("観察する巣を選ぶ",12f,accent,true));gap(content,10)
        for(i in 0..2) {
            val state=store.read(i)
            val title=when(state) {
                Slot.Empty -> "0${i+1}     新しい巣をつくる     ＋"
                is Slot.Saved -> "0${i+1}     ${state.colony.phase.label}\n         ${formatDay(state.colony.day)}  ·  働きアリ ${state.colony.workers}匹"
                is Slot.Damaged -> "0${i+1}     保存データの確認が必要"
            }
            val card=button(title,state==Slot.Empty && i==0) {openSlot(i)}.apply {
                gravity=Gravity.CENTER_VERTICAL or Gravity.START;setPadding(dp(18),dp(12),dp(14),dp(12));textSize=14f
                setOnLongClickListener {
                    if(state!=Slot.Empty)confirmDelete(i) else Toast.makeText(this@MainActivity,"まだ巣がありません",Toast.LENGTH_SHORT).show()
                    true
                }
            }
            add(content,card,if(state is Slot.Saved)74 else 58);gap(content,8)
        }
        val footer=row()
        weighted(footer,button("遊び方と生態") {showGuide()},48)
        weighted(footer,musicButton(),48)
        add(content,footer)
        gap(content,8)
        add(content,text("3つの巣を自動保存  ·  保存済みの巣は長押しで削除",10f,muted).apply {gravity=Gravity.CENTER})
    }
    private fun openSlot(index: Int) {
        slot=index
        when(val state=store.read(index)) {
            Slot.Empty -> {draft=Nest.create();editorTool="queen";showEditor()}
            is Slot.Damaged -> dialog(AlertDialog.Builder(this).setTitle("保存データを保護しました").setMessage(state.reason+"\n削除する場合は一覧の枠を長押ししてください。").setPositiveButton("閉じる",null).create())
            is Slot.Saved -> {
                val c=state.colony
                val before=c.day
                c.advanceDays(StateCodec.offlineDays(c,System.currentTimeMillis()))
                colony=c;endShown=c.terminal;showGame();saveCurrent()
                if(c.terminal)showEnding(c)
                if(c.day-before>=.01)Toast.makeText(this,String.format(java.util.Locale.JAPAN,"留守の間に %.1f 時間が経過しました",(c.day-before)*24),Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun confirmDelete(index: Int) {
        dialog(AlertDialog.Builder(this).setTitle("巣 0${index+1} を削除しますか？").setMessage("この巣の成長と観察記録を削除します。他の2つの巣には影響しません。")
            .setNegativeButton("残す",null).setPositiveButton("削除する"){_,_->store.delete(index);showHome()}.create())
    }
    private fun showEditor() {
        page="editor";colony=null
        val nest=draft?:Nest.create().also {draft=it}
        val layout=base()
        add(layout,toolbar("巣をつくる","巣 0${slot+1}  /  はじめの一度だけ",::confirmLeaveEditor))
        val options=vertical().apply {setPadding(dp(14),0,dp(14),dp(8))}
        val shapes=row();Nest.SHAPES.forEachIndexed {index,label -> weighted(shapes,button(label,index==nest.shape){changeBlueprint(index,nest.size)}) }
        add(options,shapes);gap(options,6)
        val sizes=row();Nest.SIZES.forEachIndexed {index,label -> weighted(sizes,button(label,index==nest.size){changeBlueprint(nest.shape,index)}) }
        add(options,sizes);add(layout,options)
        val view=NestView(this).apply {draft=nest;editing=true;tool=editorTool;onEdit={point->editNest(point)}}
        scene=view;layout.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=vertical().apply {setPadding(dp(14),dp(12),dp(14),dp(14));setBackgroundColor(ink)}
        val toolRow=row()
        listOf("room" to "部屋＋","queen" to "女王室","twig" to "小枝","stone" to "小石","erase" to "消す").forEach { (key,label)->
            weighted(toolRow,button(label,editorTool==key){editorTool=key;showEditor()},48,2)
        }
        add(bottom,toolRow);gap(bottom,10)
        status=text(editorHint(),12f,accent).apply {minHeight=dp(36)};add(bottom,status!!)
        progressText=text("収容目安 ${nest.capacity}匹  ·  障害物 ${nest.obstacles.size}/8  ·  移動効率 ${(nest.efficiency*100).toInt()}%",11f,muted)
        add(bottom,progressText!!);gap(bottom,10)
        add(bottom,button("この巣に、女王を迎える",true){startColony()},52)
        gap(bottom,7)
        add(bottom,text("開始後は巣を編集せず、時間を進めて観察します。",10f,muted).apply {gravity=Gravity.CENTER})
        add(layout,bottom)
    }
    private fun editorHint()=when(editorTool){
        "room" -> "土の空いた場所をタップして部屋を追加。通路は自動でつながります。"
        "queen" -> "女王を住まわせる部屋をタップ。奥の部屋ほど外敵から遠くなります。"
        "twig" -> "通路をタップして小枝を配置。外敵も働きアリも通りづらくなります。"
        "stone" -> "通路をタップして小石を配置。小枝より強く、両者の移動を遅くします。"
        else -> "小枝・小石、または端にある部屋をタップして取り除きます。"
    }
    private fun changeBlueprint(shape: Int,size: Int) {
        val apply={draft=Nest.create(shape,size);showEditor()}
        if((draft?.obstacles?.isNotEmpty()==true)||((draft?.chambers?.size?:0)>5)) {
            dialog(AlertDialog.Builder(this).setTitle("巣の形を変更").setMessage("追加した部屋と障害物をリセットして、形・広さを変更します。").setNegativeButton("戻る",null).setPositiveButton("変更する"){_,_->apply()}.create())
        } else apply()
    }
    private fun editNest(point: Point) {
        val n=draft?:return
        val success=when(editorTool) {
            "room" -> n.addRoom(point)
            "queen" -> {
                val room=n.chambers.filter {it.id!=0}.minByOrNull {it.point.distance(point)}
                if(room!=null && room.point.distance(point)<.11){n.queenRoom=room.id;true}else false
            }
            "twig" -> n.putObstacle(point,ObstacleKind.TWIG)
            "stone" -> n.putObstacle(point,ObstacleKind.STONE)
            else -> n.erase(point)
        }
        status?.text=if(success)editorHint()else when(editorTool){"room"->"ほかの部屋から少し離れた土を選んでください（最大20室）。";"queen"->"地中にある部屋の中をタップしてください。";"erase"->"障害物、または女王室以外の端の部屋を選んでください。";else->"空いている通路の中央付近を選んでください（最大8個）。"}
        progressText?.text="収容目安 ${n.capacity}匹  ·  障害物 ${n.obstacles.size}/8  ·  移動効率 ${(n.efficiency*100).toInt()}%"
        scene?.invalidate()
    }
    private fun confirmLeaveEditor() {
        dialog(AlertDialog.Builder(this).setTitle("巣づくりをやめますか？").setMessage("まだ女王を迎えていない設計は保存されません。")
            .setNegativeButton("続ける",null).setPositiveButton("一覧に戻る"){_,_->showHome()}.create())
    }
    private fun startColony() {
        val n=draft?:return
        if(n.path(0,n.queenRoom).isEmpty())return
        colony=Colony(n);endShown=false
        saveCurrent(force=true)
        if(saveError) {status?.text="保存できませんでした。空き容量を確認し、もう一度お試しください。";return}
        draft=null
        showGame()
    }
    private fun showGame() {
        page="game";draft=null
        val c=colony?:return
        val layout=base()
        val top=toolbar("アリの巣","巣 0${slot+1}  /  観察の時間"){saveCurrent();showHome()}
        pauseButton=button(if(c.speed==0)"▶"else"Ⅱ"){
            c.speed=if(c.speed==0)3600 else 0
            updateHud();saveCurrent()
        }.apply {contentDescription="時間の一時停止・再開";textSize=19f}
        top.addView(pauseButton,LinearLayout.LayoutParams(dp(48),dp(48)));add(layout,top)
        val summary=row().apply {setPadding(dp(22),dp(4),dp(22),dp(8))}
        dateLabel=text("",12f,muted);summary.addView(dateLabel,LinearLayout.LayoutParams(0,-2,1f))
        val read=text("観察のみ  ·  自動保存",10f,accent);summary.addView(read);add(layout,summary)
        val metricRow=row().apply {setPadding(dp(16),dp(4),dp(16),dp(12))}
        metrics=listOf("働きアリ","卵・幼虫・蛹","食料","女王の健康").map { label ->
            val box=vertical().apply {gravity=Gravity.CENTER}
            val value=text("—",21f,cream,true);box.addView(value);gap(box,4);box.addView(text(label,10f,muted))
            metricRow.addView(box,LinearLayout.LayoutParams(0,-2,1f));value
        }
        add(layout,metricRow)
        val view=NestView(this).apply {colony=c;onInspect={title,body->info(title,body)}}
        val growth=row().apply {setPadding(dp(20),0,dp(16),dp(6))}
        growthText=text("",11f,accent)
        growth.addView(growthText,LinearLayout.LayoutParams(0,-2,1f))
        growth.addView(button("全体"){view.resetCamera()},LinearLayout.LayoutParams(dp(62),dp(36)))
        add(layout,growth)
        scene=view;layout.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=vertical().apply {setPadding(dp(18),dp(13),dp(18),dp(12));setBackgroundColor(ink)}
        phaseTitle=text("",17f,accent,true);add(bottom,phaseTitle!!);gap(bottom,6)
        status=text("",12f,cream).apply {minHeight=dp(36);setLineSpacing(dp(2).toFloat(),1f)};add(bottom,status!!)
        progressText=text("",10f,muted).apply {minHeight=dp(27)};gap(bottom,5);add(bottom,progressText!!)
        val scroll=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false}
        val speedRow=row()
        Colony.SPEEDS.forEachIndexed {i,speed->
            val b=button(Colony.SPEED_LABELS[i],c.speed==speed){c.speed=speed;updateHud();saveCurrent()}
            speedButtons+=b;speedRow.addView(b,LinearLayout.LayoutParams(dp(if(i<2)58 else 84),dp(46)).apply {rightMargin=dp(6)})
        }
        scroll.addView(speedRow);add(bottom,scroll);gap(bottom,9)
        val actions=row()
        weighted(actions,button("観察記録"){showJournal()},44)
        weighted(actions,button("生態と操作"){showGuide()},44)
        weighted(actions,musicButton(),44)
        add(bottom,actions);add(layout,bottom)
        updateHud()
    }
    private fun updateHud() {
        if(page!="game")return
        val c=colony?:return
        val hudKey="${c.day.toInt()}:${c.temperature.toInt()}:${c.workers}:${c.brood.sumOf{it.count}}:${c.food.toInt()}:${c.queenHealth.toInt()}:${c.phase}:${c.speed}:${c.invader?.kind}:${c.journal.lastOrNull()?.text}:${c.nest.capacity}:${c.nest.construction.sumOf{(it.built*100).toInt()}}:$saveError"
        if(hudKey==lastHudKey)return
        lastHudKey=hudKey
        val date=SeasonCalendar.date(c.day)
        dateLabel?.text="${c.day.toInt()/365+1}年目 ${date.label} · ${if(c.winter)"冬ごもり"else date.season} ${c.temperature.toInt()}℃"
        if(metrics.size==4) {
            metrics[0].text="${c.workers}";metrics[1].text="${c.brood.sumOf{it.count}}";metrics[2].text="${c.food.toInt()}";metrics[3].text="${c.queenHealth.toInt()}%"
        }
        val digging=c.nest.construction
        growthText?.text="巣 ${c.nest.completedRooms}室" + if(digging.isEmpty()) "  ·  群れ ${c.population}匹" else
            "  + ${digging.size}室を掘削中 ${(digging.map{it.built}.average()*100).toInt()}%\n群れ ${c.population}匹  ·  掘削する働きアリ ${c.builders}匹"
        phaseTitle?.text=if(c.invader!=null && !c.terminal)"${c.invader!!.kind.label}が侵入中"else c.phase.label
        status?.text=when {
            saveError -> "保存に失敗しました。空き容量を確認してください。巣はメモリに保持しています。"
            c.speed==0 && !c.terminal -> "時間を止めています。倍率を選ぶと、観察が再開します。"
            c.invader!=null && !c.terminal -> "${c.guards}匹が防衛に集合。小枝と小石が侵入を遅らせています。"
            else -> c.journal.lastOrNull()?.text?:""
        }
        progressText?.text=when {
            c.terminal -> "${if(c.phase==Phase.CLEARED)"旅立った子女王 ${c.youngQueens}匹"else"群れの記録は保存されました"}  ·  撃退 ${c.repelled}回"
            c.phase==Phase.ARRIVAL -> "女王の入巣を観察しています · 最大1時間/秒"
            c.invader!=null || c.phase==Phase.FLIGHT -> "大切な瞬間のため、一時的に最大1時間/秒で観察中"
            c.youngQueens>0 -> "子女王 ${c.youngQueens}匹 · 羽化から12日以上、19℃以上の朝に旅立ちます"
            c.royalLaid -> "子女王を育成中 · 卵から羽化まで温暖時の約80日"
            c.completedFlights>0 -> "母女王の観察を継続中 · 子女王の旅立ち ${c.completedFlights}回 · 創設から${(c.day/365).toInt()}年"
            c.workers==0 -> "最初の働きアリへ · 温暖時の約60日で卵から羽化"
            else -> "巣の収容目安 ${c.nest.capacity}匹 · 成熟の目安3年 / 極端な過密でも繁殖へ"
        }
        pauseButton?.text=if(c.speed==0)"▶"else"Ⅱ"
        speedButtons.forEachIndexed{i,b->
            val selected=c.speed==Colony.SPEEDS[i]
            b.setTextColor(if(selected)ink else cream);b.background=background(if(selected)accent else panel,true,12f)
            b.isEnabled=!c.terminal;b.alpha=if(c.terminal).55f else 1f
        }
        pauseButton?.isEnabled=!c.terminal
    }
    private fun saveCurrent(force: Boolean=false) {
        val c=colony?:return
        if(page!="game" && !force)return
        try {store.write(slot,c);saveError=false} catch(_: Exception) {saveError=true}
    }
    private fun showJournal() {
        val c=colony?:return
        info("巣 0${slot+1} の観察記録",c.journal.asReversed().joinToString("\n\n"){"${formatDay(it.day)}\n${it.text}"})
    }
    private fun showEnding(c: Colony) {
        val cleared=c.phase==Phase.CLEARED
        val title=when {cleared->"命は、次の巣へ。";c.queenDiedOfAge->"女王の一生が終わりました。";else->"巣の灯が消えました。"}
        val body=if(cleared)
            "第${c.completedFlights.coerceAtLeast(1)}回の旅立ち。${formatDay(c.day)}。\n${c.youngQueens}匹の子女王が、翅を広げて旅立ちました。\n\n母女王は元の巣に残ります。同じ巣で観察を続け、次の子女王の誕生と旅立ちを見守れます。\n\n働きアリ ${c.workers}匹 / 撃退 ${c.repelled}回\nこの巣の記録は保存されています。"
        else "${c.journal.lastOrNull()?.text}\n\n${formatDay(c.day)} / 撃退 ${c.repelled}回\n\nこの巣の記録は保存されています。新しい巣をつくり、次の女王を迎えられます。"
        // Stack the two full Japanese choices so neither label is clipped on a narrow screen.
        val content=vertical().apply {setPadding(dp(24),dp(12),dp(24),dp(20))}
        add(content,text(body+"\n\n新しい巣で女王を迎えると、この保存枠を置き換えます。",14f,cream).apply {setLineSpacing(dp(4).toFloat(),1f)})
        gap(content,18)
        lateinit var alert:AlertDialog
        add(content,button("新たな女王アリを迎える巣を作る",!cleared){
            alert.dismiss();draft=Nest.create();editorTool="queen";showEditor()
        }.apply {setPadding(dp(12),dp(12),dp(12),dp(12));minHeight=dp(54)})
        if(cleared && c.queenHealth>0) {
            gap(content,10)
            add(content,button("現在の女王アリのその後を観察する",true){
                if(c.continueObservation()) {
                    alert.dismiss();endShown=false;lastFrame=0
                    showGame();saveCurrent(force=true)
                }
            }.apply {setPadding(dp(12),dp(12),dp(12),dp(12));minHeight=dp(54)})
        }
        val scroll=ScrollView(this).apply {addView(content)}
        alert=AlertDialog.Builder(this).setTitle(title).setView(scroll).create()
        dialog(alert)
    }
    private fun showGuide() {
        info("この小さな世界について", "【遊び方】\nはじめに巣の形・広さを選び、部屋を追加し、女王室を決めます。通路に小枝や小石を置くと、外敵と働きアリの両方の足が遅くなります。障害物は最大8個。開始後の巣はアリたちに任せます。\n\n【観察の操作】\n1倍は現実と同じ時間です。60倍、1時間/秒、1日/秒、7日/秒で早送りできます。女王の到着・外敵の襲来・子女王の旅立ちは最大1時間/秒に自動で減速します。Ⅱで一時停止。ピンチで全体表示から部屋やアリの細部まで拡大、ドラッグで移動。「全体」で巣の端まで見渡せます。部屋をタップすると詳しく観察できます。\n\n【季節と暦】\n5月1日に始まり、ゲーム内の月が変わると12種類の林床の背景が切り替わります。芽吹き、新緑、梅雨、紅葉、落ち葉、霜や雪を観察できます。暦はうるう年のない365日周期です。画面の年目は巣の創設から数えます。端末の実際の月ではなく、早送りした巣の時間に連動します。\n\n【3つの巣と留守の間】\n10秒ごととアプリを閉じるときに自動保存。再開時には現実に経過した時間（最大30日）が進みます。選んだ早送り倍率は留守中に掛かりません。一時停止した巣は留守中も止まります。保存枠は一覧で長押しすると削除できます。\n\n【生態のモデル】\n温帯のアリを参考にした、特定種を断定しないモデルです。女王は産卵し、働きアリは育児・採餌・掘削・防衛を行います。創設時の女王は体内の蓄えで子を育てます。卵18日→幼虫22日→蛹20日（温暖時の基準）で成虫へ。寒い季節には活動と発育が遅くなります。\n\n【群れと巣の成長】\n群れが増えると働きアリが新しい通路を掘り、その先を育児室・貯蔵室・休息室へ広げます。工事の進捗は断面と上部に表示されます。完成した部屋へ幼体・食料・働きアリが分散し、部屋数の固定上限を設けず、群れの需要に応じて地中へ拡大します。縦坑はゆるく曲がり、枝分かれと不均一な間隔で横長の部屋を増やします。混み合った部屋も少しずつ広げます。冬や外敵への防衛中は掘削も遅くなります。部屋の配置と用途は観察用の簡略モデルです。\n\n【次の世代へ】\n創設3年、または1年以上かつ収容目安の155%を超える過密で、80匹以上の働きアリと食料があれば子女王を育て始めます。子女王は基準80日で羽化し、12日以上の成熟期間と暖かな日を待って雄と旅立ちます。子女王の旅立ちでクリアです。「現在の女王アリのその後を観察する」を選ぶと、巣と群れをそのまま引き継いで時間が進みます。子女王を育て始める間隔は最低365日とし、暖かな季節と食料・働きアリの条件を待って次の繁殖へ進みます。旅立つたびに、続けて観察するか新しい巣をつくるかを選べます。\n\nここでの「女王の交代」は新しい巣への世代継承を指します。母女王を子女王が必ず置き換えるという生態ではありません。母女王は元の巣に残ります。長寿の生態を参考に、本作では創設から10〜20年の範囲で女王ごとの寿命を設定します。これはゲーム用の個体差で、野外の寿命分布を再現したものではありません。捕食・飢餓・寿命で女王が死ぬと観察は終了します。\n\n【再現の範囲】\n卵の性・カースト分化、栄養、季節、侵入虫との戦いは簡略化しています。日数・寿命・成長率・過密による繁殖・襲撃周期・小枝と小石の効果はゲーム用の設定で、実測値ではありません。画面の働きアリは最大240匹の代表表示で、全個体数は上部の数字に表示します。歩行は観察しやすい速さで、発育の時間とは別に表示しています。\n\n【参考資料】\n横浜市衛生研究所「羽アリ」と「アリの結婚飛行」（女王の長寿・季節の繁殖）\nhttps://www.city.yokohama.lg.jp/kenko-iryo-fukushi/kenko-iryo/eiken/geppo/2011/1104.files/110401.pdf\n\nArizona State University, Life Cycle of an Ant Colony\nhttps://askabiologist.asu.edu/ant-colony-life-cycle\n\nNational Park Service, Carpenter Ant\nhttps://www.nps.gov/articles/carpenter-ant.htm\n\nRoces & Núñez, 1989, Brood translocation and circadian variation of temperature preference in the ant Camponotus mus\nhttps://pubmed.ncbi.nlm.nih.gov/28312153/\n\nRajendran et al., 2026, Colony demographics shape nest construction in Camponotus fellah ants\nhttps://pubmed.ncbi.nlm.nih.gov/42017334/\n\n外敵の形態：University of Minnesota Extension / Rove beetle・Ground beetles、UC IPM / Earwigs\nhttps://ipm.ucanr.edu/home-and-landscape/earwigs/\n\n【巣の形の参考モデル】\nGuimarães, Pereira, Batista, Rodrigues & Antonialli Junior (2018): The complex nest architecture of the Ponerinae ant Odontomachus chelifer\nhttps://doi.org/10.1371/journal.pone.0189896\n\nBatista, Oliveira & Antonialli-Junior (2021): A 3D model to illustrate the nest architecture of Acromyrmex balzani\nhttps://doi.org/10.1590/1806-9665-RBENT-2021-0037\n\n両資料はCC BY 4.0（商用利用・改変可）。\nhttps://creativecommons.org/licenses/by/4.0/\n\n公開された3D復元と巣の構造を参考に、ゲーム用の2D断面と成長規則へ変更した独自モデルです。原図や3Dファイルそのものは同梱していません。研究で観察された室数・深さを無限に伸ばせるという意味ではなく、上限を設けない増築は本作のゲーム仕様です。\n\n【音と絵】\n森の土の背景はこの作品のために生成したアートです。生物・巣・障害物は動的描画。アリはクロオオアリなどのオオアリ属の形態を参考に、6本の関節脚、腹柄、複眼、大顎、節のある腹部と触角、女王の2対の翅を描いています。歩行は左右交互の3本組を基本とし、障害物で移動が遅くなると歩調も遅くなります。外敵はハネカクシの短い上翅と露出した腹節、ハサミムシの尾のはさみ、オサムシの上翅の筋を描き分け、6本の関節脚、糸状触角、大顎を持ちます。外敵の歩調も移動距離に連動します。種の完全な形態復元や実測歩行の再現ではありません。BGM「土の下の午後」は独自のシンセサイザーで制作した穏やかなアンビエント曲です。広告・課金・通信・収集する個人情報はありません。")
    }
    private fun musicButton(): Button = button(if(music.enabled)"BGM ON"else"BGM OFF") {}.apply {
        setOnClickListener {music.setEnabled(!music.enabled);text=if(music.enabled)"BGM ON"else"BGM OFF";contentDescription=text}
    }
    private fun info(title: String,body: String,positiveText: String="観察に戻る",onPositive: (() -> Unit)?=null) {
        val scroll=ScrollView(this)
        val content=text(body,14f,cream).apply {setPadding(dp(24),dp(14),dp(24),dp(20));setLineSpacing(dp(5).toFloat(),1.05f);setTextIsSelectable(true);autoLinkMask=android.text.util.Linkify.WEB_URLS}
        scroll.addView(content)
        dialog(AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton(positiveText){_,_->onPositive?.invoke()}.create())
    }
    private fun dialog(alert: AlertDialog) {
        dialogCount++
        alert.setOnDismissListener {dialogCount=(dialogCount-1).coerceAtLeast(0);lastFrame=0}
        alert.show();alert.window?.setBackgroundDrawable(background(ink,true,20f))
        alert.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
    }
    private fun formatDay(day: Double): String = "${day.toInt()/365+1}年目・${day.toInt()%365+1}日"
}
