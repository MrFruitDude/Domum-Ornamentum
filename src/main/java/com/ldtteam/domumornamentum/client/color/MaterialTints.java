package com.ldtteam.domumornamentum.client.color;

import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Material tinting for materially textured blocks and items.
 *
 * <p>1.21 packed the skin's block-state id into each retextured quad's tint index and let one colour handler decode it.
 * Since 1.21.4 a quad's tint index selects an entry from a fixed per-block list of tint sources, so that encoding no
 * longer fits. Instead every distinct skin block of a {@link MaterialTextureData} gets a slot of
 * {@link #LAYERS_PER_SKIN} tint indices: a retextured quad keeps the skin's own tint layer, offset by the slot. The
 * colours for those indices are produced per block (NeoForge's dynamic tint values) and per item stack, by asking the
 * skin block's own tint sources, like 1.21 asked its colour handlers.</p>
 */
public final class MaterialTints
{
    /** Tint layers reserved per skin block; vanilla blocks use at most two. */
    public static final int LAYERS_PER_SKIN = 8;

    private static final Comparator<Map.Entry<Identifier, Block>> BY_COMPONENT = Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString));

    private MaterialTints()
    {
    }

    /**
     * The tint index a quad retextured with {@code skin} must use.
     *
     * @param skinTintIndex the tint index of the skin block's own quad.
     * @return the remapped index, or -1 (untinted) when the skin quad is untinted.
     */
    public static int remapTintIndex(final MaterialTextureData textureData, final Block skin, final int skinTintIndex)
    {
        if (skinTintIndex < 0 || skinTintIndex >= LAYERS_PER_SKIN)
        {
            return -1;
        }
        final int slot = skins(textureData).indexOf(skin);
        return slot < 0 ? -1 : slot * LAYERS_PER_SKIN + skinTintIndex;
    }

    /**
     * Append the tint values for every index {@link #remapTintIndex} can produce for this texture data.
     *
     * @param level the level to tint in, or null for the level-independent colours (items, non-world tinting).
     */
    public static void collect(
        final MaterialTextureData textureData,
        final BlockColors blockColors,
        @Nullable final BlockAndTintGetter level,
        @Nullable final BlockPos pos,
        final IntList tintValues)
    {
        for (final Block skin : skins(textureData))
        {
            final BlockState skinState = skin.defaultBlockState();
            final List<BlockTintSource> sources = blockColors.getTintSources(skinState);
            for (int layer = 0; layer < LAYERS_PER_SKIN; layer++)
            {
                final BlockTintSource source = layer < sources.size() ? sources.get(layer) : null;
                if (source == null)
                {
                    tintValues.add(-1);
                }
                else
                {
                    tintValues.add(level == null || pos == null ? source.color(skinState) : source.colorInWorld(skinState, level, pos));
                }
            }
        }
    }

    /** Distinct skin blocks in a stable order (by component id), so the model and the colours agree on slots. */
    private static List<Block> skins(final MaterialTextureData textureData)
    {
        final List<Block> skins = new ArrayList<>();
        textureData.getTexturedComponents().entrySet().stream().sorted(BY_COMPONENT).forEach(entry -> {
            final Block skin = entry.getValue();
            if (skin != Blocks.AIR && !skins.contains(skin))
            {
                skins.add(skin);
            }
        });
        return skins;
    }
}
