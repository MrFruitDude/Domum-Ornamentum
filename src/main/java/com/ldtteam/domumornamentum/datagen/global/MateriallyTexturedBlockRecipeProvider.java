package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import com.ldtteam.domumornamentum.datagen.global.DomumRecipeProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MateriallyTexturedBlockRecipeProvider extends DomumRecipeProvider
{

    public MateriallyTexturedBlockRecipeProvider(final BootstrapContext<Recipe<?>> recipes, final BootstrapContext<Advancement> advancements)
    {
        super(recipes, advancements);
    }

    @Override
    protected void buildRecipes()
    {
        BuiltInRegistries.BLOCK.forEach(
                block -> {
                    if (Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block)).getNamespace().equals(Constants.MOD_ID) && block instanceof IMateriallyTexturedBlock materiallyTexturedBlock) {
                        materiallyTexturedBlock.buildRecipes(this.output);
                    }
                }
        );
    }
}
