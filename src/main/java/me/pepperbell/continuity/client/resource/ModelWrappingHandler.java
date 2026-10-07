package me.pepperbell.continuity.client.resource;

import com.google.common.collect.ImmutableSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableMap;

import me.pepperbell.continuity.client.mixinterface.ModelLoaderExtension;
import me.pepperbell.continuity.client.model.CtmBakedModel;
import me.pepperbell.continuity.client.model.EmissiveBakedModel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;

public class ModelWrappingHandler {
	private final boolean wrapCtm;
	private final boolean wrapEmissive;
	private final ImmutableMap<ModelIdentifier, BlockState> blockStateModelIds;
	private final ImmutableSet<BlockState> ctmAffectedStates;

	private ModelWrappingHandler(boolean wrapCtm, boolean wrapEmissive, ImmutableSet<BlockState> ctmAffectedStates) {
		this.wrapCtm = wrapCtm;
		this.wrapEmissive = wrapEmissive;
		this.ctmAffectedStates = ctmAffectedStates;
		// Only build the map when CTM is actually wrapping something
		this.blockStateModelIds = wrapCtm ? createBlockStateModelIdMap(ctmAffectedStates) : ImmutableMap.of();
	}

	@Nullable
	public static ModelWrappingHandler create(boolean wrapCtm, boolean wrapEmissive, ImmutableSet<BlockState> ctmAffectedStates) {
		if (!wrapCtm && !wrapEmissive) {
			return null;
		}
		// When CTM is requested but no states are affected, don't wrap anything
		if (wrapCtm && ctmAffectedStates.isEmpty()) {
			wrapCtm = false;
		}
		if (!wrapCtm && !wrapEmissive) {
			return null;
		}
		return new ModelWrappingHandler(wrapCtm, wrapEmissive, ctmAffectedStates);
	}

	private static ImmutableMap<ModelIdentifier, BlockState> createBlockStateModelIdMap(ImmutableSet<BlockState> affectedStates) {
		ImmutableMap.Builder<ModelIdentifier, BlockState> builder = ImmutableMap.builder();
		// Only index block states that are actually affected by CTM
		for (BlockState state : affectedStates) {
			Identifier blockId = state.getBlock().getRegistryEntry().registryKey().getValue();
			ModelIdentifier modelId = BlockModels.getModelId(blockId, state);
			builder.put(modelId, state);
		}
		return builder.build();
	}

	public BakedModel wrap(@Nullable BakedModel model, Identifier modelId) {
		if (model != null && !model.isBuiltin() && !modelId.equals(ModelLoader.MISSING_ID)) {
			// Injection point 1: CTM wrapping (modpack core can replace this)
			model = wrapCtm(model, modelId);
			// Injection point 2: Emissive wrapping (modpack core can replace this)
			model = wrapEmissive(model);
		}
		return model;
	}

	/**
	 * Injection point: Wrap a model with CTM processing.
	 * Modpack core can override this to use alternative caching strategies.
	 *
	 * @param model The model to wrap
	 * @param modelId The model identifier
	 * @return The wrapped model, or the original if no wrapping is needed
	 */
	protected BakedModel wrapCtm(BakedModel model, Identifier modelId) {
		if (wrapCtm && modelId instanceof ModelIdentifier) {
			BlockState state = getBlockStateForModel((ModelIdentifier) modelId);
			if (state != null) {
				return createCtmModel(model, state);
			}
		}
		return model;
	}

	/**
	 * Injection point: Look up the block state for a model identifier.
	 * Modpack core can override this to use ModernFix's cache.
	 *
	 * @param modelId The model identifier
	 * @return The block state, or null if not found
	 */
	@Nullable
	protected BlockState getBlockStateForModel(ModelIdentifier modelId) {
		return blockStateModelIds.get(modelId);
	}

	/**
	 * Injection point: Create a CTM baked model.
	 * Modpack core can override this to use object pools.
	 *
	 * @param wrapped The wrapped model
	 * @param state The block state
	 * @return The CTM model
	 */
	protected BakedModel createCtmModel(BakedModel wrapped, BlockState state) {
		return new CtmBakedModel(wrapped, state);
	}

	/**
	 * Injection point: Wrap a model with emissive processing.
	 * Modpack core can override this.
	 *
	 * @param model The model to wrap
	 * @return The wrapped model, or the original if no wrapping is needed
	 */
	protected BakedModel wrapEmissive(BakedModel model) {
		if (wrapEmissive) {
			return new EmissiveBakedModel(model);
		}
		return model;
	}

	@ApiStatus.Internal
	public static void init(IEventBus modEventBus) {
		modEventBus.<ModelEvent.ModifyBakingResult>addListener(event -> {
			ModelLoader modelLoader = event.getModelBakery();
			ModelWrappingHandler wrappingHandler = ((ModelLoaderExtension) modelLoader).continuity$getModelWrappingHandler();
			if (wrappingHandler != null) {
				Map<Identifier, BakedModel> bakedModels = event.getModels();
				Set<Identifier> keys = ImmutableSet.copyOf(event.getModels().keySet());
				for (Identifier modelId : keys) {
					bakedModels.put(modelId, wrappingHandler.wrap(bakedModels.get(modelId), modelId));
				}
			}
		});
	}
}
