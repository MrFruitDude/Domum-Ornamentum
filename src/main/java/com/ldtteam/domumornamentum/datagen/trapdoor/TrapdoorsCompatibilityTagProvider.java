package com.ldtteam.domumornamentum.datagen.trapdoor;

import com.ldtteam.domumornamentum.block.ModBlocks;
import static com.ldtteam.domumornamentum.datagen.TagAppenderHelper.addBlocks;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import com.ldtteam.domumornamentum.datagen.tags.BlockTagsProvider;
import com.ldtteam.domumornamentum.datagen.DatagenContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class TrapdoorsCompatibilityTagProvider extends BlockTagsProvider
{
    public TrapdoorsCompatibilityTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable DatagenContext existingFileHelper) {
        super(output, lookupProvider, Constants.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {

        addBlocks(this.tag(BlockTags.TRAPDOORS),
            ModBlocks.getInstance().getTrapdoor()
          );

        addBlocks(this.tag(BlockTags.WOODEN_TRAPDOORS),
            ModBlocks.getInstance().getTrapdoor()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Trapdoor Compatibility Tag Provider";
    }
}
