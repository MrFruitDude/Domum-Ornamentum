package com.ldtteam.domumornamentum.client.model.item;

import com.google.common.base.Suppliers;
import com.ldtteam.domumornamentum.DomumOrnamentum;
import com.ldtteam.domumornamentum.client.color.MaterialTints;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Item-model counterpart to the materialized block model.
 *
 * <p>26.2 no longer exposes the legacy {@code BakedModel#getOverrides()} path
 * used by Domum Ornamentum to retexture item stacks. This model keeps the
 * normal baked geometry and remaps only the sprites named by the stack's
 * {@link MaterialTextureData}. UVs, normals, colours and transforms remain
 * intact through NeoForge's {@link MutableQuad}.</p>
 */
public final class MateriallyTexturedItemModel implements ItemModel
{
    private final List<ItemTintSource> tints;
    private final QuadCollection baseQuads;
    private final Supplier<Vector3fc[]> extents;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final ConcurrentMap<MaterialTextureData, QuadCollection> retexturedQuads = new ConcurrentHashMap<>();
    /** MC 26.3: item layers take pre-split (solid/translucent) quads; cache the split per quad collection. */
    private final ConcurrentMap<QuadCollection, ItemQuads> itemQuads = new ConcurrentHashMap<>();
    private final ConcurrentMap<Block, TargetTextures> targetTextures = new ConcurrentHashMap<>();
    /** Target blocks whose texture load already failed once and was logged, so a retry every frame stays quiet. */
    private static final Set<Block> LOGGED_LOAD_FAILURES = ConcurrentHashMap.newKeySet();

    private MateriallyTexturedItemModel(
        final List<ItemTintSource> tints,
        final QuadCollection baseQuads,
        final ModelRenderProperties properties,
        final Matrix4fc transformation
    )
    {
        this.tints = tints;
        this.baseQuads = baseQuads;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = Suppliers.memoize(() -> computeExtents(baseQuads.getAll()));
    }

    private static Vector3fc[] computeExtents(final List<BakedQuad> quads)
    {
        final Set<Vector3fc> result = new HashSet<>();
        for (final BakedQuad quad : quads)
        {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++)
            {
                result.add(quad.position(vertex));
            }
        }
        return result.toArray(Vector3fc[]::new);
    }

    @Override
    public void update(
        final ItemStackRenderState output,
        final ItemStack item,
        final ItemModelResolver resolver,
        final ItemDisplayContext displayContext,
        @Nullable final ClientLevel level,
        @Nullable final ItemOwner owner,
        final int seed
    )
    {
        output.appendModelIdentityElement(this);
        final ItemStackRenderState.LayerRenderState layer = output.newLayer();

        if (item.hasFoil())
        {
            final ItemStackRenderState.FoilType foilType = hasSpecialAnimatedTexture(item)
                ? ItemStackRenderState.FoilType.SPECIAL
                : ItemStackRenderState.FoilType.STANDARD;
            layer.setFoilType(foilType);
            output.setAnimated();
            output.appendModelIdentityElement(foilType);
        }

        MaterialTextureData textureData = MaterialTextureData.readFromItemStack(item);
        if (textureData.isEmpty())
        {
            textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(item);
        }
        // The GUI item atlas reuses one icon per model identity, so the materials must be part of it; otherwise
        // every stack of this item whose tint layers match shares whichever icon was drawn first.
        output.appendModelIdentityElement(textureData);

        final IntList tintLayers = layer.tintLayers();
        if (textureData.isEmpty())
        {
            for (final ItemTintSource tintSource : this.tints)
            {
                tintLayers.add(tintSource.calculate(item, level, owner == null ? null : owner.asLivingEntity()));
            }
        }
        else
        {
            // Retextured quads carry the skin's tint layers (see MaterialTints#remapTintIndex).
            MaterialTints.collect(textureData, Minecraft.getInstance().getBlockColors(), null, null, tintLayers);
        }
        for (int i = 0; i < tintLayers.size(); i++)
        {
            output.appendModelIdentityElement(tintLayers.getInt(i));
        }

        QuadCollection quads = textureData.isEmpty() ? this.baseQuads : this.retexturedQuads.get(textureData);
        boolean cacheable = true;
        if (quads == null)
        {
            final boolean[] incomplete = new boolean[1];
            quads = buildRetexturedQuads(textureData, incomplete);
            cacheable = !incomplete[0];
            if (cacheable)
            {
                // Only cache a result whose target textures all loaded, so a transient load failure is retried.
                this.retexturedQuads.putIfAbsent(textureData, quads);
            }
        }

        layer.setExtents(this.extents);
        layer.setLocalTransform(this.transformation);
        this.properties.applyToLayer(layer, displayContext);
        if (!textureData.isEmpty())
        {
            remapParticleMaterial(layer, textureData);
        }
        layer.setQuads(cacheable ? this.itemQuads.computeIfAbsent(quads, q -> ItemQuads.split(q.getAll())) : ItemQuads.split(quads.getAll()));
        if (quads.hasMaterialFlag(BakedQuad.FLAG_ANIMATED))
        {
            output.setAnimated();
        }
    }

    private void remapParticleMaterial(
        final ItemStackRenderState.LayerRenderState layer,
        final MaterialTextureData textureData
    )
    {
        final Material.Baked source = this.properties.particleMaterial();
        final Block target = textureData.getTexturedComponents().get(source.sprite().contents().name());
        if (target == null || target == Blocks.AIR)
        {
            return;
        }

        final TargetTextures textures = targetTextures(target);
        final Material.Baked replacement = textures == null ? null : textures.particleMaterial();
        if (replacement != null)
        {
            layer.setParticleMaterial(replacement);
        }
    }

    private QuadCollection buildRetexturedQuads(final MaterialTextureData textureData, final boolean[] incomplete)
    {
        final QuadCollection.Builder builder = new QuadCollection.Builder();
        boolean changed = false;

        for (final BakedQuad quad : this.baseQuads.getQuads(null))
        {
            final BakedQuad replacement = remapQuad(quad, textureData, incomplete);
            if (replacement != null)
            {
                builder.addUnculledFace(replacement);
            }
            changed |= replacement != quad;
        }

        for (final Direction direction : Direction.values())
        {
            for (final BakedQuad quad : this.baseQuads.getQuads(direction))
            {
                final BakedQuad replacement = remapQuad(quad, textureData, incomplete);
                if (replacement != null)
                {
                    builder.addCulledFace(direction, replacement);
                }
                changed |= replacement != quad;
            }
        }

        return changed ? builder.build() : this.baseQuads;
    }

    /**
     * Remaps one quad to its component's target texture. Returns {@code null} when the component is set to
     * AIR (no material): the quad is erased, like the block model and 1.21's RetexturedBakedModelBuilder.
     */
    @Nullable
    private BakedQuad remapQuad(final BakedQuad source, final MaterialTextureData textureData, final boolean[] incomplete)
    {
        final Identifier sourceTexture = source.materialInfo().sprite().contents().name();
        final Block target = textureData.getTexturedComponents().get(sourceTexture);
        if (target == null)
        {
            return source;
        }
        if (target == Blocks.AIR)
        {
            return null;
        }

        final TargetTextures textures = targetTextures(target);
        if (textures == null)
        {
            incomplete[0] = true;
            return source;
        }
        final TargetSprite targetSprite = textures.forDirection(source.direction());
        if (targetSprite == null)
        {
            return source;
        }

        final BakedQuad.MaterialInfo targetInfo = targetSprite.quad().materialInfo();
        return new MutableQuad()
            .setFrom(source)
            .setSpriteAndMoveUv(targetInfo.sprite(), targetInfo.layer(), targetInfo.itemRenderType(), targetInfo.itemGlintRenderType(), targetInfo.itemGlintSpecialRenderType())
            .setTintIndex(MaterialTints.remapTintIndex(textureData, target, targetInfo.tintIndex()))
            .setShadeOverride(targetInfo.shadeDirectionOverride())
            .setLightEmission(targetInfo.lightEmission())
            .setAmbientOcclusion(targetInfo.ambientOcclusion())
            .toBakedQuad();
    }

    /**
     * The target block's textures, cached for this bake. A failed load is not cached (the next frame retries), so an
     * early-reload hiccup cannot leave a material untextured until the next resource reload.
     */
    @Nullable
    private TargetTextures targetTextures(final Block target)
    {
        return this.targetTextures.computeIfAbsent(target, MateriallyTexturedItemModel::loadTargetTextures);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static TargetTextures loadTargetTextures(final Block block)
    {
        try
        {
            final BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(block.defaultBlockState());
            final List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(0L), parts);
            final EnumMap<Direction, TargetSprite> byDirection = new EnumMap<>(Direction.class);
            TargetSprite unculled = null;

            for (final BlockStateModelPart part : parts)
            {
                final List<BakedQuad> unculledQuads = part.getQuads(null);
                if (unculled == null && !unculledQuads.isEmpty())
                {
                    unculled = targetSprite(unculledQuads.getFirst());
                }

                for (final Direction direction : Direction.values())
                {
                    if (byDirection.containsKey(direction))
                    {
                        continue;
                    }
                    final List<BakedQuad> directionalQuads = part.getQuads(direction);
                    if (!directionalQuads.isEmpty())
                    {
                        byDirection.put(direction, targetSprite(directionalQuads.getFirst()));
                    }
                }
            }

            final Material.Baked particle = model.particleMaterial();
            return new TargetTextures(byDirection, unculled, particle);
        }
        catch (final RuntimeException e)
        {
            // A target can be unavailable during an early resource reload. Keep the
            // source quad for now and return null so computeIfAbsent stores nothing.
            if (LOGGED_LOAD_FAILURES.add(block))
            {
                DomumOrnamentum.LOGGER.warn("Could not load textures of {} for a materially textured item; retrying", block, e);
            }
            return null;
        }
    }

    private static TargetSprite targetSprite(final BakedQuad quad)
    {
        return new TargetSprite(quad);
    }

    private static boolean hasSpecialAnimatedTexture(final ItemStack itemStack)
    {
        return itemStack.is(ItemTags.COMPASSES) || itemStack.is(Items.CLOCK);
    }

    private record TargetSprite(BakedQuad quad)
    {
    }

    private record TargetTextures(
        Map<Direction, TargetSprite> byDirection,
        @Nullable TargetSprite unculled,
        Material.@Nullable Baked particleMaterial
    )
    {
        @Nullable
        private TargetSprite forDirection(final Direction direction)
        {
            if (direction != null)
            {
                final TargetSprite directional = this.byDirection.get(direction);
                if (directional != null)
                {
                    return directional;
                }
            }
            return this.unculled;
        }
    }

    public record Unbaked(
        Identifier model,
        Optional<Transformation> transformation,
        List<ItemTintSource> tints
    ) implements ItemModel.Unbaked
    {
        public static final MapCodec<MateriallyTexturedItemModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Identifier.CODEC.fieldOf("model").forGetter(MateriallyTexturedItemModel.Unbaked::model),
                    Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(MateriallyTexturedItemModel.Unbaked::transformation),
                    ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(MateriallyTexturedItemModel.Unbaked::tints)
                )
                .apply(i, MateriallyTexturedItemModel.Unbaked::new)
        );

        @Override
        public void resolveDependencies(final ResolvableModel.Resolver resolver)
        {
            resolver.markDependency(this.model);
        }

        @Override
        public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation)
        {
            final ModelBaker baker = context.blockModelBaker();
            final ResolvedModel resolvedModel = baker.getModel(this.model);
            final TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
            final QuadCollection quads = resolvedModel.bakeTopGeometry(textureSlots, baker, BlockModelRotation.IDENTITY);
            final ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolvedModel, textureSlots);
            final Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
            return new MateriallyTexturedItemModel(this.tints, quads, properties, modelTransform);
        }

        @Override
        public MapCodec<MateriallyTexturedItemModel.Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
