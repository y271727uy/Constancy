package org.thinkingstudio.constancy.compat.ctm;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.chisel.ctm.CTM;
import team.chisel.ctm.api.model.IModelCTM;
import team.chisel.ctm.api.texture.ICTMTexture;

/** CTM is the sole connected-texture rendering backend for Constancy. */
public final class CtmBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger("Constancy/CTM");

    private CtmBackend() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(CTM.MOD_ID);
    }

    public static void initialize() {
        if (!isAvailable()) {
            throw new IllegalStateException("CTM backend is required but was not loaded");
        }

        // Keep the public API types as an explicit integration contract.
        LOGGER.info("Using ConnectedTexturesMod as the sole rendering backend (model API: {}, texture API: {})",
                IModelCTM.class.getSimpleName(), ICTMTexture.class.getSimpleName());
    }
}
