package net.untitledduckmod.mixin.fabric.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import software.bernie.geckolib.util.ClientUtil;

/**
 * Temporary GeckoLib 5.3 compatibility shim for 1.21.11.
 * The shipped GeckoLib build still references ClientUtil#getCameraPos() logic that
 * expects Camera#getPos() to exist. Overwriting the helper here is the smallest fix
 * that keeps the older library working until the project can move to a GeckoLib/Loom
 * combination built for this Minecraft version.
 */
@Mixin(value = ClientUtil.class, remap = false)
public abstract class GeckoLibClientUtilMixin {
    /**
     * Match the old helper contract, but source the position from Camera.pos directly.
     * This keeps GeckoLib's render-time camera lookups working on 1.21.11.
     */
    @Overwrite(remap = false)
    public static Vec3d getCameraPos() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (camera == null) {
            return Vec3d.ZERO;
        }

        return ((CameraAccessor) camera).untitledduckmod$getPos();
    }
}
