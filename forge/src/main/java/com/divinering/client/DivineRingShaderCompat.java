package com.divinering.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Soft bridge to Iris/Oculus. Nothing is linked at compile time: the mod works
 * without Iris/Oculus and the API is only touched when the mod is present.
 *
 * <p>When a shader pack is active, the normal level framebuffer is replaced by
 * Iris' composite/final pass. A ring drawn at {@code AFTER_ENTITIES} is therefore
 * overwritten. The client moves to {@code AFTER_LEVEL} in that case, after Iris has
 * completed its final level pass.</p>
 */
public final class DivineRingShaderCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SHADER_MOD_LOADED =
            ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";

    private static boolean attempted;
    private static Object api;
    private static Method isShaderPackInUse;

    private static boolean depthBridgeAttempted;
    private static Object pipelineManager;
    private static Method getPipelineNullable;
    private static Field renderTargetsField;
    private static Method getDepthTexture;
    private static Method getCurrentWidth;
    private static Method getCurrentHeight;

    private DivineRingShaderCompat() {
    }

    public static boolean isShaderModLoaded() {
        return SHADER_MOD_LOADED;
    }

    public static boolean isShaderPackActive() {
        if (!SHADER_MOD_LOADED) {
            return false;
        }
        ensureApi();
        if (api == null || isShaderPackInUse == null) {
            return false;
        }
        try {
            Object value = isShaderPackInUse.invoke(api);
            return value instanceof Boolean active && active;
        } catch (Throwable throwable) {
            LOGGER.warn("[DivineRing] Could not query the Iris/Oculus shader-pack state; "
                    + "falling back to the normal render stage.", throwable);
            api = null;
            isShaderPackInUse = null;
            return false;
        }
    }

    private static void ensureApi() {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            Class<?> type = Class.forName(IRIS_API);
            Object instance = type.getMethod("getInstance").invoke(null);
            Method method = type.getMethod("isShaderPackInUse");
            if (instance != null) {
                api = instance;
                isShaderPackInUse = method;
                LOGGER.info("[DivineRing] Iris/Oculus detected; shader-pack compatibility path available.");
            }
        } catch (ClassNotFoundException ignored) {
            LOGGER.debug("[DivineRing] Iris API class not present.");
        } catch (Throwable throwable) {
            LOGGER.warn("[DivineRing] Could not initialise the Iris/Oculus compatibility bridge.", throwable);
        }
    }

    /**
     * Returns the raw OpenGL depth texture used by the active Iris/Oculus pipeline.
     * The final framebuffer's depth attachment is not reliable under a shader pack;
     * this texture is the one that contains terrain and entity depth before the final
     * composite.
     *
     * @return the depth texture id, or 0 when the internal bridge is unavailable.
     */
    public static int oculusDepthTexture() {
        Object renderTargets = renderTargets();
        if (renderTargets == null || getDepthTexture == null) {
            return 0;
        }
        try {
            Object value = getDepthTexture.invoke(renderTargets);
            return value instanceof Integer id ? id : 0;
        } catch (Throwable throwable) {
            warnDepthOnce("read the Oculus depth texture", throwable);
            return 0;
        }
    }

    public static int oculusDepthWidth() {
        return intTargetValue(getCurrentWidth);
    }

    public static int oculusDepthHeight() {
        return intTargetValue(getCurrentHeight);
    }

    private static int intTargetValue(Method method) {
        Object renderTargets = renderTargets();
        if (renderTargets == null || method == null) {
            return 0;
        }
        try {
            Object value = method.invoke(renderTargets);
            return value instanceof Integer size ? size : 0;
        } catch (Throwable throwable) {
            return 0;
        }
    }

    private static Object renderTargets() {
        ensureDepthBridge();
        if (pipelineManager == null || getPipelineNullable == null || renderTargetsField == null) {
            return null;
        }
        try {
            Object pipeline = getPipelineNullable.invoke(pipelineManager);
            if (pipeline == null) {
                return null;
            }
            return renderTargetsField.get(pipeline);
        } catch (Throwable throwable) {
            warnDepthOnce("access the Oculus rendering pipeline", throwable);
            return null;
        }
    }

    private static void ensureDepthBridge() {
        if (depthBridgeAttempted || !SHADER_MOD_LOADED) {
            return;
        }
        depthBridgeAttempted = true;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            // Iris/Oculus 1.7.6+ 移除了 getPipelineManager()，pipelineManager 改为私有静态字段；
            // 老版本仍走 getPipelineManager()。双路径探测保证两端兼容。
            Object manager;
            try {
                manager = iris.getMethod("getPipelineManager").invoke(null);
            } catch (NoSuchMethodException newApi) {
                Field pipelineManagerField = iris.getDeclaredField("pipelineManager");
                pipelineManagerField.setAccessible(true);
                manager = pipelineManagerField.get(null);
            }
            Class<?> managerType = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            Method pipelineGetter = managerType.getMethod("getPipelineNullable");

            Class<?> pipelineType = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            Field targets = pipelineType.getDeclaredField("renderTargets");
            targets.setAccessible(true);

            Class<?> targetsType = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            Method depth = targetsType.getMethod("getDepthTexture");
            Method width = targetsType.getMethod("getCurrentWidth");
            Method height = targetsType.getMethod("getCurrentHeight");

            if (manager != null) {
                pipelineManager = manager;
                getPipelineNullable = pipelineGetter;
                renderTargetsField = targets;
                getDepthTexture = depth;
                getCurrentWidth = width;
                getCurrentHeight = height;
                LOGGER.info("[DivineRing] Oculus internal depth bridge available.");
            }
        } catch (ClassNotFoundException ignored) {
            LOGGER.debug("[DivineRing] Oculus internal pipeline classes not present.");
        } catch (Throwable throwable) {
            LOGGER.warn("[DivineRing] Could not initialise the Oculus depth bridge; "
                    + "the ring will use the fallback depth path.", throwable);
        }
    }

    private static boolean warnedDepthFailure;

    private static void warnDepthOnce(String action, Throwable throwable) {
        if (warnedDepthFailure) {
            return;
        }
        warnedDepthFailure = true;
        LOGGER.warn("[DivineRing] Could not {}; using the fallback depth path.", action, throwable);
    }
}
