package com.ldtteam.domumornamentum.client.render;

import com.ldtteam.domumornamentum.util.ItemStackUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws the translucent preview of the materially textured block the player is about to place.
 */
public final class ModelGhostRenderer {
    private static final ModelGhostRenderer INSTANCE = new ModelGhostRenderer();
    private static final int GHOST_ALPHA = 128;
    private static final int FULL_BRIGHT = 15728880;

    private ModelGhostRenderer() {}

    public static ModelGhostRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * What the held stack would place, and a view of the level in which it is placed: the preview's state and the
     * block entity built from the stack answer at the placement position, so the model reads the stack's materials.
     */
    public record Preview(BlockPos pos, BlockState state, BlockAndTintGetter level) {}

    @Nullable
    public static Preview prepare(
        final Player player,
        final InteractionHand hand,
        final ItemStack stack,
        final BlockHitResult blockHitResult,
        final BlockAndTintGetter level
    ) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        final BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, blockHitResult);
        BlockState state = blockItem.getBlock().getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(state);

        final BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = null;
        if (blockItem.getBlock() instanceof EntityBlock entityBlock) {
            blockEntity = entityBlock.newBlockEntity(pos, state);
            if (blockEntity != null) {
                blockEntity.applyComponentsFromItemStack(stack);
            }
        }
        return new Preview(pos, state, new PreviewLevel(level, pos, state, blockEntity));
    }

    /**
     * Submits the ghost for this frame.
     *
     * @param poseStack camera-relative pose of the submit event.
     */
    public void renderGhost(
        final PoseStack poseStack,
        final SubmitNodeCollector collector,
        final ItemStack renderStack,
        final Vec3 targetedRenderPos,
        final BlockHitResult blockHitResult,
        final ClientLevel level,
        final boolean ignoreDepth
    ) {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = Objects.requireNonNull(mc.player);
        final InteractionHand hand = Objects.requireNonNull(ItemStackUtils.getHandWithMateriallyTexturedItemStackFromPlayer(player));
        final Preview preview = prepare(player, hand, renderStack, blockHitResult, level);
        if (preview == null) {
            return;
        }

        final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(preview.state());
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(preview.level(), preview.pos(), preview.state(), RandomSource.create(42L), parts);
        if (parts.isEmpty()) {
            return;
        }

        final IntList tints = new IntArrayList();
        IClientBlockExtensions.of(preview.state()).collectDynamicTintValues(preview.state(), preview.level(), preview.pos(), tints);

        poseStack.pushPose();
        // Offset/scale by an unnoticeable amount to prevent z-fighting
        final Vec3 camera = mc.gameRenderer.mainCamera().position();
        poseStack.translate(
            targetedRenderPos.x - camera.x - 0.000125F,
            targetedRenderPos.y - camera.y + 0.000125F,
            targetedRenderPos.z - camera.z - 0.000125F
        );
        poseStack.scale(1.001F, 1.001F, 1.001F);
        // The collector copies the pose now and runs the callback when the frame is drawn.
        collector.submitCustomGeometry(poseStack, ModRenderTypes.GHOST_BLOCK_PREVIEW.get(), (pose, consumer) -> putParts(consumer, pose, parts, tints));
        poseStack.popPose();
    }

    private static void putParts(final VertexConsumer consumer, final PoseStack.Pose pose, final List<BlockStateModelPart> parts, final IntList tints) {
        final QuadInstance quadInstance = new QuadInstance();
        quadInstance.setLightCoords(FULL_BRIGHT);
        for (final BlockStateModelPart part : parts) {
            for (final Direction direction : Direction.values()) {
                putQuads(consumer, pose, quadInstance, part.getQuads(direction), tints);
            }
            putQuads(consumer, pose, quadInstance, part.getQuads(null), tints);
        }
    }

    private static void putQuads(
        final VertexConsumer consumer,
        final PoseStack.Pose pose,
        final QuadInstance quadInstance,
        final List<BakedQuad> quads,
        final IntList tints
    ) {
        for (final BakedQuad quad : quads) {
            quadInstance.setColor(ghostColor(quad.materialInfo().tintIndex(), tints));
            consumer.putBakedQuad(pose, quad, quadInstance);
        }
    }

    /** The quad's tint colour (white when untinted) at the ghost's half transparency. */
    public static int ghostColor(final int tintIndex, final IntList tints) {
        final int rgb = tintIndex >= 0 && tintIndex < tints.size() ? tints.getInt(tintIndex) : -1;
        return ARGB.color(GHOST_ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /** The level as seen by the preview: the previewed block is in place at its position. */
    private record PreviewLevel(BlockAndTintGetter level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) implements BlockAndTintGetter {
        @Override
        public @Nullable BlockEntity getBlockEntity(final BlockPos at) {
            return pos.equals(at) ? blockEntity : level.getBlockEntity(at);
        }

        @Override
        public BlockState getBlockState(final BlockPos at) {
            return pos.equals(at) ? state : level.getBlockState(at);
        }

        @Override
        public FluidState getFluidState(final BlockPos at) {
            return pos.equals(at) ? state.getFluidState() : level.getFluidState(at);
        }

        @Override
        public ModelData getModelData(final BlockPos at) {
            if (pos.equals(at)) {
                return blockEntity == null ? ModelData.EMPTY : blockEntity.getModelData();
            }
            return level.getModelData(at);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return level.cardinalLighting();
        }

        @Override
        public int getBlockTint(final BlockPos at, final ColorResolver resolver) {
            return level.getBlockTint(at, resolver);
        }
    }
}
