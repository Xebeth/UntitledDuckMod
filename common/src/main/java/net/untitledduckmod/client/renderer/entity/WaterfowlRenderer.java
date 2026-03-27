package net.untitledduckmod.client.renderer.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.untitledduckmod.client.model.WaterfowlModel;
import net.untitledduckmod.common.entity.WaterfowlEntity;
import net.untitledduckmod.common.init.ModEntityTypes;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.PerBoneRender;
import software.bernie.geckolib.renderer.base.PerBoneRenderTasks;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Objects;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class WaterfowlRenderer<T extends WaterfowlEntity, R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<T, R> {
    private static final float ADULT_SHADOW_RADIUS = 0.3f;
    private static final boolean RENDER_DEBUG_AXES = false;
    private static final boolean RENDER_DEBUG_ITEM_PROXY = false;
    private static final DataTicket<ItemStack> HELD_ITEM_STACK_TICKET = DataTicket.create("waterfowl_held_item_stack", ItemStack.class);
    private static final DataTicket<WaterfowlEntity> WATERFOWL_ENTITY_TICKET = DataTicket.create("waterfowl_entity", WaterfowlEntity.class);
    private static final Field ITEM_RENDER_STATE_LAYERS_FIELD = getField(ItemRenderState.class, "layers");
    private static final Field ITEM_RENDER_STATE_LAYER_COUNT_FIELD = getField(ItemRenderState.class, "layerCount");
    private static final Field LAYER_RENDER_STATE_RENDER_LAYER_FIELD = getLayerRenderField();

    public WaterfowlRenderer(WaterfowlModel<T> model, EntityRendererFactory.Context context) {
        super(context, model);
        this.shadowRadius = ADULT_SHADOW_RADIUS;
        withRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void addPerBoneRender(R renderState, BakedGeoModel model, boolean didRenderModel,
                                         BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
                if (!didRenderModel) {
                    return;
                }

                ItemStack heldItemStack = renderState.getOrDefaultGeckolibData(HELD_ITEM_STACK_TICKET, ItemStack.EMPTY);
                WaterfowlEntity entity = renderState.getOrDefaultGeckolibData(WATERFOWL_ENTITY_TICKET, null);

                if (entity == null || heldItemStack.isEmpty()) {
                    return;
                }

                model.getBone("beak").ifPresent(bone -> consumer.accept(bone,
                        (renderState2, poseStack, bone2, renderTasks, cameraState, packedLight, packedOverlay, renderColor) ->
                                renderHeldItem(renderState2, poseStack, entity, heldItemStack, renderTasks, packedLight)));
            }
        });
    }

    @Override
    public void submitPerBoneRenderTasks(R renderState, MatrixStack poseStack, PerBoneRenderTasks.ForRenderer<R> perBoneTasks,
                                         OrderedRenderCommandQueue renderTasks, CameraRenderState cameraState,
                                         int packedLight, int packedOverlay, int renderColor) {
        if (perBoneTasks.isEmpty()) {
            return;
        }

        // GeckoLib 5.3 submits per-bone renders before the model render task applies
        // the current frame's animations. Refresh the animated pose here so beak-held
        // items use the same bone transforms as the rendered duck/goose model.
        getGeoModel().handleAnimations(createAnimationState(renderState));

        poseStack.push();
        poseStack.peek().getPositionMatrix().set(renderState.getGeckolibData(DataTickets.MODEL_RENDER_POSE));

        for (Map.Entry<GeoBone, List<PerBoneRender<R>>> boneTasks : perBoneTasks) {
            poseStack.push();
            boneTasks.getKey().transformToBone(poseStack);

            for (PerBoneRender<R> renderOp : boneTasks.getValue()) {
                poseStack.push();
                renderOp.submitRenderTask(renderState, poseStack, boneTasks.getKey(), renderTasks, cameraState, packedLight, packedOverlay, renderColor);
                poseStack.pop();
            }

            poseStack.pop();
        }

        poseStack.pop();
    }

    private void renderHeldItem(R renderState, MatrixStack poseStack, WaterfowlEntity entity, ItemStack heldItemStack,
                                OrderedRenderCommandQueue renderTasks, int packedLight) {
        MatrixStack heldItemPoseStack = snapshotPoseStack(poseStack);

        if (RENDER_DEBUG_AXES) {
            renderDebugAxes(heldItemPoseStack, renderTasks, 0.18f, 6f);
        }

        if (renderState.entityType == ModEntityTypes.getDuck()) {
            heldItemPoseStack.translate(0.0, -0.15, -0.18);
        } else if (renderState.entityType == ModEntityTypes.getGoose()) {
            heldItemPoseStack.translate(0.0, -0.10, -0.32);
        }

        heldItemPoseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));

        if (renderState.entityType == ModEntityTypes.getDuck()) {
            heldItemPoseStack.translate(0.0, 0.0, 0.12);
        } else if (renderState.entityType == ModEntityTypes.getGoose()) {
            heldItemPoseStack.translate(0.0, 0.0, 0.13);
        }

        if (RENDER_DEBUG_AXES) {
            renderDebugAxes(heldItemPoseStack, renderTasks, 0.12f, 4f);
        }

        if (RENDER_DEBUG_ITEM_PROXY) {
            // Push the debug proxy clearly out of the beak and render it at a larger,
            // dedicated size so we can inspect the attachment transform itself.
            heldItemPoseStack.translate(0.0, 0.0, 0.32);
            renderDebugItemProxy(heldItemPoseStack, renderTasks, packedLight);
        } else {
            heldItemPoseStack.scale(0.4f, 0.4f, 0.4f);
            renderHeldItemWithLayerOverride(entity, heldItemStack, heldItemPoseStack, renderTasks, packedLight);
        }
    }

    private MatrixStack snapshotPoseStack(MatrixStack poseStack) {
        MatrixStack snapshot = new MatrixStack();
        snapshot.peek().getPositionMatrix().set(poseStack.peek().getPositionMatrix());
        snapshot.peek().getNormalMatrix().set(poseStack.peek().getNormalMatrix());
        return snapshot;
    }

    private void renderDebugItemProxy(MatrixStack poseStack, OrderedRenderCommandQueue renderTasks, int packedLight) {
        poseStack.push();
        poseStack.scale(0.22f, 0.22f, 0.22f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        renderTasks.submitBlock(poseStack, Blocks.WHITE_WOOL.getDefaultState(), packedLight, OverlayTexture.DEFAULT_UV, 0);
        poseStack.pop();

        // Small forward marker so the proxy still communicates facing direction.
        poseStack.push();
        poseStack.translate(0.0, 0.0, 0.26);
        poseStack.scale(0.09f, 0.09f, 0.09f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        renderTasks.submitBlock(poseStack, Blocks.RED_WOOL.getDefaultState(), packedLight, OverlayTexture.DEFAULT_UV, 0);
        poseStack.pop();

        renderTasks.submitCustom(poseStack, RenderLayers.lines(), (matricesEntry, vertexConsumer) -> {
            renderDebugLine(vertexConsumer, matricesEntry, 0f, 0f, 0f, 0f, 0f, 0.32f, 0xFFFF5050, 7f);
        });
    }

    private void renderHeldItemWithLayerOverride(WaterfowlEntity entity, ItemStack heldItemStack, MatrixStack poseStack,
                                                 OrderedRenderCommandQueue renderTasks, int packedLight) {
        ItemRenderState itemRenderState = new ItemRenderState();
        MinecraftClient client = MinecraftClient.getInstance();
        client.getItemModelManager().clearAndUpdate(itemRenderState, heldItemStack, ItemDisplayContext.NONE, entity.getEntityWorld(), entity, entity.getId());
        overrideBeakItemRenderLayers(itemRenderState);
        itemRenderState.render(poseStack, renderTasks, packedLight, OverlayTexture.DEFAULT_UV, 0);
    }

    private static void overrideBeakItemRenderLayers(ItemRenderState itemRenderState) {
        if (ITEM_RENDER_STATE_LAYERS_FIELD == null || ITEM_RENDER_STATE_LAYER_COUNT_FIELD == null || LAYER_RENDER_STATE_RENDER_LAYER_FIELD == null) {
            return;
        }

        try {
            Object[] layers = (Object[]) ITEM_RENDER_STATE_LAYERS_FIELD.get(itemRenderState);
            int layerCount = ITEM_RENDER_STATE_LAYER_COUNT_FIELD.getInt(itemRenderState);

            for (int i = 0; i < layerCount; i++) {
                RenderLayer renderLayer = (RenderLayer) LAYER_RENDER_STATE_RENDER_LAYER_FIELD.get(layers[i]);

                if (renderLayer == null) {
                    continue;
                }

                if (renderLayer == net.minecraft.client.render.TexturedRenderLayers.getItemTranslucentCull()) {
                    LAYER_RENDER_STATE_RENDER_LAYER_FIELD.set(layers[i], RenderLayers.entityCutoutNoCull(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE, false));
                } else if (renderLayer == net.minecraft.client.render.TexturedRenderLayers.getBlockTranslucentCull()
                        || renderLayer == net.minecraft.client.render.TexturedRenderLayers.getEntityCutout()) {
                    LAYER_RENDER_STATE_RENDER_LAYER_FIELD.set(layers[i], RenderLayers.entityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false));
                }
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field getField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field getLayerRenderField() {
        try {
            Class<?> layerClass = Class.forName("net.minecraft.client.render.item.ItemRenderState$LayerRenderState");
            return getField(layerClass, "renderLayer");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private void renderDebugAxes(MatrixStack poseStack, OrderedRenderCommandQueue renderTasks, float axisLength, float lineWidth) {
        renderTasks.submitCustom(poseStack, RenderLayers.lines(), (matricesEntry, vertexConsumer) -> {
            renderAxisLine(vertexConsumer, matricesEntry, axisLength, 0f, 0f, 0xFFFF0000, lineWidth);
            renderAxisLine(vertexConsumer, matricesEntry, 0f, axisLength, 0f, 0xFF00FF00, lineWidth);
            renderAxisLine(vertexConsumer, matricesEntry, 0f, 0f, axisLength, 0xFF4DA6FF, lineWidth);
        });
    }

    private void renderAxisLine(VertexConsumer vertexConsumer, MatrixStack.Entry matricesEntry, float x, float y, float z, int color, float lineWidth) {
        renderDebugLine(vertexConsumer, matricesEntry, 0f, 0f, 0f, x, y, z, color, lineWidth);
    }

    private void renderDebugLine(VertexConsumer vertexConsumer, MatrixStack.Entry matricesEntry,
                                 float startX, float startY, float startZ,
                                 float endX, float endY, float endZ,
                                 int color, float lineWidth) {
        float x = endX - startX;
        float y = endY - startY;
        float z = endZ - startZ;
        float normalLength = (float) Math.sqrt(x * x + y * y + z * z);
        float normalX = normalLength == 0 ? 0 : x / normalLength;
        float normalY = normalLength == 0 ? 0 : y / normalLength;
        float normalZ = normalLength == 0 ? 0 : z / normalLength;

        vertexConsumer.vertex(matricesEntry, startX, startY, startZ).color(color).normal(matricesEntry, normalX, normalY, normalZ).lineWidth(lineWidth);
        vertexConsumer.vertex(matricesEntry, endX, endY, endZ).color(color).normal(matricesEntry, normalX, normalY, normalZ).lineWidth(lineWidth);
    }

    @Override
    protected float getShadowRadius(R state) {
        return super.getShadowRadius(state) * state.ageScale;
    }

    @Override
    public void addRenderData(T animatable, Void relatedObject, R renderState, float partialTick) {
        ItemStack mainHandStack = animatable.getMainHandStack();
        // set the variant in the render state
        renderState.addGeckolibData(WaterfowlEntity.VARIANT_TICKET, animatable.getVariant());
        renderState.addGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET, animatable.getBabyScale());
        renderState.addGeckolibData(HELD_ITEM_STACK_TICKET, mainHandStack);
        renderState.addGeckolibData(WATERFOWL_ENTITY_TICKET, animatable);

        if (animatable.hasCustomName()) {
            renderState.addGeckolibData(WaterfowlEntity.CUSTOM_NAME_TICKET, Objects.requireNonNull(animatable.getCustomName()).getString());
        }
    }

    @Override
    public void scaleModelForRender(R renderState, float widthScale, float heightScale, MatrixStack poseStack, BakedGeoModel model, CameraRenderState cameraRenderState) {
        float babyScale = 0.7f;

        if (renderState.hasGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET))
            //noinspection DataFlowIssue
            babyScale = renderState.getGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET);

        float modelScale = renderState.baby ? babyScale : 0.8f + babyScale * 0.5f;

        super.scaleModelForRender(renderState, modelScale, modelScale, poseStack, model, cameraRenderState);
    }
}
