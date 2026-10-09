package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Reach: увеличенная дальность атаки (и, по желанию, взаимодействия с блоками).
 * С 1.20.5 дальность — атрибуты entity_interaction_range (3.0) и block_interaction_range (4.5).
 * Модуль поднимает их на клиенте: прицел начинает «видеть» сущности дальше, и клиент
 * отправляет обычный PlayerInteractEntityC2SPacket (атака) по дальней цели.
 */
public final class ReachModule extends AbstractModule {
	private final AttributeBoost entityRange = new AttributeBoost(EntityAttributes.ENTITY_INTERACTION_RANGE, "reach_entity");
	private final AttributeBoost blockRange = new AttributeBoost(EntityAttributes.BLOCK_INTERACTION_RANGE, "reach_block");

	public ReachModule() {
		super("Reach", "key.actest.reach", GLFW.GLFW_KEY_K);
	}

	@Override
	public String getHudInfo() {
		return String.format(Locale.ROOT, "%.1f", ActestConfig.get().reach.entityRange);
	}

	@Override
	public void onTick(MinecraftClient client) {
		ActestConfig.Reach cfg = ActestConfig.get().reach;
		entityRange.apply(client.player, cfg.entityRange);
		if (cfg.blocks) {
			blockRange.apply(client.player, cfg.blockRange);
		} else {
			blockRange.remove(client.player);
		}
	}

	@Override
	protected void onDisable() {
		entityRange.remove(MinecraftClient.getInstance().player);
		blockRange.remove(MinecraftClient.getInstance().player);
	}
}
