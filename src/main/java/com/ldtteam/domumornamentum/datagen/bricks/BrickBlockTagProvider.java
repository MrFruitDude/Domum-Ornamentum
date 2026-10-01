package com.ldtteam.domumornamentum.datagen.bricks;

import static com.ldtteam.domumornamentum.datagen.TagAppenderHelper.addBlocks;

import com.ldtteam.domumornamentum.block.IModBlocks;
import com.ldtteam.domumornamentum.tag.ModTags;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import com.ldtteam.domumornamentum.datagen.tags.BlockTagsProvider;
import com.ldtteam.domumornamentum.datagen.DatagenContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BrickBlockTagProvider extends BlockTagsProvider
{

    public BrickBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable DatagenContext existingFileHelper) {
        super(output, lookupProvider, Constants.MOD_ID, existingFileHelper);
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Brick Blocks Tag Provider";
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider holderLookupProvider) {
        final var brickTag = this.tag(ModTags.BRICKS);
        addBlocks(brickTag, IModBlocks.getInstance().getBricks());
        addBlocks(brickTag, IModBlocks.getInstance().getExtraTopBlocks());
    }
}
