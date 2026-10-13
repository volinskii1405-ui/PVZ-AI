package com.biomebeasts;

import net.fabricmc.api.ModInitializer;

public class BiomeBeasts implements ModInitializer {
	public static final String MOD_ID = "biomebeasts";

	@Override
	public void onInitialize() {
		// Entities must be registered before items (spawn eggs reference entity types).
		ModEntities.init();
		ModItems.init();
	}
}
