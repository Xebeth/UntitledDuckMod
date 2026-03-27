package net.untitledduckmod.mixin.fabric.client;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Temporary GeckoLib 5.3 compatibility shim for 1.21.11.
 * GeckoLib used to call Camera#getPos(), but that accessor is gone on this version,
 * so the overwrite in {@link GeckoLibClientUtilMixin} reads the backing field instead.
 * Remove this once the mod can depend on a GeckoLib build compiled for 1.21.11.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("pos")
    Vec3d untitledduckmod$getPos();
}
