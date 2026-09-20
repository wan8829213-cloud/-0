package com.example.yimei

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Project(
    val name: String, val kw: List<String>, val points: List<String>,
    val options: String, val recovery: String, val risks: String, val ask: String
)
data class IntentInfo(val key: String, val label: String, val kw: List<String>)
data class Quick(val k: String, val text: String)
data class Detection(val intent: IntentInfo, val project: Project?)

enum class Tone(val title: String, val greeting: String, val ending: String) {
    PRO("专业", "您好，", ""), WARM("亲切", "亲，", " 😊"), BRIEF("简洁", "", "");
    val apiName get() = name.lowercase()
}

/** 本地话术引擎：对应小程序 scripts.js 的 detect / buildReplies。 */
object ReplyEngine {
    private var projects = listOf<Project>()
    private var generic = Project("这个项目", listOf(), listOf(), "", "", "", "")
    private var intents = listOf<IntentInfo>()
    var quick = listOf<Quick>()
        private set
    private val general = IntentInfo("general", "一般咨询", listOf())
    private const val INVITE = "建议约一次医生面诊，面诊只是评估，不代表一定要做。您这周哪天方便？"

    private fun JSONArray?.strs(): List<String> = if (this == null) listOf() else (0 until length()).map { getString(it) }
    private fun proj(o: JSONObject) = Project(
        o.getString("name"), o.optJSONArray("kw").strs(), o.getJSONArray("points").strs(),
        o.getString("options"), o.getString("recovery"), o.getString("risks"), o.getString("ask")
    )

    fun load(ctx: Context) {
        if (quick.isNotEmpty()) return
        val text = ctx.assets.open("script_data.json").bufferedReader().use { it.readText() }
        val o = JSONObject(text)
        projects = (0 until o.getJSONArray("projects").length()).map { proj(o.getJSONArray("projects").getJSONObject(it)) }
        generic = proj(o.getJSONObject("generic"))
        intents = (0 until o.getJSONArray("intents").length()).map {
            val i = o.getJSONArray("intents").getJSONObject(it)
            IntentInfo(i.getString("key"), i.getString("label"), i.optJSONArray("kw").strs())
        }
        quick = (0 until o.getJSONArray("quick").length()).map {
            val q = o.getJSONArray("quick").getJSONObject(it); Quick(q.getString("k"), q.getString("text"))
        }
    }

    fun detect(msg: String): Detection {
        val intent = intents.firstOrNull { i -> i.kw.any { msg.contains(it) } } ?: general
        val project = projects.firstOrNull { p -> p.kw.any { msg.contains(it) } }
        return Detection(intent, project)
    }

    fun buildReplies(msg: String, d: Detection, tone: Tone): List<String> {
        val p = d.project ?: generic
        val n = if (tone == Tone.BRIEF) 2 else p.points.size
        val pts = p.points.take(n).joinToString("、")
        val g = tone.greeting
        val topic = if (d.project == null) "您咨询的问题" else p.name
        val list: List<String> = when (d.intent.key) {
            "suitability" -> listOf(
                "${g}${p.name}适不适合，主要看${pts}。${p.options}具体方案需要医生当面评估后才能确定。",
                "${g}想先帮您做个初步了解：${p.ask}方便的话可以发一张素颜正面照，我请医生先看看大致情况，最终以面诊为准。",
                "${g}每个人的条件不一样，适合的方案也不同，医生会结合${pts}给您出个性化建议。$INVITE")
            "recovery" -> listOf(
                "${g}${p.recovery}术后按医嘱护理、按时复诊，会恢复得更顺利。",
                "${g}您是想赶在某个时间点前恢复吗？比如上班或重要活动，告诉我时间，我请医生结合您的安排来规划。",
                "${g}具体恢复期还要看最终选的方案，$INVITE")
            "risk" -> listOf(
                "${g}任何医美项目都存在一定风险，${p.name}常见的有${p.risks}。降低风险的关键是：术前充分评估、选择有资质的机构和执业医师、使用有正规批文的产品，并按医嘱做好术后护理。",
                "${g}您最担心的是哪方面呢？疼痛、恢复期还是效果？我帮您把问题整理好，面诊时让医生逐一解答。",
                "${g}这些顾虑都很正常，面诊时医生会详细讲清楚风险和注意事项，您了解清楚再决定。$INVITE")
            "price" -> listOf(
                "${g}${p.name}的费用主要和方案（术式或产品）、医生资历、机构有关，差别比较大。医生面诊确定方案后，会给您出明细报价，写清楚包含哪些项目。",
                "${g}方便说下您的大概预算吗？我可以先帮您了解在这个范围内有哪些可选方案。",
                "${g}价格要等方案定了才准确，先面诊了解清楚会比较放心。$INVITE")
            "hesitate" -> listOf(
                "${g}理解您的顾虑，医美确实需要慎重决定。您可以先把想了解的都问清楚，不着急做决定。",
                "${g}方便说说您主要担心哪方面吗？效果、恢复期、安全还是费用？我帮您一项项问清楚。",
                "${g}可以先来面诊了解一下自己的情况，面诊只是评估，想好了再决定就可以。您这周哪天方便？")
            "aftercare" -> listOf(
                "${g}术后一定程度的肿胀、淤青在恢复期内比较常见。但如果出现持续加重的红肿疼痛、发热、渗液、异常硬结，或眼部项目出现视物模糊，请立即联系您的手术医生或尽快就医。",
                "${g}请问您现在是术后第几天，做的是什么项目？方便发张照片的话，我马上转给医生看。",
                "${g}我这边马上帮您联系医生，请保持电话畅通。期间请按医嘱护理，有任何变化随时告诉我。")
            else -> listOf(
                "${g}收到～关于${topic}，为了给您准确的建议，想先了解一下您的具体情况。",
                "${g}${p.ask}",
                "${g}这类问题当面沟通会更清楚，$INVITE")
        }
        return list.map { it + tone.ending }
    }
}
