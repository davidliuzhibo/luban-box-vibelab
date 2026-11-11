package com.vrgames.lubanbox.core

enum class RelicKind { BURR, KLOTSKI, BOX, ASSEMBLY, ROTARY }

/**
 * One mechanism on the bench. [rounds] > 1 means several boards in a row (华容道). [token] is what
 * the player gets for opening it; [story] and [facts] fill the 营造图谱 window once it is solved
 * (only commonly accepted history; legends are said to be legends).
 */
class Relic(
    val id: String,
    val chapter: Int,
    val title: String,
    val kind: RelicKind,
    val goal: String,
    val token: String,
    /** The first rung of the hint ladder: how to think about it, said aloud (narration/idea_<id>.ogg). */
    val idea: String,
    val story: String,
    val facts: List<String>,
    val rounds: Int = 1,
)

object Relics {
    val chapterTitles = mapOf(1 to "第一章 · 鲁班工坊", 2 to "第二章 · 铜作", 3 to "第三章 · 天工", 4 to "终章 · 总机关匣")

    /** What each chapter's tokens make, once all are found. */
    val chapterRewards = mapOf(1 to "鲁班尺", 2 to "铜印「天工开物」", 3 to "三垣星图")

    val all = listOf(
        Relic(
            id = "box",
            chapter = 1,
            title = "鲁班盒",
            kind = RelicKind.BOX,
            goal = "盒子上没有锁眼。按对的顺序推动盒壁上的木板，盖子才会松开。",
            token = "鲁班尺 · 第一节",
            idea = "盒壁上的木板一开始只有一块能动。先把它推开，再看哪一块跟着松动了。",
            story = "机关盒的木板里藏着暗销：一块板挪开一点，另一块板才能动。只有知道顺序的人，才能把它打开。",
            facts = listOf(
                "机关盒不靠锁和钥匙，靠的是暗藏的销子和槽口，顺序就是它的「钥匙」。",
                "中国古代的妆奁、文具盒、药箱里，常做暗格和机关，用来收藏贵重的东西。",
            ),
        ),
        Relic(
            id = "dougong",
            chapter = 1,
            title = "斗拱",
            kind = RelicKind.ASSEMBLY,
            goal = "按营造的顺序，从下往上把十三个构件拼成一朵斗拱。",
            token = "鲁班尺 · 第二节",
            idea = "斗拱从下往上搭：最下面是方方的坐斗，上面十字交叉放两根栱，再往上放小斗。先找最大的那个斗。",
            story = "斗拱是中国古建筑檐下层层出挑的木构件：方形的「斗」和弓形的「栱」交替叠起，把屋檐的重量一层层传到柱子上，让屋檐伸得又远又稳。",
            facts = listOf(
                "北宋李诫编修的《营造法式》（1103 年刊行）用「材」作为度量单位，规定材的高宽比为 15 比 10。这件斗拱的构件也按这个比例制作。",
                "山西应县木塔（辽代，1056 年建成）用了 54 种不同的斗拱，被称为「斗拱博物馆」。",
                "斗拱的构件之间全靠榫卯和自身的重量咬合，地震时能微微错动、吸收震动。",
            ),
        ),
        Relic(
            id = "klotski",
            chapter = 1,
            title = "华容道",
            kind = RelicKind.KLOTSKI,
            goal = "推动木块，给曹操让出一条路，让他从下方的出口离开。共三局。",
            token = "鲁班尺 · 第三节",
            idea = "曹操最大，出口要留出两格宽的路。先把挡在他下面的小兵挪到两边去。",
            story = "故事出自《三国演义》：赤壁之战后，曹操败走华容道，关羽念旧日恩义，放他过去。棋盘上，曹操被关羽、张飞、赵云、马超、黄忠和小兵团团围住。",
            facts = listOf(
                "华容道属于滑块类智力游戏。世界各地都有同类的滑块游戏，中国的这一版用三国故事给木块起了名字。",
                "最经典的布局叫「横刀立马」，最少要 81 步才能让曹操出关。",
                "这一局只能平推，木块不能拿起来，也不能转向，所以每一步都要给后面的木块留出空位。",
            ),
            rounds = 3,
        ),
        Relic(
            id = "lock",
            chapter = 1,
            title = "鲁班锁",
            kind = RelicKind.BURR,
            goal = "六根木条互相卡住，不用一颗钉子。找到能动的那一根，把它们一根根拆下来。",
            token = "鲁班尺 · 第四节",
            idea = "六根木条里，一开始只有一根能动。挨个推一推，听到闷响就换一根；能动的那根推到底，别的才会松开。",
            story = "鲁班锁也叫孔明锁。六根刻了榫口的木条十字交叉，彼此咬合，拆开以后再想装回去，往往比拆还难。相传是鲁班为了考校儿子的手艺而做。",
            facts = listOf(
                "鲁班即公输班，春秋时期鲁国的工匠，被后世木匠尊为祖师。鲁班锁出自他之手只是民间传说。",
                "鲁班锁靠榫卯咬合，不用钉子和胶。榫卯也是中国传统木构建筑的根本做法。",
                "六根木条的鲁班锁有成千上万种榫口组合，难的要先推动好几根木条，才能取出第一根。",
            ),
        ),
        Relic(
            id = "wordlock",
            chapter = 2,
            title = "文字锁",
            kind = RelicKind.ROTARY,
            goal = "转动四个铜环，在锁窗里排出「天工开物」。铜环之间有齿轮相连，转一个，旁边的也会跟着动。",
            token = "铜印 · 天",
            idea = "铜环之间有齿轮，转一个，旁边的也会跟着转。先把一个环对好，再看转别的环时它会不会被带歪。",
            story = "文字锁不用钥匙：锁身上的铜环刻着汉字，转到约定的字排成一行，锁才会打开，是一种用汉字做密码的锁。",
            facts = listOf(
                "锁上要排出的「天工开物」，是明代宋应星所著的书名。书在 1637 年刊行，记录了冶铸、舟车、锤锻、陶埏等一百多种工艺，被外国学者称为「中国 17 世纪的工艺百科全书」。",
                "这把锁的铜环之间用齿轮相连，是为了游戏设计的复原演绎；古代文字锁的铜环一般各自转动。",
            ),
        ),
        Relic(
            id = "padlock",
            chapter = 2,
            title = "花旗锁",
            kind = RelicKind.BOX,
            goal = "这把铜锁找不到锁孔。先找到藏起来的锁孔，再把钥匙送进去，打开锁梁。",
            token = "铜印 · 工",
            idea = "锁孔藏起来了。转一转锁看看背面，找一块能滑开的铜片；钥匙插到一半，再按侧面的簧。",
            story = "花旗锁是中国传统的簧片锁：钥匙插进锁孔，压紧锁里撑开的铜簧片，锁梁才能拔出来。锁身常做成如意、鱼、元宝等吉祥的样子。",
            facts = listOf(
                "簧片锁在中国使用了两千多年，钥匙的形状要和锁孔、簧片的排布对上，才能把簧片压到一起。",
                "这把锁的暗藏锁孔和侧簧是复原演绎：古人确实喜欢在锁上做暗孔、暗簧，增加开锁的难度。",
            ),
        ),
        Relic(
            id = "censer",
            chapter = 2,
            title = "被中香炉",
            kind = RelicKind.ASSEMBLY,
            goal = "从外往里装好球壳、两层圆环和香盂。装好以后，不管香炉怎么滚，香盂都会保持水平。",
            token = "铜印 · 开",
            idea = "从外往里装：先放球壳，再装大圆环、小圆环，最后挂上香盂。",
            story = "被中香炉是放在被褥里熏香的小香炉：球形外壳里套着两层能转动的圆环，最里面挂着香盂。香炉怎么滚动，香盂都朝上，香灰不会洒出来。",
            facts = listOf(
                "《西京杂记》记载，西汉长安的巧匠丁缓做过这种「被中香炉」。",
                "陕西扶风法门寺地宫出土的唐代鎏金银香囊就是这种结构。",
                "它的原理和今天陀螺仪、相机稳定器里的万向支架相同。",
            ),
        ),
        Relic(
            id = "mirror",
            chapter = 2,
            title = "透光镜",
            kind = RelicKind.ROTARY,
            goal = "转动铜镜，把灯光反射到屏风上。光斑里会出现铜镜背面的字。",
            token = "铜印 · 物",
            idea = "转动镜子，看屏风上的光斑往哪边跑，一点一点把它赶到屏风中间。",
            story = "透光镜是一种奇特的铜镜：把阳光反射到墙上，光斑里竟然显出镜背的花纹和铭文，好像光透过了铜镜。",
            facts = listOf(
                "上海博物馆藏有一面西汉透光镜，镜背铭文是「见日之光，天下大明」。",
                "现代研究认为，镜面在铸造和研磨时，留下了与背面花纹对应的极细微起伏，反射时就把花纹「画」进了光斑。",
            ),
        ),
        Relic(
            id = "chariot",
            chapter = 3,
            title = "指南车",
            kind = RelicKind.ASSEMBLY,
            goal = "给指南车装上齿轮和木人。装对了，车子怎么转，木人都指着南方。台上有一个齿轮是多余的。",
            token = "星图 · 紫微垣",
            idea = "先装连着车轮的齿轮，再装中间传动的，最后放上木人。台上有一个齿轮哪儿都装不上。",
            story = "指南车不用磁石：车轮转弯时，车里的齿轮把两只轮子转动的差别传给木人，让它反方向转回来，所以木人一直指着出发时的方向。",
            facts = listOf(
                "《宋史·舆服志》记载，北宋燕肃在 1027 年、吴德仁在 1107 年都造过指南车，还写下了各个齿轮的齿数。",
                "指南车只靠齿轮，不靠磁石；它和指南针是两种完全不同的发明。",
            ),
        ),
        Relic(
            id = "armillary",
            chapter = 3,
            title = "浑天仪",
            kind = RelicKind.ROTARY,
            goal = "转动四游环、俯仰窥管，用窥管对准织女星。越接近，管口越亮。",
            token = "星图 · 太微垣",
            idea = "先转四游环，把窥管转到织女星那一边，再上下俯仰。管口越亮，就越接近。",
            story = "浑仪是观测星星位置的仪器：几层圆环套在一起，转动其中的四游环、俯仰窥管，对准一颗星，就能从环上的刻度读出它的位置。",
            facts = listOf(
                "北宋苏颂、韩公廉主持建造的水运仪象台，顶层就安放着一架浑仪。",
                "这架浑天仪的极轴按北宋都城开封的纬度（约北纬 35 度）倾斜。",
                "织女星是天琴座最亮的星，也是牛郎织女故事里的「织女」。",
            ),
        ),
        Relic(
            id = "waterclock",
            chapter = 3,
            title = "水运仪象台",
            kind = RelicKind.ASSEMBLY,
            goal = "按顺序装好水运仪象台的擒纵机构：枢轮、天池、天衡、天关、左右天锁，最后放上浑象，让它一格一格地走起来。",
            token = "星图 · 天市垣",
            idea = "照着水流的路线装：先枢轮，再天池，然后天衡、天关、左右天锁，最后放浑象。",
            story = "水流注满枢轮上的一只水斗，重量压下天衡，天关抬起、天锁松开，枢轮就转过一格，再被天锁挡住。这样一格一格地走，带动浑象和报时装置。",
            facts = listOf(
                "北宋元祐年间，苏颂、韩公廉在开封建成水运仪象台，从 1088 年开始建造，高约 12 米，集观测、演示、报时于一体。",
                "苏颂在《新仪象法要》中记录了它的结构和图样。",
                "其中控制枢轮一格一格转动的机构，被许多科学史学者看作钟表擒纵机构的先驱。",
            ),
        ),
        Relic(
            id = "chest",
            chapter = 4,
            title = "总机关匣",
            kind = RelicKind.ASSEMBLY,
            goal = "把鲁班尺、铜印、三垣星图放进总机关匣的三个凹槽。",
            token = "天工",
            idea = "三件信物各有各的凹槽，看形状对上哪一个。",
            story = "三件信物合在一起，总机关匣终于打开。木作、铜作、天工，古代工匠的巧思都在这里。",
            facts = listOf(
                "本作中的器物都是根据史料的复原演绎：鲁班锁、华容道、斗拱、文字锁、花旗锁、被中香炉、透光镜、指南车、浑天仪、水运仪象台。",
            ),
        ),
    )

    fun byId(id: String) = all.first { it.id == id }

    fun chapter(c: Int) = all.filter { it.chapter == c }
}
