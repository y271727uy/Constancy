package me.pepperbell.continuity.client.resource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.pepperbell.continuity.api.client.CachingPredicates;
import me.pepperbell.continuity.client.mixinterface.ModelLoaderExtension;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class BakedModelManagerReloadExtension {
	private final CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingResultFuture;
	private final AtomicBoolean wrapEmissiveModels = new AtomicBoolean();
	private final SpriteLoaderLoadContextImpl spriteLoaderLoadContext;
	private volatile List<QuadProcessors.ProcessorHolder> processorHolders;

	public BakedModelManagerReloadExtension(ResourceManager resourceManager, Executor prepareExecutor) {
		ctmLoadingResultFuture = CompletableFuture.supplyAsync(() -> CtmPropertiesLoader.loadAllWithState(resourceManager), prepareExecutor);
		spriteLoaderLoadContext = new SpriteLoaderLoadContextImpl(ctmLoadingResultFuture.thenApply(CtmPropertiesLoader.LoadingResult::getTextureDependencies), wrapEmissiveModels);
		EmissiveSuffixLoader.load(resourceManager);
	}

	public void setContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(spriteLoaderLoadContext);
	}

	public void clearContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(null);
	}

	public void beforeBaking(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations, ModelLoader modelLoader) {
		CtmPropertiesLoader.LoadingResult result = ctmLoadingResultFuture.join();

		List<QuadProcessors.ProcessorHolder> processorHolders = result.createProcessorHolders(spriteId -> {
			SpriteAtlasManager.AtlasPreparation preparation = preparations.get(spriteId.getAtlasId());
			Sprite sprite = preparation.getSprite(spriteId.getTextureId());
			if (sprite != null) {
				return sprite;
			}
			return preparation.getMissingSprite();
		});

		this.processorHolders = processorHolders;

		// Compute the set of block states actually affected by CTM processors
		ImmutableSet<BlockState> ctmAffectedStates = computeAffectedBlockStates(processorHolders);

		ModelWrappingHandler wrappingHandler = ModelWrappingHandler.create(!processorHolders.isEmpty(), wrapEmissiveModels.get(), ctmAffectedStates);
		((ModelLoaderExtension) modelLoader).continuity$setModelWrappingHandler(wrappingHandler);
	}

	private static ImmutableSet<BlockState> computeAffectedBlockStates(List<QuadProcessors.ProcessorHolder> processorHolders) {
		ImmutableSet.Builder<BlockState> builder = ImmutableSet.builder();
		for (QuadProcessors.ProcessorHolder holder : processorHolders) {
			CachingPredicates predicates = holder.predicates();
			if (predicates.affectsBlockStates()) {
				// Collect all block states that this processor affects
				for (Block block : Registries.BLOCK) {
					for (BlockState state : block.getStateManager().getStates()) {
						if (predicates.affectsBlockState(state)) {
							builder.add(state);
						}
					}
				}
			}
		}
		return builder.build();
	}

	public void apply() {
		List<QuadProcessors.ProcessorHolder> processorHolders = this.processorHolders;
		if (processorHolders != null) {
			QuadProcessors.reload(processorHolders);
		}
	}

	private static class SpriteLoaderLoadContextImpl implements SpriteLoaderLoadContext {
		private final CompletableFuture<Map<Identifier, Set<Identifier>>> allExtraIdsFuture;
		private final Map<Identifier, CompletableFuture<Set<Identifier>>> extraIdsFutures = new Object2ObjectOpenHashMap<>();
		private final EmissiveControl blockAtlasEmissiveControl;

		public SpriteLoaderLoadContextImpl(CompletableFuture<Map<Identifier, Set<Identifier>>> allExtraIdsFuture, AtomicBoolean blockAtlasHasEmissivesHolder) {
			this.allExtraIdsFuture = allExtraIdsFuture;
			blockAtlasEmissiveControl = new EmissiveControlImpl(blockAtlasHasEmissivesHolder);
		}

		@Override
		public CompletableFuture<@Nullable Set<Identifier>> getExtraIdsFuture(Identifier atlasId) {
			return extraIdsFutures.computeIfAbsent(atlasId, id -> allExtraIdsFuture.thenApply(allExtraIds -> allExtraIds.get(id)));
		}

		@Override
		@Nullable
		public EmissiveControl getEmissiveControl(Identifier atlasId) {
			if (atlasId.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
				return blockAtlasEmissiveControl;
			}
			return null;
		}

		private static class EmissiveControlImpl implements EmissiveControl {
			@Nullable
			private volatile Map<Identifier, Identifier> emissiveIdMap;
			private final AtomicBoolean hasEmissivesHolder;

			public EmissiveControlImpl(AtomicBoolean hasEmissivesHolder) {
				this.hasEmissivesHolder = hasEmissivesHolder;
			}

			@Override
			@Nullable
			public Map<Identifier, Identifier> getEmissiveIdMap() {
				return emissiveIdMap;
			}

			@Override
			public void setEmissiveIdMap(Map<Identifier, Identifier> emissiveIdMap) {
				this.emissiveIdMap = emissiveIdMap;
			}

			@Override
			public void markHasEmissives() {
				hasEmissivesHolder.set(true);
			}
		}
	}
}
