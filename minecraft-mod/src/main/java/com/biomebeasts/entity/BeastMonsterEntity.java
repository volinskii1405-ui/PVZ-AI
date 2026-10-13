package com.biomebeasts.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A hostile biome predator. Optionally inflicts a status effect on each hit. */
public class BeastMonsterEntity extends HostileEntity {
	@Nullable
	private final RegistryEntry<StatusEffect> hitEffect;
	private final int hitEffectTicks;
	private final int hitEffectAmplifier;

	public BeastMonsterEntity(EntityType<? extends BeastMonsterEntity> type, World world) {
		this(type, world, null, 0, 0);
	}

	public BeastMonsterEntity(EntityType<? extends BeastMonsterEntity> type, World world,
			@Nullable RegistryEntry<StatusEffect> hitEffect, int hitEffectTicks, int hitEffectAmplifier) {
		super(type, world);
		this.hitEffect = hitEffect;
		this.hitEffectTicks = hitEffectTicks;
		this.hitEffectAmplifier = hitEffectAmplifier;
	}

	public static DefaultAttributeContainer.Builder createAttributes(double health, double speed, double damage, double knockback) {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, health)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, speed)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, damage)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, knockback)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && this.hitEffect != null && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(this.hitEffect, this.hitEffectTicks, this.hitEffectAmplifier), this);
		}
		return hit;
	}
}
