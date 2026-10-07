package me.pepperbell.continuity.client.model;

import java.util.List;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.pepperbell.continuity.api.client.CachingPredicates;
import me.pepperbell.continuity.api.client.QuadProcessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;

public final class QuadProcessors {
	private static ProcessorHolder[] processorHolders = new ProcessorHolder[0];
	private static final BlockStateKeyCache CACHE = new BlockStateKeyCache();

	public static Function<Sprite, Slice> getCache(BlockState state) {
		return CACHE.apply(state);
	}

	private static Slice computeSlice(BlockState state, Sprite sprite) {
		List<QuadProcessor> processorList = new ObjectArrayList<>();
		List<QuadProcessor> multipassProcessorList = new ObjectArrayList<>();

		for (ProcessorHolder holder : processorHolders) {
			QuadProcessor processor = holder.processor();
			CachingPredicates predicates = holder.predicates();
			if (!predicates.affectsBlockStates() || predicates.affectsBlockState(state)) {
				if (predicates.affectsSprites()) {
					if (predicates.affectsSprite(sprite)) {
						processorList.add(processor);
						if (predicates.isValidForMultipass()) {
							multipassProcessorList.add(processor);
						}
					}
				} else {
					processorList.add(processor);
				}
			}
		}

		QuadProcessor[] processors = processorList.toArray(QuadProcessor[]::new);
		QuadProcessor[] multipassProcessors = multipassProcessorList.toArray(QuadProcessor[]::new);
		return new Slice(processors, multipassProcessors);
	}

	@ApiStatus.Internal
	public static void reload(List<QuadProcessors.ProcessorHolder> processorHolders) {
		QuadProcessors.processorHolders = processorHolders.toArray(ProcessorHolder[]::new);
		CACHE.clear();
	}

	/**
	 * Clear all caches.
	 * Public API for diagnostics and testing.
	 */
	public static void clearCache() {
		CACHE.clear();
	}

	/**
	 * Get the estimated number of cached block states.
	 * Public API for diagnostics.
	 *
	 * @return The number of cached block states
	 */
	public static int getCacheSize() {
		return CACHE.size();
	}

	public record ProcessorHolder(QuadProcessor processor, CachingPredicates predicates) {
	}

	public record Slice(QuadProcessor[] processors, QuadProcessor[] multipassProcessors) {
	}

	private static class BlockStateKeyCache {
		private final Reference2ReferenceOpenHashMap<BlockState, SpriteKeyCache> map = new Reference2ReferenceOpenHashMap<>();
		private final StampedLock lock = new StampedLock();

		public SpriteKeyCache apply(BlockState state) {
			SpriteKeyCache innerCache;

			// Fast path: optimistic read
			long optimisticReadStamp = lock.tryOptimisticRead();
			if (optimisticReadStamp != 0L) {
				innerCache = tryOptimisticRead(state, optimisticReadStamp);
				if (innerCache != null) {
					return innerCache;
				}
			}

			// Slow path: read lock
			long readStamp = lock.readLock();
			try {
				innerCache = map.get(state);
			} finally {
				lock.unlockRead(readStamp);
			}

			if (innerCache == null) {
				long writeStamp = lock.writeLock();
				try {
					innerCache = map.get(state);
					if (innerCache == null) {
						innerCache = new SpriteKeyCache(state);
						map.put(state, innerCache);
					}
				} finally {
					lock.unlockWrite(writeStamp);
				}
			}

			return innerCache;
		}

		/**
		 * Try to read from the map optimistically.
		 * Returns null on validation failure or concurrent modification.
		 * Separated into a method to avoid polluting the hot path with exception handling.
		 */
		@Nullable
		private SpriteKeyCache tryOptimisticRead(BlockState state, long stamp) {
			try {
				// This map read could happen at the same time as a map write.
				// This is safe due to the map implementation used, which is guaranteed to not mutate the map during a read.
				SpriteKeyCache innerCache = map.get(state);
				if (innerCache != null && lock.validate(stamp)) {
					return innerCache;
				}
			} catch (Exception e) {
				// Concurrent modification detected, fall back to locked read
			}
			return null;
		}

		public void clear() {
			long writeStamp = lock.writeLock();
			try {
				map.values().forEach(SpriteKeyCache::clear);
			} finally {
				lock.unlockWrite(writeStamp);
			}
		}

		public int size() {
			long readStamp = lock.readLock();
			try {
				return map.size();
			} finally {
				lock.unlockRead(readStamp);
			}
		}
	}

	private static class SpriteKeyCache implements Function<Sprite, Slice> {
		private final Reference2ReferenceOpenHashMap<Sprite, Slice> map = new Reference2ReferenceOpenHashMap<>(4, Hash.FAST_LOAD_FACTOR);
		private final StampedLock lock = new StampedLock();
		private final BlockState state;

		public SpriteKeyCache(BlockState state) {
			this.state = state;
		}

		@Override
		public Slice apply(Sprite sprite) {
			Slice slice;

			// Fast path: optimistic read
			long optimisticReadStamp = lock.tryOptimisticRead();
			if (optimisticReadStamp != 0L) {
				slice = tryOptimisticRead(sprite, optimisticReadStamp);
				if (slice != null) {
					return slice;
				}
			}

			// Slow path: read lock
			long readStamp = lock.readLock();
			try {
				slice = map.get(sprite);
			} finally {
				lock.unlockRead(readStamp);
			}

			if (slice == null) {
				long writeStamp = lock.writeLock();
				try {
					slice = map.get(sprite);
					if (slice == null) {
						slice = computeSlice(state, sprite);
						map.put(sprite, slice);
					}
				} finally {
					lock.unlockWrite(writeStamp);
				}
			}

			return slice;
		}

		/**
		 * Try to read from the map optimistically.
		 * Returns null on validation failure or concurrent modification.
		 * Separated into a method to avoid polluting the hot path with exception handling.
		 */
		@Nullable
		private Slice tryOptimisticRead(Sprite sprite, long stamp) {
			try {
				// This map read could happen at the same time as a map write.
				// This is safe due to the map implementation used, which is guaranteed to not mutate the map during a read.
				Slice slice = map.get(sprite);
				if (slice != null && lock.validate(stamp)) {
					return slice;
				}
			} catch (Exception e) {
				// Concurrent modification detected, fall back to locked read
			}
			return null;
		}

		public void clear() {
			long writeStamp = lock.writeLock();
			try {
				map.clear();
			} finally {
				lock.unlockWrite(writeStamp);
			}
		}
	}
}
