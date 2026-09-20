package com.example.yimei

/** 发送前合规兜底，对应小程序 compliance.js。只是基础过滤，不能替代人工审核。 */
object Compliance {
    private val rules = listOf(
        "保证|确保" to "力求",
        "100%|百分百|百分之百" to "",
        "永久|终身有效" to "较长期",
        "无副作用|零风险|没有风险|绝对安全|无任何风险" to "风险需经医生评估",
        "一定能|肯定能|包您?满意|包好" to "有望",
        "最好的|最佳|行业第一|全国第一|顶级|最专业|最权威" to "合适的",
        "根治" to "改善",
        "无痕|不留疤" to "瘢痕不明显",
        "无效退款|不满意退款|包退" to ""
    ).map { Regex(it.first) to it.second }

    data class Result(val text: String, val hits: List<String>)

    fun check(text: String): Result {
        var out = text
        val hits = LinkedHashSet<String>()
        for ((re, rep) in rules) {
            re.findAll(out).forEach { hits.add(it.value) }
            out = re.replace(out, rep)
        }
        return Result(out.replace(Regex("\\s{2,}"), " ").trim(), hits.toList())
    }
}
