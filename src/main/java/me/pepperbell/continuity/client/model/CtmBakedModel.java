package me.pepperbell.continuity.client.model;

import java.util.function.Function;
import java.util.function.Supplier;

import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.util.RenderUtil;
import me.pepperbell.continuity.impl.client.ProcessingContextImpl;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

public class CtmBakedModel extends ForwardingBakedModel {
	public static final int PASSES = 4;

	protected final BlockState defaultState;
	protected volatile Function<Sprite, QuadProcessors.Slice> defaultSliceFunc;

	public CtmBakedModel(BakedModel wrapped, BlockState defaultState) {
		this.wrapped = wrapped;
		this.defaultState = defaultState;
	}

	@Override
	public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
		// Early exit checks (injection point 1: feature flags)
		if (!shouldProcessCtm(state, blockView, pos)) {
			super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		CtmQuadTransform quadTransform = container.ctmQuadTransform;
		if (quadTransform.isActive()) {
			super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
			return;
		}

		// Injection point 2: appearance state resolution
		BlockState appearanceState = resolveAppearanceState(blockView, state, pos);

		// Injection point 3: quad transform preparation
		prepareQuadTransform(quadTransform, blockView, appearanceState, state, pos, randomSupplier, context);

		// Core processing
		context.pushTransform(quadTransform);
		super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
		context.popTransform();

		// Output and cleanup
		quadTransform.processingContext.outputTo(context.getEmitter());
		quadTransform.reset();
	}

	/**
	 * Injection point: Check if CTM processing should run.
	 * Modpack core can override this to use cached feature states.
	 *
	 * @param state The block state
	 * @param blockView The world view
	 * @param pos The block position
	 * @return true if CTM should process this block
	 */
	protected boolean shouldProcessCtm(BlockState state, BlockRenderView blockView, BlockPos pos) {
		if (!ContinuityConfig.INSTANCE.connectedTextures.get()) {
			return false;
		}
		ModelObjectsContainer container = ModelObjectsContainer.get();
		return container.featureStates.getConnectedTexturesState().isEnabled();
	}

	/**
	 * Injection point: Resolve the appearance state for a block.
	 * The correct way to get the appearance of the origin state from within a block model is to (1) call
	 * getAppearance on the result of blockView.getBlockState(pos) instead of the passed state and (2) pass the
	 * pos and world state of the adjacent block as the source pos and source state.
	 * (1) is not followed here because at this point in execution, within this call to
	 * CtmBakedModel#emitBlockQuads, the state parameter must already contain the world state. Even if this
	 * CtmBakedModel is wrapped, then the wrapper must pass the same state as it received because not doing so can
	 * cause crashes when the wrapped model is a vanilla multipart model or delegates to one. Thus, getting the
	 * world state again is inefficient and unnecessary.
	 * (2) is not possible here because the appearance state is necessary to get the slice and only the processors
	 * within the slice actually perform checks on adjacent blocks. Likewise, the processors themselves cannot
	 * retrieve the appearance state since the correct processors can only be chosen with the initially correct
	 * appearance state.
	 * Additionally, the side is chosen to always be the first constant of the enum (DOWN) for simplicity. Querying
	 * the appearance for all six sides would be more correct, but less efficient. This may be fixed in the future,
	 * especially if there is an actual use case for it.
	 *
	 * @param blockView The world view
	 * @param state The block state
	 * @param pos The block position
	 * @return The appearance state
	 */
	protected BlockState resolveAppearanceState(BlockRenderView blockView, BlockState state, BlockPos pos) {
		return state.getAppearance(blockView, pos, Direction.DOWN, state, pos);
	}

	/**
	 * Injection point: Prepare the quad transform.
	 * Modpack core can override this to use alternative preparation logic.
	 *
	 * @param quadTransform The transform to prepare
	 * @param blockView The world view
	 * @param appearanceState The appearance state
	 * @param state The block state
	 * @param pos The block position
	 * @param randomSupplier The random supplier
	 * @param context The render context
	 */
	protected void prepareQuadTransform(
			CtmQuadTransform quadTransform,
			BlockRenderView blockView,
			BlockState appearanceState,
			BlockState state,
			BlockPos pos,
			Supplier<Random> randomSupplier,
			RenderContext context
	) {
		quadTransform.prepare(
				blockView,
				appearanceState,
				state,
				pos,
				randomSupplier,
				context,
				ContinuityConfig.INSTANCE.useManualCulling.get(),
				getSliceFunc(appearanceState)
		);
	}

	@Override
	public boolean isVanillaAdapter() {
		if (!ContinuityConfig.INSTANCE.connectedTextures.get()) {
			return super.isVanillaAdapter();
		}
		return false;
	}

	/**
	 * Get the slice function for a block state.
	 * Injection point: Modpack core can override this to use ModernFix's cache.
	 * 
	 * Uses a simpler volatile pattern instead of full DCL when the state matches the default.
	 *
	 * @param state The block state
	 * @return The slice function
	 */
	protected Function<Sprite, QuadProcessors.Slice> getSliceFunc(BlockState state) {
		if (state == defaultState) {
			// Fast path: read volatile once
			Function<Sprite, QuadProcessors.Slice> sliceFunc = defaultSliceFunc;
			if (sliceFunc == null) {
				// Slow path: compute and publish
				sliceFunc = computeDefaultSliceFunc();
			}
			return sliceFunc;
		}
		// Non-default state: no caching
		return QuadProcessors.getCache(state);
	}

	/**
	 * Compute and cache the default slice function.
	 * Synchronized to ensure only one thread computes the value.
	 */
	private synchronized Function<Sprite, QuadProcessors.Slice> computeDefaultSliceFunc() {
		// Double-check inside the synchronized block
		Function<Sprite, QuadProcessors.Slice> sliceFunc = defaultSliceFunc;
		if (sliceFunc == null) {
			sliceFunc = QuadProcessors.getCache(defaultState);
			defaultSliceFunc = sliceFunc;
		}
		return sliceFunc;
	}

	protected static class CtmQuadTransform implements RenderContext.QuadTransform {
		protected final ProcessingContextImpl processingContext = new ProcessingContextImpl();

		protected BlockRenderView blockView;
		protected BlockState appearanceState;
		protected BlockState state;
		protected BlockPos pos;
		protected Supplier<Random> randomSupplier;
		protected RenderContext renderContext;
		protected boolean useManualCulling;
		protected Function<Sprite, QuadProcessors.Slice> sliceFunc;

		protected boolean active;

		@Override
		public boolean transform(MutableQuadView quad) {
			if (useManualCulling && renderContext.isFaceCulled(quad.cullFace())) {
				return false;
			}

			for (int pass = 0; pass < PASSES; pass++) {
				Boolean result = transformOnce(quad, pass);
				if (result != null) {
					return result;
				}
			}

			return true;
		}

		protected Boolean transformOnce(MutableQuadView quad, int pass) {
			Sprite sprite = RenderUtil.getSpriteFinder().find(quad);
			QuadProcessors.Slice slice = sliceFunc.apply(sprite);
			QuadProcessor[] processors = pass == 0 ? slice.processors() : slice.multipassProcessors();
			for (QuadProcessor processor : processors) {
				QuadProcessor.ProcessingResult result = processor.processQuad(quad, sprite, blockView, appearanceState, state, pos, randomSupplier, pass, processingContext);
				if (result == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) {
					continue;
				}
				if (result == QuadProcessor.ProcessingResult.NEXT_PASS) {
					return null;
				}
				if (result == QuadProcessor.ProcessingResult.STOP) {
					return true;
				}
				if (result == QuadProcessor.ProcessingResult.DISCARD) {
					return false;
				}
			}
			return true;
		}

		public boolean isActive() {
			return active;
		}

		public void prepare(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext renderContext, boolean useManualCulling, Function<Sprite, QuadProcessors.Slice> sliceFunc) {
			this.blockView = blockView;
			this.appearanceState = appearanceState;
			this.state = state;
			this.pos = pos;
			this.randomSupplier = randomSupplier;
			this.renderContext = renderContext;
			this.useManualCulling = useManualCulling;
			this.sliceFunc = sliceFunc;

			active = true;

			processingContext.prepare();
		}

		public void reset() {
			blockView = null;
			appearanceState = null;
			state = null;
			pos = null;
			randomSupplier = null;
			renderContext = null;
			useManualCulling = false;
			sliceFunc = null;

			active = false;

			processingContext.reset();
		}
	}
}
