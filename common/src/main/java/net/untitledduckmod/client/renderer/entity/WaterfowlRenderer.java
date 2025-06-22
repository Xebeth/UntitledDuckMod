package net.untitledduckmod.client.renderer.entity;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.untitledduckmod.client.model.WaterfowlModel;
import net.untitledduckmod.common.entity.WaterfowlEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.ItemInHandGeoLayer;

public class WaterfowlRenderer<T extends WaterfowlEntity, R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<T, R> {
    public WaterfowlRenderer(WaterfowlModel<T> model, EntityRendererFactory.Context context) {
        super(context, model);
        this.shadowRadius = 0.3f;
        // add the render layer so that ducks/geese hold items in their beak
        // this replaces the need to manually render the item in renderRecursively
        addRenderLayer(new ItemInHandGeoLayer<>(this, "beak", "beak"));
    }

    @Override
    public void addRenderData(T animatable, Void relatedObject, R renderState) {
        // set the variant in the render state
        renderState.addGeckolibData(WaterfowlEntity.VARIANT_TICKET, animatable.getVariant());
        renderState.addGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET, animatable.getBabyScale());
    }

    @Override
    public void scaleModelForRender(R renderState, float widthScale, float heightScale, MatrixStack poseStack, BakedGeoModel model, boolean isReRender) {
        float babyScale = 0.7f;

        if (renderState.hasGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET))
            //noinspection DataFlowIssue
            babyScale = renderState.getGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET);

        float modelScale = renderState.baby ? babyScale : 0.8f + babyScale * 0.5f;

        super.scaleModelForRender(renderState, modelScale, modelScale, poseStack, model, isReRender);
    }
}