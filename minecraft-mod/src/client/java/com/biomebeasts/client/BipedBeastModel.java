package com.biomebeasts.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.entity.mob.MobEntity;

/** Humanoid model (zombie/skeleton layers) usable by any mob; raises the arms while attacking. */
public class BipedBeastModel<T extends MobEntity> extends BipedEntityModel<T> {
	public BipedBeastModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		CrossbowPosing.meleeAttack(this.leftArm, this.rightArm, entity.isAttacking(), this.handSwingProgress, animationProgress);
	}
}
