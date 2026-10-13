package com.biomebeasts.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Forest / birch forest bear.
 * <ul>
 *   <li>28 HP (14 hearts)</li>
 *   <li>Movement speed 0.195 = 1.5x a sprinting player (player sprint = 0.1 * 1.3 = 0.13)</li>
 *   <li>Hit deals 7 HP (3.5 hearts)</li>
 *   <li>Drops 4 bear steaks and bear hide (see data/biomebeasts/loot_table/entities/bear.json)</li>
 * </ul>
 */
public class BearEntity extends BeastEntity {
	public static final double MAX_HEALTH = 28.0;
	public static final double MOVEMENT_SPEED = 0.13 * 1.5;
	public static final double ATTACK_DAMAGE = 7.0;

	public BearEntity(EntityType<? extends BearEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createBearAttributes() {
		return createAttributes(MAX_HEALTH, MOVEMENT_SPEED, ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_POLAR_BEAR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_POLAR_BEAR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_POLAR_BEAR_DEATH;
	}
}
