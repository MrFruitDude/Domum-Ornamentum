package com.ldtteam.domumornamentum.recipe.architectscutter;

import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Architect's Cutter recipes the client last received from the server (recipes are server-only since 1.21.2). Filled from
 * the client's RecipesReceivedEvent, read by the JEI plugin.
 */
public final class ClientArchitectsCutterRecipes
{
    private static volatile List<RecipeHolder<ArchitectsCutterRecipe>> recipes = List.of();

    private ClientArchitectsCutterRecipes()
    {
    }

    /**
     * Replace the known recipes with the ones in the given synced recipe map; null clears them (disconnect).
     */
    public static void update(@Nullable final RecipeMap recipeMap)
    {
        recipes = recipeMap == null ? List.of() : List.copyOf(recipeMap.byType(ModRecipeTypes.ARCHITECTS_CUTTER.get()));
    }

    public static List<RecipeHolder<ArchitectsCutterRecipe>> get()
    {
        return recipes;
    }
}
