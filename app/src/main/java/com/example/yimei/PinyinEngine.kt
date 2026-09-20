package com.example.yimei

import android.content.Context

data class Cand(val text: String, val consumed: Int)

/**
 * 全拼输入引擎。词库 assets/dict.txt：「c 拼音 汉字…」（单字，按常用度排序）与「w 词语 拼音」。
 * 支持整句连打（woxiangzuoshuangyanpi → 我想做双眼皮）、部分上屏、前缀补全；ü 用 v 输入。
 */
class PinyinEngine(ctx: Context) {
    private class Entry(val t: String, val r: Int, val w: Boolean, val idx: Int)
    private val map = HashMap<String, MutableList<Entry>>()
    private val keys: List<String>

    init {
        val seenKey = HashSet<String>()
        var n = 0; var idx = 0
        ctx.assets.open("dict.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                val p = line.trim().split(Regex("\\s+"))
                if (p.size < 3) continue
                if (p[0] == "w") {
                    map.getOrPut(p[2]) { mutableListOf() }.add(Entry(p[1], n++, true, idx++))
                } else if (p[0] == "c") {
                    val base = if (p[1] in seenKey) 500 else 0
                    seenKey.add(p[1])
                    var i = 0
                    for (ch in p[2]) {
                        map.getOrPut(p[1]) { mutableListOf() }.add(Entry(ch.toString(), 100000 + (base + i) * 10, false, idx++))
                        i++
                    }
                }
            }
        }
        for (v in map.values) v.sortWith(compareBy<Entry>({ it.r }, { it.idx }))
        keys = map.keys.sorted()
    }

    private fun top(key: String, count: Int): List<String> {
        val out = ArrayList<String>()
        for (e in map[key] ?: return out) {
            if (e.t !in out) out.add(e.t)
            if (out.size >= count) break
        }
        return out
    }

    private fun lowerBound(k: String): Int {
        var lo = 0; var hi = keys.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (keys[m] < k) lo = m + 1 else hi = m }
        return lo
    }

    private class Seg(val cov: Int, val pieces: List<Int>)

    private fun segment(b: String): Seg {
        val memo = HashMap<Int, Seg>()
        fun go(i: Int): Seg {
            if (i >= b.length) return Seg(0, emptyList())
            memo[i]?.let { return it }
            var best = Seg(0, emptyList())
            val maxL = minOf(b.length - i, 24)
            for (L in maxL downTo 1) {
                if (!map.containsKey(b.substring(i, i + L))) continue
                val r = go(i + L)
                val cov = L + r.cov
                val pieces = listOf(L) + r.pieces
                if (cov > best.cov || (cov == best.cov && pieces.size < best.pieces.size)) best = Seg(cov, pieces)
            }
            memo[i] = best
            return best
        }
        return go(0)
    }

    private fun composed(b: String, pieces: List<Int>): String {
        var i = 0; val sb = StringBuilder()
        for (L in pieces) { sb.append(top(b.substring(i, i + L), 1).firstOrNull() ?: ""); i += L }
        return sb.toString()
    }

    fun candidates(buf: String): List<Cand> {
        if (buf.isEmpty()) return emptyList()
        val out = ArrayList<Cand>(); val seen = HashSet<String>()
        fun push(t: String, c: Int) { if (seen.add("$t|$c")) out.add(Cand(t, c)) }
        val seg = segment(buf)
        val full = seg.cov == buf.length

        if (map.containsKey(buf)) top(buf, 20).forEach { push(it, buf.length) }
        if (seg.pieces.size > 1) push(composed(buf, seg.pieces), if (full) buf.length else seg.cov)

        val partial = ArrayList<Pair<String, Int>>()
        var shown = 0
        var L = buf.length - 1
        while (L >= 1 && shown < 3) {
            val k = buf.substring(0, L)
            if (map.containsKey(k)) { top(k, 5).forEach { partial.add(it to L) }; shown++ }
            L--
        }

        val comp = ArrayList<Entry>()
        var i = lowerBound(buf)
        while (i < keys.size && keys[i].startsWith(buf)) {
            if (keys[i] != buf) comp.addAll(map[keys[i]]!!)
            i++
        }
        comp.sortWith(compareBy<Entry>({ it.r }, { it.idx }))
        if (buf.length >= 4) comp.filter { it.w }.take(3).forEach { push(it.t, buf.length) }
        partial.forEach { push(it.first, it.second) }
        comp.filter { !it.w }.take(8).forEach { push(it.t, buf.length) }
        return out
    }
}
