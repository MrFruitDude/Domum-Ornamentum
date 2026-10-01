package com.ldtteam.domumornamentum.util;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.event.handlers.ClientTickEventHandler;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

@EventBusSubscriber(modid = Constants.MOD_ID)
public class MaterialTextureDataUtil
{
    /**
     * port26.3: the 26.x item model asks for the cycling preview materials of every rendered item without data on every
     * frame, while the result only changes once per second. Remember the last result per block together with the second
     * it belongs to, so the tag streams run once per block per second instead of once per item per frame.
     */
    private static final Map<Block, CycledData> CYCLED_CACHE = new ConcurrentHashMap<>();

    private record CycledData(long second, MaterialTextureData data) {}

    /**
     * Skin tags changed: the cached candidates may be stale (a dedicated server never advances the second).
     */
    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event)
    {
        CYCLED_CACHE.clear();
    }

    private MaterialTextureDataUtil()
    {
        throw new IllegalStateException("Can not instantiate an instance of: MaterialTextureDataUtil. This is a utility class");
    }

    public static MaterialTextureData generateRandomTextureDataFrom(final ItemStack stack) {
        final Item item = stack.getItem();
        if (!(item instanceof BlockItem blockItem))
            return MaterialTextureData.EMPTY;

        final Block block = blockItem.getBlock();
        return generateRandomTextureDataFrom(block);
    }

    @NotNull
    public static MaterialTextureData generateRandomTextureDataFrom(final Block block)
    {
        return generateRandomTextureDataFrom(block, ClientTickEventHandler.getInstance().getNonePausedTicks() / 20);
    }

    /**
     * The cycled preview materials of a block for one second of non-paused client time, cached per block.
     */
    @NotNull
    public static MaterialTextureData generateRandomTextureDataFrom(final Block block, final long second)
    {
        if (!(block instanceof IMateriallyTexturedBlock))
            return MaterialTextureData.EMPTY;

        final CycledData cached = CYCLED_CACHE.get(block);
        if (cached != null && cached.second() == second)
        {
            return cached.data();
        }

        final MaterialTextureData data = computeRandomTextureData(block, second);
        if (data != null)
        {
            CYCLED_CACHE.put(block, new CycledData(second, data));
            return data;
        }
        // Lookup failed (e.g. tags not bound yet): not cached, so the next call retries.
        return MaterialTextureData.EMPTY;
    }

    /**
     * Uncached computation (the 1.21 body), exposed for the equivalence test.
     *
     * @return the data, or null when the tag lookup failed.
     */
    public static MaterialTextureData computeRandomTextureData(final Block block, final long second)
    {
        if (!(block instanceof IMateriallyTexturedBlock materiallyTexturedBlock))
            return MaterialTextureData.EMPTY;

        try {
            final MaterialTextureData.Builder newData = MaterialTextureData.builder();

            int localOffset = BuiltInRegistries.BLOCK.getId(block);
            int offsetIndex = 0;
            for (IMateriallyTexturedBlockComponent component : materiallyTexturedBlock.getComponents())
            {
                final List<Block> candidates = new ArrayList<>(
                  StreamSupport
                    .stream(BuiltInRegistries.BLOCK.getTagOrEmpty(component.getValidSkins()).spliterator(), false)
                    .map(Holder::value).toList());
                if (candidates.isEmpty())
                {
                    continue;
                }

                final int index = (int) ((second + (offsetIndex += localOffset)) % candidates.size());
                final Block texture = candidates.get(index);
                newData.setComponent(component.getId(), texture);
            }

            return newData.build();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
