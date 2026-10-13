package com.biomebeasts.client;

import com.biomebeasts.ModEntities;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.CodEntityModel;
import net.minecraft.client.render.entity.model.CowEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.OcelotEntityModel;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.client.render.entity.model.WitchEntityModel;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;

public class BiomeBeastsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		reg(ModEntities.BEAR, "bear", 0.9F, 1.0F, c -> new BearModel<>(c.getPart(EntityModelLayers.POLAR_BEAR)));
		reg(ModEntities.SAND_SCORPION, "sand_scorpion", 0.8F, 0.9F, c -> new SpiderEntityModel<>(c.getPart(EntityModelLayers.SPIDER)));
		reg(ModEntities.YETI, "yeti", 0.5F, 1.4F, c -> new ZombieEntityModel<>(c.getPart(EntityModelLayers.ZOMBIE)));
		reg(ModEntities.JAGUAR, "jaguar", 0.4F, 1.3F, c -> new OcelotEntityModel<>(c.getPart(EntityModelLayers.OCELOT)));
		reg(ModEntities.BOG_HAG, "bog_hag", 0.5F, 1.0F, c -> new WitchEntityModel<>(c.getPart(EntityModelLayers.WITCH)));
		reg(ModEntities.LION, "lion", 0.4F, 1.7F, c -> new OcelotEntityModel<>(c.getPart(EntityModelLayers.OCELOT)));
		reg(ModEntities.ELK, "elk", 0.7F, 1.4F, c -> new CowEntityModel<>(c.getPart(EntityModelLayers.COW)));
		reg(ModEntities.STONE_TROLL, "stone_troll", 0.5F, 1.6F, c -> new ZombieEntityModel<>(c.getPart(EntityModelLayers.ZOMBIE)));
		reg(ModEntities.REEF_SHARK, "reef_shark", 0.3F, 3.0F, c -> new CodEntityModel<>(c.getPart(EntityModelLayers.COD)));
		reg(ModEntities.WENDIGO, "wendigo", 0.5F, 1.25F, c -> new SkeletonEntityModel<>(c.getPart(EntityModelLayers.SKELETON)));
		reg(ModEntities.CANYON_BOAR, "canyon_boar", 0.7F, 1.2F, c -> new PigEntityModel<>(c.getPart(EntityModelLayers.PIG)));
		reg(ModEntities.BISON, "bison", 0.7F, 1.5F, c -> new CowEntityModel<>(c.getPart(EntityModelLayers.COW)));
	}

	private static <T extends MobEntity> void reg(EntityType<T> type, String name, float shadow, float scale,
			Function<EntityRendererFactory.Context, EntityModel<T>> model) {
		EntityRendererRegistry.register(type, ctx -> new BeastRenderer<>(ctx, model.apply(ctx), shadow, scale, name));
	}
}
