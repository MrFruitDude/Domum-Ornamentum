package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.recipe.architectscutter.ClientArchitectsCutterRecipes;
import com.ldtteam.domumornamentum.util.Constants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * Keeps {@link ClientArchitectsCutterRecipes} in step with the recipes the server syncs. JEI starts its runtime from the same
 * event at LOWEST priority, so this default-priority listener has filled the list before JEI's registerRecipes reads it.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipesEventHandler
{
    private ClientRecipesEventHandler()
    {
    }

    @SubscribeEvent
    public static void onRecipesReceived(final RecipesReceivedEvent event)
    {
        ClientArchitectsCutterRecipes.update(event.getRecipeMap());
    }

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientArchitectsCutterRecipes.update(null);
    }
}
