package com.biomebeasts.client;

import com.biomebeasts.BiomeBeasts;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/** Renders a mob with a vanilla model, a custom texture and a size multiplier. */
public class BeastRenderer<T extends MobEntity> extends MobEntityRenderer<T, EntityModel<T>> {
	private final Identifier texture;
	private final float scale;

	public BeastRenderer(EntityRendererFactory.Context context, EntityModel<T> model, float shadowRadius, float scale, String name) {
		super(context, model, shadowRadius * scale);
		this.texture = Identifier.of(BiomeBeasts.MOD_ID, "textures/entity/" + name + ".png");
		this.scale = scale;
	}

	@Override
	public Identifier getTexture(T entity) {
		return this.texture;
	}

	@Override
	protected void scale(T entity, MatrixStack matrices, float amount) {
		matrices.scale(this.scale, this.scale, this.scale);
	}
}
