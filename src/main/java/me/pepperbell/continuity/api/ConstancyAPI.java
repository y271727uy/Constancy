package me.pepperbell.continuity.api;

import me.pepperbell.continuity.client.model.CtmBakedModel;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.ApiStatus;

/**
 * Public API for Constancy internals.
 * Designed for modpack-core optimizations to access internal functionality.
 */
@ApiStatus.Experimental
public final class ConstancyAPI {
    private ConstancyAPI() {
        throw new AssertionError();
    }

    /**
     * Check if a baked model needs CTM processing.
     *
     * @param model The baked model to check
     * @return true if the model is a CTM model
     */
    public static boolean needsCtm(BakedModel model) {
        return model instanceof CtmBakedModel;
    }

    /**
     * Check if two block states should connect for CTM.
     * This is the core connection predicate logic.
     *
     * @param blockView The world view
     * @param appearanceState The appearance state of the origin block
     * @param state The actual state of the origin block
     * @param pos The position of the origin block
     * @param otherAppearanceState The appearance state of the other block
     * @param otherState The actual state of the other block
     * @param otherPos The position of the other block
     * @param face The face being checked
     * @param quadSprite The sprite on the quad
     * @return true if the blocks should connect
     */
    public static boolean shouldConnect(
            BlockRenderView blockView,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            BlockState otherAppearanceState,
            BlockState otherState,
            BlockPos otherPos,
            Direction face,
            Sprite quadSprite,
            ConnectionPredicate predicate
    ) {
        return predicate.shouldConnect(blockView, appearanceState, state, pos, otherAppearanceState, otherState, otherPos, face, quadSprite);
    }

    /**
     * Get the processor slice for a given block state and sprite.
     * This is the cache lookup entry point.
     *
     * @param state The block state
     * @param sprite The sprite
     * @return The processor slice
     */
    public static QuadProcessors.Slice getProcessorSlice(BlockState state, Sprite sprite) {
        return QuadProcessors.getCache(state).apply(sprite);
    }

    /**
     * Clear all internal caches.
     * Useful for diagnostics and testing.
     */
    public static void clearCaches() {
        QuadProcessors.clearCache();
    }

    /**
     * Get the estimated number of cached block states.
     *
     * @return The cache size
     */
    public static int getCacheSize() {
        return QuadProcessors.getCacheSize();
    }
}
