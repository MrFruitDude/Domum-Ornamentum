package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.client.color.MaterialTints;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.properties.ModProperties;
import com.ldtteam.domumornamentum.util.Constants;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Materially textured blocks take their tint from their skins. The number of tint layers depends on the block's
 * material data, so they use NeoForge's dynamic tint values (which require that no fixed tint source list is
 * registered for these blocks).
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class RegisterColorHandlersEventHandler {

    private static final IClientBlockExtensions MATERIAL_TINTS = new IClientBlockExtensions() {
        @Override
        public void collectDynamicTintValues(final BlockState state, final BlockAndTintGetter level, final BlockPos pos, final IntList tintValues) {
            final MaterialTextureData textureData = level.getModelData(pos).get(ModProperties.MATERIAL_TEXTURE_PROPERTY);
            if (textureData == null || textureData.isEmpty()) {
                return;
            }
            final boolean worldTint = !(state.getBlock() instanceof IMateriallyTexturedBlock block) || block.usesWorldSpecificTinting();
            MaterialTints.collect(textureData, Minecraft.getInstance().getBlockColors(), worldTint ? level : null, worldTint ? pos : null, tintValues);
        }
    };

    @SubscribeEvent
    public static void onRegisterClientExtensions(final RegisterClientExtensionsEvent event) {
        event.registerBlock(MATERIAL_TINTS, ModBlocks.getMateriallyTexturableBlocks());
    }
}
