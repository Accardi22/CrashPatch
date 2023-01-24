package cc.woverflow.crashpatch

import cc.polyfrost.oneconfig.libs.universal.ChatColor
import cc.polyfrost.oneconfig.libs.universal.UMinecraft
import cc.polyfrost.oneconfig.utils.Multithreading
import cc.polyfrost.oneconfig.utils.commands.CommandManager
import cc.polyfrost.oneconfig.utils.commands.annotations.Command
import cc.polyfrost.oneconfig.utils.commands.annotations.Greedy
import cc.polyfrost.oneconfig.utils.commands.annotations.Main
import cc.polyfrost.oneconfig.utils.commands.annotations.SubCommand
import cc.polyfrost.oneconfig.utils.dsl.tick
import cc.woverflow.crashpatch.config.CrashPatchConfig
import cc.woverflow.crashpatch.crashes.CrashHelper
import cc.woverflow.crashpatch.crashes.DeobfuscatingRewritePolicy
import cc.woverflow.crashpatch.hooks.McDirUtil
import cc.woverflow.crashpatch.hooks.ModsCheckerPlugin
import net.minecraft.util.ChatComponentText
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File


@Mod(modid = CrashPatch.MODID, version = CrashPatch.VERSION, name = CrashPatch.NAME, modLanguageAdapter = "cc.polyfrost.oneconfig.utils.KotlinLanguageAdapter")
object CrashPatch {
    const val MODID = "crashpatch"
    const val NAME = "CrashPatch"
    const val VERSION = "@VERSION@"
    val isSkyclient by lazy(LazyThreadSafetyMode.PUBLICATION) { File(mcDir, "OneConfig/CrashPatch/SKYCLIENT").exists() || File(mcDir, "W-OVERFLOW/CrashPatch/SKYCLIENT").exists() || ModsCheckerPlugin.modsMap.keys.any { it == "skyclientcosmetics" || it == "scc" || it == "skyclientaddons" || it == "skyblockclientupdater" || it == "skyclientupdater" || it == "skyclientcore" } }
    val gameDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val file = mcDir
        try {
            if (file.parentFile?.name?.let { it == ".minecraft" || it == "minecraft" } == true) file.parentFile else file
        } catch (e: Exception) {
            e.printStackTrace()
            file
        }
    }
    val mcDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        McDirUtil.getMcDir()
    }

    @Mod.EventHandler
    fun onPreInit(e: FMLPreInitializationEvent) {
        DeobfuscatingRewritePolicy.install()
        Multithreading.runAsync {
            logger.info("Is SkyClient: $isSkyclient")
            if (!CrashHelper.loadJson()) {
                logger.error("CrashHelper failed to preload crash data JSON!")
            }
        }
    }

    @Mod.EventHandler
    fun onInit(e: FMLInitializationEvent) {
        CommandManager.INSTANCE.registerCommand(CrashPatchCommand())
        CrashPatchConfig
        //throw Throwable("java.lang.NoClassDefFoundError: xyz/matthewtgm/requisite/keybinds/KeyBind at lumien.custommainmenu.configuration.ConfigurationLoader.load(ConfigurationLoader.java:142) club.sk1er.bossbarcustomizer.BossbarMod.loadConfig cc.woverflow.hytils.handlers.chat.modules.modifiers.DefaultChatRestyler Failed to login: null The Hypixel Alpha server is currently closed! net.kdt.pojavlaunch macromodmodules")
    }

    @Command(value = "crashpatch")
    class CrashPatchCommand {
        @Main
        fun main() {
            CrashPatchConfig.openGui()
        }

        @SubCommand
        fun reload() {
            if (CrashHelper.loadJson()) {
                CrashHelper.simpleCache.clear()
                UMinecraft.getMinecraft().thePlayer.addChatMessage(ChatComponentText("${ChatColor.RED}[CrashPatch] Successfully reloaded JSON file!"))
            } else {
                UMinecraft.getMinecraft().thePlayer.addChatMessage(ChatComponentText("${ChatColor.RED}[CrashPatch] Failed to reload the JSON file!"))
            }
        }

        var a = false

        @SubCommand
        fun crash() {
            UMinecraft.getMinecraft().thePlayer.addChatMessage(ChatComponentText("${ChatColor.RED}[CrashPatch] Crashing..."))
            tick(1) {
                if (!a) {
                    a = true
                    throw Throwable("java.lang.NoClassDefFoundError: xyz/matthewtgm/requisite/keybinds/KeyBind at lumien.custommainmenu.configuration.ConfigurationLoader.load(ConfigurationLoader.java:142) club.sk1er.bossbarcustomizer.BossbarMod.loadConfig cc.woverflow.hytils.handlers.chat.modules.modifiers.DefaultChatRestyler Failed to login: null The Hypixel Alpha server is currently closed! net.kdt.pojavlaunch macromodmodules")
                } else {
                    a = false
                }
            }
        }
    }
}
val logger: Logger = LogManager.getLogger(CrashPatch)