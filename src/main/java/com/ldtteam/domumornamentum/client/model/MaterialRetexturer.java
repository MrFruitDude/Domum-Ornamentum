package com.ldtteam.domumornamentum.client.model;

import com.ldtteam.domumornamentum.client.color.MaterialTints;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Sprite remapping shared by the materially textured block and item models: a skin block's sprites are
 * scanned once into {@link TargetTextures}, and each source quad whose sprite names a textured component
 * is moved onto the skin's sprite for its face.
 */
public final class MaterialRetexturer
{
    private static final Direction[] DIRECTIONS = Direction.values();

    private MaterialRetexturer()
    {
    }

    /**
     * The first quad of each face of a skin block's model (plus its first unculled quad), and its particle.
     *
     * @param byDirection first quad per {@link Direction#ordinal()}, {@code null} where the skin has no such face.
     */
    public record TargetTextures(@Nullable BakedQuad[] byDirection, @Nullable BakedQuad unculled, Material.@Nullable Baked particleMaterial)
    {
        public static final TargetTextures EMPTY = new TargetTextures(new BakedQuad[DIRECTIONS.length], null, null);

        /** Scan a skin model's parts; the first quad found for each face wins, in part order. */
        public static TargetTextures of(final List<BlockStateModelPart> parts, final Material.@Nullable Baked particleMaterial)
        {
            final BakedQuad[] byDirection = new BakedQuad[DIRECTIONS.length];
            BakedQuad unculled = null;
            for (final BlockStateModelPart part : parts)
            {
                if (unculled == null)
                {
                    final List<BakedQuad> unculledQuads = part.getQuads(null);
                    if (!unculledQuads.isEmpty())
                    {
                        unculled = unculledQuads.getFirst();
                    }
                }
                for (final Direction direction : DIRECTIONS)
                {
                    if (byDirection[direction.ordinal()] == null)
                    {
                        final List<BakedQuad> directionalQuads = part.getQuads(direction);
                        if (!directionalQuads.isEmpty())
                        {
                            byDirection[direction.ordinal()] = directionalQuads.getFirst();
                        }
                    }
                }
            }
            return new TargetTextures(byDirection, unculled, particleMaterial);
        }

        /**
         * The skin quad to take the sprite from: the cull face's quad when meshing a culled face, else the quad
         * facing the source quad's normal, else the skin's unculled quad.
         */
        @Nullable
        public BakedQuad forDirection(@Nullable final Direction cullFace, @Nullable final Direction normal)
        {
            if (cullFace != null && byDirection[cullFace.ordinal()] != null)
            {
                return byDirection[cullFace.ordinal()];
            }
            final BakedQuad normalQuad = normal == null ? null : byDirection[normal.ordinal()];
            return normalQuad == null ? unculled : normalQuad;
        }
    }

    /**
     * Remap one quad onto its component's skin.
     *
     * @param textures the skin's textures; may return {@code null} when they are not available yet, which keeps the
     *                 source quad.
     * @param cullFace the face being meshed, or {@code null} for unculled quads and item models.
     * @return the source quad when its sprite is not a textured component (or the skin has no matching face),
     *         {@code null} when the component is set to AIR (the face is erased, like 1.21), else the retextured quad.
     */
    @Nullable
    public static BakedQuad remap(
        final BakedQuad source,
        final MaterialTextureData textureData,
        final Function<Block, @Nullable TargetTextures> textures,
        @Nullable final Direction cullFace
    )
    {
        final Identifier sourceTexture = source.materialInfo().sprite().contents().name();
        final Block target = textureData.getTexturedComponents().get(sourceTexture);
        if (target == null)
        {
            return source;
        }
        if (target == Blocks.AIR)
        {
            return null;
        }

        final TargetTextures targetTextures = textures.apply(target);
        final BakedQuad targetQuad = targetTextures == null ? null : targetTextures.forDirection(cullFace, source.direction());
        if (targetQuad == null)
        {
            return source;
        }

        final BakedQuad.MaterialInfo targetInfo = targetQuad.materialInfo();
        return new MutableQuad()
            .setFrom(source)
            .setSpriteAndMoveUv(targetInfo.sprite(), targetInfo.layer(), targetInfo.itemRenderType(), targetInfo.itemGlintRenderType(), targetInfo.itemGlintSpecialRenderType())
            .setTintIndex(MaterialTints.remapTintIndex(textureData, target, targetInfo.tintIndex()))
            .setShadeOverride(targetInfo.shadeDirectionOverride())
            .setLightEmission(targetInfo.lightEmission())
            .setAmbientOcclusion(targetInfo.ambientOcclusion())
            .toBakedQuad();
    }
}
