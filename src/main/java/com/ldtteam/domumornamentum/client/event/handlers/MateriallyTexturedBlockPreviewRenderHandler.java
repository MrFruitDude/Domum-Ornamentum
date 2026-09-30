package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.client.render.ModelGhostRenderer;
import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.domumornamentum.util.ItemStackUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class MateriallyTexturedBlockPreviewRenderHandler {

    /**
     * Since 26.x world geometry is only drawn when it is submitted while the frame is collected; anything drawn from
     * RenderLevelStageEvent is discarded.
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(final SubmitCustomGeometryEvent event) {
        renderMateriallyTexturedBlockPreview(event.getPoseStack(), event.getSubmitNodeCollector());
    }

    public static void renderMateriallyTexturedBlockPreview(final PoseStack poseStack, final SubmitNodeCollector collector) {
        final HitResult rayTraceResult = Minecraft.getInstance().hitResult;
        if (!(rayTraceResult instanceof final BlockHitResult blockRayTraceResult) || blockRayTraceResult.getType() == HitResult.Type.MISS)
            return;

        final Player playerEntity = Minecraft.getInstance().player;
        if (playerEntity == null || playerEntity.isSpectator())
            return;

        final ItemStack heldStack = ItemStackUtils.getMateriallyTexturedItemStackFromPlayer(playerEntity);
        if (heldStack.isEmpty())
            return;

        Vec3 targetedRenderPos = Vec3.atLowerCornerOf(blockRayTraceResult.getBlockPos().offset(blockRayTraceResult.getDirection().getUnitVec3i()));
        renderGhost(poseStack, collector, heldStack, targetedRenderPos, blockRayTraceResult, Minecraft.getInstance().level);
    }

    private static void renderGhost(
            final PoseStack poseStack,
            final SubmitNodeCollector collector,
            final ItemStack heldStack,
            final Vec3 targetedRenderPos, BlockHitResult blockRayTraceResult, ClientLevel level) {
        ModelGhostRenderer.getInstance().renderGhost(
                poseStack,
                collector,
                heldStack,
                targetedRenderPos,
                blockRayTraceResult,
                level,
                false
        );
    }

}
