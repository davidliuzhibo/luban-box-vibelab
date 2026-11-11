package com.vrgames.lubanbox.core

/**
 * Saved progress: opened relics, those opened without a single hint (巧手), and those skipped.
 * A skipped relic counts as done for what opens next (the story goes on, its token is given), and
 * stays marked until the player comes back and opens it for real.
 */
class Progress(solved: Set<String> = emptySet(), unaided: Set<String> = emptySet(), skipped: Set<String> = emptySet()) {
    private val solved = solved.toMutableSet()
    private val unaided = unaided.toMutableSet()
    private val skipped = skipped.toMutableSet()

    /** Done: opened or skipped. */
    fun solved(id: String) = id in solved || id in skipped
    /** Opened by the player's own hands (with or without hints). */
    fun opened(id: String) = id in solved
    fun unaided(id: String) = id in unaided
    fun skipped(id: String) = id in skipped && id !in solved
    val tokens get() = Relics.all.filter { solved(it.id) }.map { it.token }

    /** Chapters 1–3 are done in turn; the closing chest needs all three treasures. */
    fun chapterOpen(c: Int) = c == 1 || chapterDone(c - 1) && (c < 4 || (1..3).all(::chapterDone))

    /** A relic opens once the one before it is done (the chest once chapters 1–3 are all done). */
    fun open(r: Relic): Boolean {
        val i = Relics.all.indexOf(r)
        if (r.chapter == 4) return chapterOpen(4)
        return i == 0 || solved(Relics.all[i - 1].id)
    }

    fun chapterDone(chapter: Int) = Relics.all.filter { it.chapter == chapter }.all { solved(it.id) }

    fun record(id: String, hinted: Boolean) {
        solved += id
        skipped -= id
        if (!hinted) unaided += id
    }

    fun skip(id: String) {
        if (id !in solved) skipped += id
    }

    fun serialize() = Relics.all.filter { solved(it.id) }.joinToString(",") {
        when {
            it.id in unaided -> "${it.id}*"
            it.id in solved -> it.id
            else -> "${it.id}~"
        }
    }

    companion object {
        fun parse(s: String?): Progress {
            val ids = Relics.all.map { it.id }.toSet()
            val items = s.orEmpty().split(',').map { it.trim() }.filter { it.trimEnd('*', '~') in ids }
            return Progress(
                items.filter { !it.endsWith("~") }.map { it.trimEnd('*') }.toSet(),
                items.filter { it.endsWith("*") }.map { it.trimEnd('*') }.toSet(),
                items.filter { it.endsWith("~") }.map { it.trimEnd('~') }.toSet(),
            )
        }
    }
}

/**
 * The bench: which relic is out, its mechanism, and the hint ladder. Pure rules; the stage and
 * windows read it under a lock.
 */
class Workshop(val progress: Progress = Progress()) {
    var relic: Relic = Relics.all.first(); private set
    /** Board number for relics with several rounds (华容道), from 0. */
    var round = 0; private set
    /** The mechanism being worked on: sliding pieces, turning dials, or loose parts to build. */
    var slide: SlidePuzzle? = null; private set
    var rotary: RotaryPuzzle? = null; private set
    var assembly: Assembly? = null; private set
    var moves = 0; private set
    /** Hints asked for on this relic (a relic solved without any counts as 巧手). */
    var hintsUsed = 0; private set
    /** Bumps whenever the mechanism is replaced (new relic or next round): the stage rebuilds. */
    var version = 0; private set

    init {
        load((Relics.all.firstOrNull { !progress.solved(it.id) } ?: Relics.all.last()).id)
    }

    fun load(id: String, round: Int = 0) {
        relic = Relics.byId(id)
        this.round = round
        moves = 0
        if (round == 0) {
            hintsUsed = 0
            ideaGiven = false
        }
        lastHint = null
        slide = null
        rotary = null
        assembly = null
        when (id) {
            "lock" -> slide = Burrs.lock()
            "klotski" -> slide = Klotski(KlotskiBoards.rounds[round])
            "box" -> slide = Boxes.newBox()
            "dougong" -> assembly = Dougong.newAssembly()
            "wordlock" -> rotary = Bronze.wordLock()
            "padlock" -> slide = Bronze.padlock()
            "censer" -> assembly = Bronze.censer()
            "mirror" -> rotary = Bronze.mirror()
            "chariot" -> assembly = Celestial.chariot()
            "armillary" -> rotary = Celestial.armillary()
            "waterclock" -> assembly = Celestial.waterClock()
            "chest" -> assembly = Celestial.chest()
            else -> error("no mechanism for $id")
        }
        version++
    }

    val solved get() = slide?.solved ?: rotary?.solved ?: assembly?.solved ?: false

    /** Back to the start of the current round. */
    fun restart() = load(relic.id, round)

    /**
     * After the player changed the mechanism: counts the move, and when the round is solved either
     * sets up the next round or records the relic. Returns what happened.
     */
    fun afterMove(): Outcome {
        moves++
        if (!solved) return Outcome.MOVED
        if (round + 1 < relic.rounds) {
            load(relic.id, round + 1)
            return Outcome.NEXT_ROUND
        }
        val first = !progress.solved(relic.id)
        progress.record(relic.id, hinted = hintsUsed > 0)
        return if (first) Outcome.OPENED else Outcome.OPENED_AGAIN
    }

    enum class Outcome { MOVED, NEXT_ROUND, OPENED, OPENED_AGAIN }

    /** 「跳过这件」: the relic counts as done (marked skipped) and the next one opens. */
    fun skip() = progress.skip(relic.id)

    /** The first rung of the hint ladder has been given on this relic: the next ask shows the piece. */
    var ideaGiven = false; private set

    /** First rung: the way to think about this relic. Counts as a hint. */
    fun idea(): String {
        hintsUsed++
        ideaGiven = true
        return relic.idea
    }

    /** 「演示一步」: the solver is about to make a move for the player. Counts as a hint. */
    fun usedDemo() {
        hintsUsed++
        ideaGiven = true
        lastHint = null
    }

    /** The relic after this one, if it is open. */
    fun next(): Relic? = Relics.all.getOrNull(Relics.all.indexOf(relic) + 1)?.takeIf(progress::open)

    /**
     * The next step of the hint ladder. The first ask names the piece to move next; asking again
     * for the same position also shows where it goes.
     */
    fun hint(): Hint? {
        hintsUsed++
        slide?.let { p ->
            val m = p.solution()?.firstOrNull() ?: return null
            val show = lastHint?.let { it.move == m } ?: false
            return Hint(m, null, showTarget = show).also { lastHint = it }
        }
        rotary?.let { r ->
            val (dial, dir) = r.solution()?.firstOrNull() ?: return null
            val m = Move(dial, r.dials[dial].axis, dir)
            val show = lastHint?.let { it.move == m } ?: false
            return Hint(m, null, showTarget = show).also { lastHint = it }
        }
        assembly?.let { a ->
            val (part, slot) = a.hint() ?: return null
            val show = lastHint?.let { it.part == part } ?: false
            return Hint(null, part, slot, showTarget = show).also { lastHint = it }
        }
        return null
    }

    private var lastHint: Hint? = null

    /**
     * A piece to move next ([move]; for a dial, [Move.to] is the direction to turn, +1 or -1) or a
     * part and where it goes ([part], [slot]).
     */
    data class Hint(val move: Move?, val part: Int? = null, val slot: Slot? = null, val showTarget: Boolean)
}
