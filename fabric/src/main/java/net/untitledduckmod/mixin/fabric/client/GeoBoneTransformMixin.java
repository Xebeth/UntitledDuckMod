package net.untitledduckmod.mixin.fabric.client;

import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;

@Mixin(GeoBone.class)
public abstract class GeoBoneTransformMixin {
    /**
     * GeckoLib 5.3 walks this chain child-to-root, which breaks nested per-bone
     * attachments like the item held in a duck's beak. GeckoLib 5.4 reverses the
     * walk and then translates back to the target pivot, which is the behavior we
     * need on 1.21.11 until the project can move to a newer GeckoLib line.
     */
    @Overwrite
    public void transformToBone(MatrixStack poseStack) {
        GeoBone bone = (GeoBone) (Object) this;
        List<GeoBone> boneQueue = new ArrayList<>();
        GeoBone parent = bone;

        boneQueue.add(bone);

        while ((parent = parent.getParent()) != null) {
            boneQueue.add(parent);
        }

        for (int i = boneQueue.size() - 1; i >= 0; i--) {
            RenderUtil.prepMatrixForBone(poseStack, boneQueue.get(i));
        }

        RenderUtil.translateToPivotPoint(poseStack, bone);
    }
}
