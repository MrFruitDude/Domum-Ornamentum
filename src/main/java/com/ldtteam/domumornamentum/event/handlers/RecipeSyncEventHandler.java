package com.ldtteam.domumornamentum.event.handlers;

import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.util.Constants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Since 1.21.2 the server only sends clients the recipe types a mod asks for. Request the Architect's Cutter type so client
 * side recipe viewers (JEI) see the recipes even when no other mod on the server requests them.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class RecipeSyncEventHandler
{
    private RecipeSyncEventHandler()
    {
    }

    @SubscribeEvent
    public static void onDatapackSync(final OnDatapackSyncEvent event)
    {
        event.sendRecipes(ModRecipeTypes.ARCHITECTS_CUTTER.get());
    }
}
