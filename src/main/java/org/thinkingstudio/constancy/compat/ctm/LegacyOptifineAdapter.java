package org.thinkingstudio.constancy.compat.ctm;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.chisel.ctm.api.model.IModelCTM;
import team.chisel.ctm.api.texture.ICTMTexture;
import team.chisel.ctm.api.texture.ITextureContext;
import team.chisel.ctm.api.texture.ITextureType;
import team.chisel.ctm.api.util.TextureInfo;
import team.chisel.ctm.client.model.ModelBakedCTM;
import team.chisel.ctm.client.texture.render.TextureCTM;
import team.chisel.ctm.client.texture.type.TextureTypeCTM;
import team.chisel.ctm.client.util.BlockRenderLayer;

import javax.annotation.Nullable;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Compatibility slice for bundled OptiFine method=ctm rules. */
public final class LegacyOptifineAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("Constancy/LegacyCTM");
    private static final int[] INDEX = {0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,36,17,36,17,24,19,24,43,36,17,36,17,24,19,24,43,16,18,16,18,6,46,6,21,16,18,16,18,28,9,28,22,36,17,36,17,24,19,24,43,36,17,36,17,24,19,24,43,37,40,37,40,30,8,30,34,37,40,37,40,25,23,25,45,0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,36,39,36,39,24,41,24,27,36,39,36,39,24,41,24,27,16,42,16,42,6,20,6,10,16,42,16,42,28,35,28,44,36,39,36,39,24,41,24,27,36,39,36,39,24,41,24,27,37,38,37,38,30,11,30,32,37,38,37,38,25,33,25,26};
    private static volatile List<Rule> rules = List.of();
    private static final AtomicInteger transformed = new AtomicInteger();
    private static final AtomicInteger noContext = new AtomicInteger();
    private static final AtomicInteger missingTarget = new AtomicInteger();
    private LegacyOptifineAdapter() {}

    @SubscribeEvent
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        refresh(manager);
        manager.listResources("models/legacy_tiles", p -> p.getPath().endsWith(".json")).keySet().forEach(id -> {
            String path = id.getPath();
            event.register(new ResourceLocation(id.getNamespace(), path.substring("models/".length(), path.length() - 5)));
        });
    }

    private static void refresh(ResourceManager manager) {
        if (!rules.isEmpty()) return;
        List<Rule> found = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : manager.listResourceStacks("optifine/ctm", p -> p.getPath().endsWith(".properties")).entrySet()) for (Resource resource : entry.getValue()) {
            try (Reader reader = resource.openAsReader()) {
                Properties p = new Properties(); p.load(reader);
                String method = p.getProperty("method", p.getProperty("\ufeffmethod", "")); if ("ctm".equalsIgnoreCase(method)) found.add(new Rule(p.getProperty("matchTiles", p.getProperty("\ufeffmatchTiles", "")), tiles(entry.getKey(), p.getProperty("tiles", p.getProperty("\ufefftiles", "0-46")))));
            } catch (Exception e) { LOGGER.warn("Cannot parse {}", entry.getKey(), e); }
        }
        try {
            Path generated = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("generated-ctm/assets");
            if (Files.isDirectory(generated)) try (var stream = Files.walk(generated)) {
                stream.filter(p -> p.toString().endsWith(".properties") && p.toString().replace('\\','/').contains("/optifine/ctm/")).forEach(p -> {
                    LOGGER.info("Scanning generated CTM rule file {}", p);
                    try {
                        String s = p.toString().replace('\\','/'); int a = s.indexOf("/assets/") + 8; String rel = s.substring(a); int slash = rel.indexOf('/'); String marker = "/optifine/ctm/"; int markerAt = rel.indexOf(marker); if (markerAt < 0) return; String tailPath = rel.substring(markerAt + marker.length()); ResourceLocation id = new ResourceLocation(rel.substring(0, slash), "optifine/ctm/" + tailPath);
                        try (Reader reader = Files.newBufferedReader(p)) { Properties props = new Properties(); props.load(reader); String method = props.getProperty("method", props.getProperty("\ufeffmethod", "")); if ("ctm".equalsIgnoreCase(method)) found.add(new Rule(props.getProperty("matchTiles", props.getProperty("\ufeffmatchTiles", "")), tiles(id, props.getProperty("tiles", props.getProperty("\ufefftiles", "0-46"))))); }
                    } catch (Exception error) { LOGGER.warn("Failed to parse generated CTM rule file {}", p, error); }
                });
            }
        } catch (Exception ignored) {}
        rules = List.copyOf(found); LOGGER.info("Loaded {} legacy CTM rules", rules.size());
    }

    @SubscribeEvent
    public static void models(ModelEvent.ModifyBakingResult event) {
        if (rules.isEmpty()) return;
        int wrapped = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            Map<ResourceLocation, Rule> matches = new HashMap<>();
            for (BakedQuad quad : allQuads(entry.getValue())) {
                ResourceLocation sprite = quad.getSprite().contents().name();
                for (Rule rule : rules) if (rule.matches(sprite)) {
                    matches.put(sprite, rule); break;
                }
            }
            if (!matches.isEmpty()) {
                event.getModels().put(entry.getKey(), new ModelBakedCTM(new LegacyModel(matches), entry.getValue(), null));
                wrapped++;
            }
        }
        LOGGER.info("Wrapped {} baked models for {} legacy CTM rules", wrapped, rules.size());
    }

    private static List<BakedQuad> allQuads(BakedModel model) { List<BakedQuad> q = new ArrayList<>(); for (Direction d : Direction.values()) q.addAll(model.getQuads(null, d, net.minecraft.util.RandomSource.create(), ModelData.EMPTY, null)); q.addAll(model.getQuads(null, null, net.minecraft.util.RandomSource.create(), ModelData.EMPTY, null)); return q; }
    private static ResourceLocation tileId(ResourceLocation id, String path, int index) {
        if ("continuity".equals(id.getNamespace())) return new ResourceLocation("minecraft", "ctm/" + path.substring("optifine/ctm/".length()) + "/" + index);
        return new ResourceLocation("minecraft", "ctm/" + id.getNamespace() + "/" + path.substring("optifine/ctm/".length()) + "/" + index);
    }

    private static List<ResourceLocation> tiles(ResourceLocation id, String value) { String path = id.getPath().substring(0, id.getPath().lastIndexOf('/')); List<ResourceLocation> result = new ArrayList<>(); for (String token : value.split("[, ]+")) try { if (token.contains("-")) { String[] r = token.split("-", 2); for (int i = Integer.parseInt(r[0]); i <= Integer.parseInt(r[1]); i++) result.add(tileId(id, path, i)); } else result.add(tileId(id, path, Integer.parseInt(token))); } catch (NumberFormatException ignored) {} return result; }
    private record Rule(String matchTiles, List<ResourceLocation> tiles) { boolean matches(ResourceLocation id) { String path = id.getPath(); String name = path.substring(path.lastIndexOf('/') + 1); for (String m : matchTiles.split("[, ]+")) if (m.equals(name) || m.equals(path)) return true; return false; } }

    private static ICTMTexture<?> nativeTexture(Rule rule) {
        TextureAtlasSprite[] sprites = rule.tiles.stream().map(id -> Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id)).toArray(TextureAtlasSprite[]::new);
        return new OverlayTexture(new TextureTypeCTM(), new TextureInfo(sprites, java.util.Optional.empty(), null, false));
    }

    private static final class OverlayTexture extends TextureCTM<TextureTypeCTM> {
        OverlayTexture(TextureTypeCTM type, TextureInfo info) { super(type, info); }
        @Override public List<BakedQuad> transformQuad(BakedQuad quad, @Nullable ITextureContext context, int quadGoal) {
            List<BakedQuad> overlay = super.transformQuad(quad, context, quadGoal);
            if (context == null || overlay.isEmpty()) return overlay;
            List<BakedQuad> result = new ArrayList<>(overlay.size() + 1);
            result.add(quad);
            result.addAll(overlay);
            return result;
        }
    }

    private static final class LegacyModel implements IModelCTM {
        private final Map<ResourceLocation, Rule> rules;
        private final Map<ResourceLocation, ICTMTexture<?>> textures = new HashMap<>();
        LegacyModel(Map<ResourceLocation, Rule> t) { rules=t; }
        private ICTMTexture<?> texture(ResourceLocation id) { return textures.computeIfAbsent(id, key -> nativeTexture(rules.get(key))); }
        public Collection<ICTMTexture<?>> getCTMTextures() { rules.keySet().forEach(this::texture); return ImmutableList.copyOf(textures.values()); }
        public ICTMTexture<?> getTexture(ResourceLocation id) { return rules.containsKey(id) ? texture(id) : null; }
        public Set<RenderType> getExtraLayers(net.minecraft.world.level.block.state.BlockState s) { return Set.of(); }
        public TextureAtlasSprite getOverrideSprite(int i) { return null; }
        public ICTMTexture<?> getOverrideTexture(int i, ResourceLocation id) { return null; }
        public void load() {}
        public void resolveParents(Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> f, net.minecraftforge.client.model.geometry.IGeometryBakingContext c) {}
        public net.minecraft.client.resources.model.BakedModel bake(net.minecraftforge.client.model.geometry.IGeometryBakingContext c, net.minecraft.client.resources.model.ModelBaker b, Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> f, net.minecraft.client.resources.model.ModelState s, net.minecraft.client.renderer.block.model.ItemOverrides o, ResourceLocation id) { return null; }
    }
}
