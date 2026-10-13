package com.biomebeasts.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.entity.Entity;

/** The vanilla polar bear model (same body parts), but usable by any entity type. */
public class BearModel<T extends Entity> extends QuadrupedEntityModel<T> {
	public BearModel(ModelPart root) {
		super(root, true, 16.0F, 4.0F, 2.25F, 2.0F, 24);
	}
}
