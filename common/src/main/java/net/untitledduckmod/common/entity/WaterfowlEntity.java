package net.untitledduckmod.common.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.untitledduckmod.common.config.UntitledConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.Objects;

public abstract class WaterfowlEntity extends TameableEntity implements GeoAnimatable {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final float BABY_MIN_SCALE = 0.25f;
    public static final float BABY_MAX_SCALE = 0.7f;
    public static final String EGG_LAY_TIME_TAG = "EggLayTime";
    public static final String VARIANT_TAG = "Variant";
    public static final String BABY_SCALE_TAG = "BabyScale";
    public static final float SWIM_SPEED_MULTIPLIER = 3.0f;
    public static final DataTicket<Boolean> LOOKING_AROUND_TICKET = DataTicket.create("look_around", Boolean.class);
    public static final DataTicket<Byte> VARIANT_TICKET = DataTicket.create("waterfowl_variant", Byte.class);
    public static final DataTicket<Float> BABY_SCALE_TICKET = DataTicket.create("waterfowl_baby_scale", Float.class);
    public static final DataTicket<String> CUSTOM_NAME_TICKET = DataTicket.create("waterfowl_custom_name", String.class);
    protected static final TrackedData<Byte> VARIANT = DataTracker.registerData(WaterfowlEntity.class, TrackedDataHandlerRegistry.BYTE);
    protected static final TrackedData<Float> BABY_SCALE = DataTracker.registerData(WaterfowlEntity.class, TrackedDataHandlerRegistry.FLOAT);
    protected static final TrackedData<Byte> ANIMATION = DataTracker.registerData(WaterfowlEntity.class, TrackedDataHandlerRegistry.BYTE);
    public static final byte ANIMATION_IDLE = 0;
    public static final byte ANIMATION_CLEAN = 1;
    public static final byte ANIMATION_DANCE = 3;
    public static final byte ANIMATION_PANIC = 4;
    public static final byte ANIMATION_EAT = 5;

    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenPlay("walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenPlay("swim");
    protected static final RawAnimation SWIM_IDLE_ANIM = RawAnimation.begin().thenPlay("idle_swim");
    protected static final RawAnimation PANIC_ANIM = RawAnimation.begin().thenPlay("panic");
    protected static final RawAnimation FLY_ANIM = RawAnimation.begin().thenPlay("fly");
    protected static final RawAnimation CLEAN_ANIM = RawAnimation.begin().thenPlay("clean");
    protected static final RawAnimation EAT_ANIM = RawAnimation.begin().thenPlay("eat");
    protected static final RawAnimation SIT_ANIM = RawAnimation.begin().thenPlay("sit");

    @FunctionalInterface
    protected interface AnimationStateHandler<P extends GeoAnimatable> {
        PlayState handle(AnimationState<P> state);
    }

    protected record AnimationState<P extends GeoAnimatable>(AnimationController<P> controller, boolean moving, boolean inWater, byte currentAnimation) {
    }

    private static final int MIN_EGG_LAY_TIME = 12000;
    private static final int MAX_EGG_LAY_TIME = 24000;

    protected int maxVariant = 3;
    protected int eggLayTime;
    protected boolean isFlapping;
    protected boolean panicked = false;
    protected WaterfowlEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        eggLayTime = getRandomLayTime();
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
        var babyScale = getRandomBabyScale();
        var variant = getRandomVariant();

        if (entityData instanceof GeoRenderState geoRenderState) {
            geoRenderState.addGeckolibData(WaterfowlEntity.BABY_SCALE_TICKET, babyScale);
            geoRenderState.addGeckolibData(WaterfowlEntity.VARIANT_TICKET, variant);
            geoRenderState.addGeckolibData(WaterfowlEntity.LOOKING_AROUND_TICKET, lookingAround());
        }
        this.setVariant(variant); // Randomly choose between the two variants
        this.setBabyScale(babyScale);
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, (byte) 0);
        builder.add(ANIMATION, ANIMATION_IDLE);
        builder.add(BABY_SCALE, getRandomBabyScale());
    }

    @Override
    public void writeCustomData(WriteView nbt) {
        super.writeCustomData(nbt);
        nbt.putByte(VARIANT_TAG, getVariant());
        nbt.putInt(EGG_LAY_TIME_TAG, eggLayTime);
        nbt.putFloat(BABY_SCALE_TAG, getBabyScale());
    }

    @Override
    public void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);
        setVariant(nbt.getByte(VARIANT_TAG, (byte) 0));
        setBabyScale(nbt.getFloat(BABY_SCALE_TAG, 1.0f));
        this.eggLayTime = nbt.getInt(EGG_LAY_TIME_TAG, 0);
        if (this.eggLayTime == 0) {
            this.eggLayTime = getRandomLayTime();
        }
    }

    @Override
    public void setTamed(boolean tamed, boolean updateAttributes) {
        super.setTamed(tamed, updateAttributes);
        if (tamed) {
            Objects.requireNonNull(getAttributeInstance(EntityAttributes.MAX_HEALTH)).setBaseValue(20.0);
            setHealth(20.0F);
        } else {
            Objects.requireNonNull(getAttributeInstance(EntityAttributes.MAX_HEALTH)).setBaseValue(7.0);
        }
    }

    @Override
    public boolean handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    public byte getVariant() {
        return dataTracker.get(VARIANT);
    }

    public void setVariant(byte variant) {
        dataTracker.set(VARIANT, variant);
    }

    public float getRandomBabyScale() {
        return random.nextFloat() * (BABY_MAX_SCALE - BABY_MIN_SCALE) + BABY_MIN_SCALE;
    }

    public float getBabyScale() {
        return dataTracker.get(BABY_SCALE);
    }

    public void setBabyScale(float scale) {
        dataTracker.set(BABY_SCALE, scale);
    }

    public byte getRandomVariant() {
        return (byte) random.nextInt(maxVariant);
    }

    public int getRandomLayTime() {
        return random.nextInt(MIN_EGG_LAY_TIME) + (MAX_EGG_LAY_TIME - MIN_EGG_LAY_TIME);
    }

    public byte getAnimation() {
        return dataTracker.get(ANIMATION);
    }

    public void setAnimation(byte animation) {
        dataTracker.set(ANIMATION, animation);
    }

    public boolean isHungry() {
        return getHealth() <= getMaxHealth() - 0.5f;
    }

    public void tryEating() {
        if (!this.getEntityWorld().isClient() && this.isHungry()) {
            this.getEntityWorld().sendEntityStatus(this, EntityStatuses.ADD_BREEDING_PARTICLES);
            setHealth(getHealth() + UntitledConfig.foodHealingValue());
            this.setAnimation(ANIMATION_EAT);
        }
    }

    public boolean lookingAround() {
        return (getAnimation() == ANIMATION_IDLE || getAnimation() == ANIMATION_CLEAN) && !this.panicked;
    }

    @Nullable
    protected <P extends GeoAnimatable> AnimationState<P> getAnimationState(AnimationTest<P> event) {
        var livingRenderState = (LivingEntityRenderState) event.renderState();
        boolean isMoving = Math.abs(livingRenderState.limbSwingAmplitude) >= 0.05F;
        boolean inWater = isTouchingWater();
        AnimationController<P> controller = event.controller();

        if (isFlapping) {
            controller.setAnimation(FLY_ANIM);
            return null;
        }

        if (isInSittingPose()) {
            controller.setAnimation(SIT_ANIM);
            return null;
        }

        return new AnimationState<>(controller, isMoving, inWater, getAnimation());
    }

    protected <P extends GeoAnimatable> PlayState handleAnimation(AnimationTest<P> event, AnimationStateHandler<P> handler) {
        AnimationState<P> animationState = getAnimationState(event);
        if (animationState == null) {
            return PlayState.CONTINUE;
        }

        return handler.handle(animationState);
    }

    protected void spawnHeldItemParticles() {
        ItemStack stack = getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }

        for (int i = 0; i < 8; ++i) {
            Vec3d velocity = new Vec3d((this.random.nextFloat() - 0.5D) * 0.1D, this.random.nextDouble() * 0.1D + 0.1D, 0.0D);
            velocity = velocity.rotateX(-this.getPitch() * 0.017453292F);
            velocity = velocity.rotateY(-this.getYaw() * 0.017453292F);

            Vec3d rotationVec = Vec3d.fromPolar(0, bodyYaw);
            Vec3d pos = new Vec3d(this.getX() + rotationVec.x / 2.0D, getEyeY() - 0.2D, this.getZ() + rotationVec.z / 2.0D);
            this.getEntityWorld().addParticleClient(new ItemStackParticleEffect(ParticleTypes.ITEM, stack), pos.x, pos.y, pos.z,
                    velocity.x, velocity.y + 0.05D, velocity.z);
        }
    }

    protected abstract SoundEvent getLayEggSound();

    public abstract Item getEggItem();

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            // Lay egg
            if (isAlive() && !isBaby() && --eggLayTime <= 0) {
                this.playSound(this.getLayEggSound(), 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.dropItem(serverWorld, this.getEggItem());
                this.eggLayTime = getRandomLayTime();
            }

            // Slow fall speed when flapping
            Vec3d velocity = this.getVelocity();
            if (!this.isOnGround() && velocity.y < 0.0D) {
                this.setVelocity(velocity.multiply(1.0D, 0.6D, 1.0D));
            }

            // Trigger panic animation when being attacked or being on fire
            this.handlePanicAnimation();
        }

        // Play flapping/fly animation when falling
        isFlapping = this.getEntityWorld().isClient() && !isTouchingWater() && !this.isOnGround();
    }

    protected abstract void handlePanicAnimation();

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // TODO: Cleanup
        ItemStack stack = player.getStackInHand(hand);
        if (this.getEntityWorld().isClient() && (!this.isBaby() || !this.isBreedingItem(stack))) {
            if (this.isTamed() && this.isOwner(player)) {
                return ActionResult.SUCCESS;
            } else {
                return !isTamableItem(stack) || !(this.getHealth() < this.getMaxHealth()) && this.isTamed() ? ActionResult.PASS : ActionResult.SUCCESS;
            }
        } else {
            if (isTamed() && this.isOwner(player)) {
                if (this.isBreedingItem(stack) && this.getHealth() < this.getMaxHealth()) {
                    this.eat(player, hand, stack);
                    heal(UntitledConfig.foodHealingValue());
                    return ActionResult.CONSUME;
                }
                ActionResult actionResult = super.interactMob(player, hand);
                if ((!actionResult.isAccepted() || this.isBaby())) {
                    this.setSitting(!this.isSitting());
                    this.jumping = false;
                    this.navigation.stop();
                    this.setTarget(null);
                }
                return actionResult;
            } else if (tryTaming(player, stack)) {
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                if (this.random.nextInt(3) == 0) {
                    this.setTamedBy(player);
                    this.navigation.stop();
                    this.setTarget(null);
                    this.setSitting(true);
                    this.getEntityWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
                } else {
                    this.getEntityWorld().sendEntityStatus(this, EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES);
                }
                return ActionResult.CONSUME;
            } else {
                return super.interactMob(player, hand);
            }
        }
    }

    protected boolean tryTaming(PlayerEntity player, ItemStack stack) {
        return this.isTamable(player, stack);
    }

    protected abstract boolean isTamableItem(ItemStack stack);

    protected boolean isTamable(PlayerEntity player, ItemStack stack) {
        return this.isTamableItem(stack) && !this.isTamed();
    }

    @Override
    protected void swimUpward(TagKey<Fluid> fluid) {
        // This bypasses forge modifying jump depending on swim speed
        if (this.getNavigation().canSwim()) {
            this.setVelocity(this.getVelocity().add(0.0D, 0.03999999910593033D, 0.0D));
        } else {
            this.setVelocity(this.getVelocity().add(0.0D, 0.3D, 0.0D));
        }
    }

    @Override
    public boolean canSpawn(WorldView world) {
        return world.doesNotIntersectEntities(this);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        this.setSitting(false);
        return super.damage(world, source, amount);
    }

    @Override
    public double getTick(Object o) {
        return this.age;
    }

    public int getEggLayTime() {
        return this.eggLayTime;
    }

    public boolean tamedFollowOwner() {
        return true;
    }

}
