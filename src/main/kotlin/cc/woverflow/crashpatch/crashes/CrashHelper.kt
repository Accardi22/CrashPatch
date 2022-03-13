package cc.woverflow.crashpatch.crashes

import cc.woverflow.crashpatch.utils.asJsonObject
import com.google.gson.JsonObject
import gg.essential.api.utils.WebUtil
import gg.essential.universal.wrappers.message.UTextComponent
import kotlin.collections.ArrayList
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.arrayListOf
import kotlin.collections.emptyMap
import kotlin.collections.filterNot
import kotlin.collections.forEachIndexed
import kotlin.collections.linkedMapOf
import kotlin.collections.map
import kotlin.collections.set

object CrashHelper {

    private var skyclientJson: JsonObject? = null

    @JvmStatic
    fun loadJson(): Boolean {
        return try {
            skyclientJson = WebUtil.fetchString("https://raw.githubusercontent.com/SkyblockClient/CrashData/main/crashes.json")?.asJsonObject()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun scanReport(report: String, serverCrash: Boolean = false): CrashScan? {
        try {
            val responses = getResponses(report, serverCrash)

            if (responses.isEmpty()) return null
            return CrashScan(responses)
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }

    private fun getResponses(report: String, serverCrash: Boolean): Map<String, ArrayList<String>> {
        val issues = skyclientJson ?: return emptyMap()
        val responses = linkedMapOf<String, ArrayList<String>>()

        val triggersToIgnore = arrayListOf<Int>()

        val fixTypes = issues["fixtypes"].asJsonArray
        fixTypes.map { it.asJsonObject }.forEachIndexed { index, type ->
            if (!type.has("no_ingame_display") || !type["no_ingame_display"].asBoolean) {
                if ((!type.has("server_crashes") || !type["server_crashes"].asBoolean)) {
                    if (serverCrash) {
                        triggersToIgnore.add(index)
                    } else {
                        responses[type["name"].asString] = arrayListOf()
                    }
                } else {
                    if (serverCrash) {
                        responses[type["name"].asString] = arrayListOf()
                    } else {
                        triggersToIgnore.add(index)
                    }
                }
            } else {
                triggersToIgnore.add(index)
            }
        }

        val fixes = issues["fixes"].asJsonArray
        val responseCategories = ArrayList(responses.keys)

        for (solution in fixes) {
            val solutionJson = solution.asJsonObject
            if (solutionJson.has("bot_only")) continue
            val triggerNumber = if (solutionJson.has("fixtype")) solutionJson["fixtype"].asInt else issues["default_fix_type"].asInt
            if (triggersToIgnore.contains(triggerNumber)) {
                continue
            }
            val causes = solutionJson["causes"].asJsonArray
            var trigger = false
            for (cause in causes) {
                val causeJson = cause.asJsonObject
                var theReport = report
                if (serverCrash && causeJson.has("unformatted") && causeJson["unformatted"].asBoolean) {
                    theReport = UTextComponent.stripFormatting(theReport)
                }
                when (causeJson["method"].asString) {
                    "contains" -> {
                        if (theReport.contains(causeJson["value"].asString)) {
                            trigger = true
                        } else {
                            trigger = false
                            break
                        }
                    }
                    "contains_not" -> {
                        if (!theReport.contains(causeJson["value"].asString)) {
                            trigger = true
                        } else {
                            trigger = false
                            break
                        }
                    }
                    "regex" -> {
                        if (theReport.contains(Regex(causeJson["value"].asString, RegexOption.IGNORE_CASE))) {
                            trigger = true
                        } else {
                            trigger = false
                            break
                        }
                    }
                    "regex_not" -> {
                        if (!theReport.contains(Regex(causeJson["value"].asString, RegexOption.IGNORE_CASE))) {
                            trigger = true
                        } else {
                            trigger = false
                            break
                        }
                    }
                }
            }
            if (trigger) {
                responses[responseCategories[triggerNumber]]?.add(solutionJson["fix"].asString)
            }

        }
        return responses.filterNot { it.value.isEmpty() }
    }
}

data class CrashScan(
    val solutions: Map<String, MutableList<String>>
)