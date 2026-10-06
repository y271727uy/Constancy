package org.thinkingstudio.constancy.compat.ctm;

import java.util.Arrays;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.model.IQuadTransformer;

/** Correctly remaps both U and V coordinates to a replacement sprite. */
public final class SafeBakedQuadRetextured extends BakedQuad {
    private final TextureAtlasSprite texture;
    public SafeBakedQuadRetextured(BakedQuad quad, TextureAtlasSprite replacement) {
        super(Arrays.copyOf(quad.getVertices(), quad.getVertices().length), quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
        this.texture = replacement;
        for (int i = 0; i < 4; i++) {
            int offset = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float u = Float.intBitsToFloat(vertices[offset]);
            float v = Float.intBitsToFloat(vertices[offset + 1]);
            vertices[offset] = Float.floatToRawIntBits(replacement.getU(sprite.getUOffset(u)));
            vertices[offset + 1] = Float.floatToRawIntBits(replacement.getV(sprite.getVOffset(v)));
        }
    }
    @Override public TextureAtlasSprite getSprite() { return texture; }
}
