/*
 *This file is modified based on
 *https://github.com/DimensionalDevelopment/VanillaFix/blob/master/src/main/java/org/dimdev/vanillafix/crashes/mixins/client/MixinMinecraft.java
 *The source file uses the MIT License.
 */

package net.wyvest.bettercrashes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MinecraftError;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.Loader;
import net.wyvest.bettercrashes.gui.GuiCrashScreen;
import net.wyvest.bettercrashes.gui.GuiInitErrorScreen;
import net.wyvest.bettercrashes.utils.CrashUtils;
import net.wyvest.bettercrashes.utils.StateManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Queue;

/**
 * @author Runemoro
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    @Final
    private static Logger logger;
    @Shadow
    volatile boolean running;
    @Shadow
    private boolean hasCrashed;
    @Shadow
    private CrashReport crashReporter;
    @Shadow
    public static byte[] memoryReserve;
    @Shadow
    @Final
    private Queue scheduledTasks;
    @Shadow
    public EntityRenderer entityRenderer;
    @Shadow
    private long debugCrashKeyPressTime;
    @Shadow
    public GameSettings gameSettings;
    @Shadow
    public GuiIngame ingameGUI;
    @Shadow
    private IReloadableResourceManager mcResourceManager;
    @Shadow
    public FontRenderer fontRendererObj;
    @Shadow
    public TextureManager renderEngine;
    @Shadow
    private LanguageManager mcLanguageManager;
    @Shadow
    private SoundHandler mcSoundHandler;
    @Shadow
    @Final
    private IMetadataSerializer metadataSerializer_;
    @Shadow
    private Framebuffer framebufferMc;
    @Shadow
    public GuiScreen currentScreen;
    @Shadow
    private int leftClickCounter;
    @Shadow
    public int displayWidth;
    @Shadow
    public int displayHeight;

    @Shadow
    protected abstract void startGame() throws LWJGLException;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen guiScreenIn);

    @Shadow
    public abstract CrashReport addGraphicsAndWorldToCrashReport(CrashReport theCrash);

    @Shadow
    protected abstract void runGameLoop();

    @Shadow
    public abstract NetHandlerPlayClient getNetHandler();

    @Shadow
    public abstract void loadWorld(WorldClient worldClientIn);

    @Shadow
    public abstract void freeMemory();

    @Shadow
    public abstract void shutdownMinecraftApplet();

    @Shadow
    public abstract void refreshResources();

    @Shadow
    protected abstract void checkGLError(String message);

    @Shadow
    public abstract void updateDisplay();

    private int clientCrashCount = 0;
    private int serverCrashCount = 0;

    /**
     * @author Runemoro
     * @reason Overwrite Minecraft.run()
     */
    @Overwrite
    public void run() {
        running = true;
        try {
            startGame();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.makeCrashReport(throwable, "Initializing game");
            crashReport.makeCategory("Initialization");
            displayInitErrorScreen(addGraphicsAndWorldToCrashReport(crashReport));
            return;
        }
        try {
            while (running) {
                if (!hasCrashed || crashReporter == null) {
                    try {
                        runGameLoop();
                    } catch (ReportedException e) {
                        clientCrashCount++;
                        addGraphicsAndWorldToCrashReport(e.getCrashReport());
                        addInfoToCrash(e.getCrashReport());
                        resetGameState();
                        logger.fatal("Reported exception thrown!", e);
                        displayCrashScreen(e.getCrashReport());
                    } catch (Throwable e) {
                        clientCrashCount++;
                        CrashReport report = new CrashReport("Unexpected error", e);
                        addGraphicsAndWorldToCrashReport(report);
                        addInfoToCrash(report);
                        resetGameState();
                        logger.fatal("Unreported exception thrown!", e);
                        displayCrashScreen(report);
                    }
                } else {
                    serverCrashCount++;
                    addInfoToCrash(crashReporter);
                    freeMemory();
                    displayCrashScreen(crashReporter);
                    hasCrashed = false;
                    crashReporter = null;
                }
            }
        } catch (MinecraftError ignored) {
        } finally {
            shutdownMinecraftApplet();
        }

    }

    /**
     * @param report
     * @author Runemoro
     */
    public void displayCrashScreen(CrashReport report) {
        try {
            CrashUtils.outputReport(report);

            // Reset hasCrashed, debugCrashKeyPressTime, and crashIntegratedServerNextTick
            hasCrashed = false;
            debugCrashKeyPressTime = -1;
//            crashIntegratedServerNextTick = false;

            // Vanilla does this when switching to main menu but not our custom crash screen
            // nor the out of memory screen (see https://bugs.mojang.com/browse/MC-128953)
            gameSettings.showDebugInfo = false;
            ingameGUI.getChatGUI().clearChatMessages();

            // Display the crash screen
//            runGUILoop(new GuiCrashScreen(report));
            displayGuiScreen(new GuiCrashScreen(report));
        } catch (Throwable t) {
            // The crash screen has crashed. Report it normally instead.
            logger.error("An uncaught exception occured while displaying the crash screen, making normal report instead", t);
            displayCrashReport(report);
            System.exit(report.getFile() != null ? -1 : -2);
        }
    }

    private void addInfoToCrash(CrashReport crashReport) {
        crashReport.getCategory().addCrashSectionCallable("Client Crashes Since Restart", () -> String.valueOf(clientCrashCount));
        crashReport.getCategory().addCrashSectionCallable("Integrated Server Crashes Since Restart", () -> String.valueOf(serverCrashCount));
    }

    /**
     * @author Runemoro
     */
    public void resetGameState() {
        try {
            // Free up memory such that this works properly in case of an OutOfMemoryError
            int originalMemoryReserveSize = -1;
            try { // In case another mod actually deletes the memoryReserve field
                if (memoryReserve != null) {
                    originalMemoryReserveSize = memoryReserve.length;
                    memoryReserve = new byte[0];
                }
            } catch (Throwable ignored) {
            }

            StateManager.resetStates();

            if (getNetHandler() != null) {
                getNetHandler().getNetworkManager().closeChannel(new ChatComponentText("[BetterCrashes] Client crashed"));
            }
            loadWorld(null);
            if (entityRenderer.isShaderActive()) {
                entityRenderer.stopUseShader();
            }
            scheduledTasks.clear(); // TODO: Figure out why this isn't necessary for vanilla disconnect

            if (originalMemoryReserveSize != -1) {
                try {
                    memoryReserve = new byte[originalMemoryReserveSize];
                } catch (Throwable ignored) {
                }
            }
            System.gc();
        } catch (Throwable t) {
            logger.error("Failed to reset state after a crash", t);
            try {
                StateManager.resetStates();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * @param report
     * @author Runemoro
     */
    public void displayInitErrorScreen(CrashReport report) {
        CrashUtils.outputReport(report);
        try {
            mcResourceManager = new SimpleReloadableResourceManager(metadataSerializer_);
            renderEngine = new TextureManager(mcResourceManager);
            mcResourceManager.registerReloadListener(renderEngine);

            mcLanguageManager = new LanguageManager(metadataSerializer_, gameSettings.language);
            mcResourceManager.registerReloadListener(mcLanguageManager);

            refreshResources(); // TODO: Why is this necessary?
            fontRendererObj = new FontRenderer(gameSettings, new ResourceLocation("textures/font/ascii.png"), renderEngine, false);
            mcResourceManager.registerReloadListener(fontRendererObj);

            mcSoundHandler = new SoundHandler(mcResourceManager, gameSettings);
            mcResourceManager.registerReloadListener(mcSoundHandler);

            running = true;
            try {
                //noinspection deprecation
                SplashProgress.pause();// Disable the forge splash progress screen
            } catch (Throwable ignored) {
            }
            runGUILoop(new GuiInitErrorScreen(report));
        } catch (Throwable t) {
            logger.error("An uncaught exception occured while displaying the init error screen, making normal report instead", t);
            displayCrashReport(report);
            System.exit(report.getFile() != null ? -1 : -2);
        }
    }

    /**
     * @param screen
     * @author Runemoro
     */
    private void runGUILoop(GuiInitErrorScreen screen) throws Throwable {
        displayGuiScreen(screen);
        while (running && currentScreen != null && !(currentScreen instanceof GuiMainMenu) && !(Loader.isModLoaded("custommainmenu"))) {
            if (Display.isCreated() && Display.isCloseRequested()) {
                System.exit(0);
            }
            leftClickCounter = 10000;
            currentScreen.handleInput();
            currentScreen.updateScreen();

            GL11.glPushMatrix();
            GL11.glClear(16640);
            framebufferMc.bindFramebuffer(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            GL11.glViewport(0, 0, displayWidth, displayHeight);

            // EntityRenderer.setupOverlayRendering
            ScaledResolution scaledResolution = new ScaledResolution((Minecraft) (Object) this);
            GlStateManager.clear(256);
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, scaledResolution.getScaledWidth_double(), scaledResolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);

            int width = scaledResolution.getScaledWidth();
            int height = scaledResolution.getScaledHeight();
            int mouseX = Mouse.getX() * width / displayWidth;
            int mouseY = height - Mouse.getY() * height / displayHeight - 1;
            currentScreen.drawScreen(mouseX, mouseY, 0);
            if (screen.shouldCrash) {
                throw screen.report.getCrashCause();
            }

            framebufferMc.unbindFramebuffer();
            GL11.glPopMatrix();

            GL11.glPushMatrix();
            framebufferMc.framebufferRender(displayWidth, displayHeight);
            GL11.glPopMatrix();

            updateDisplay();
            Thread.yield();
            Display.sync(60);
            checkGLError("BetterCrashes GUI Loop");
        }
    }


    /**
     * @param report
     * @author Runemoro
     * @reason
     */
    @Overwrite
    public void displayCrashReport(CrashReport report) {
        CrashUtils.outputReport(report);
    }

}



