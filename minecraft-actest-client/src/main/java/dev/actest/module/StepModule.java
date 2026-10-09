package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Step: подъём на блоки без прыжка. Ванильная высота шага — атрибут step_height = 0.6
 * (полублок/ступенька). Модуль поднимает его на клиенте до step.height, и ванильная
 * физика сама «шагает» на блок за один тик — на сервер уходит Δy до step.height
 * при onGround=true.
 */
public final class StepModule extends AbstractModule {
	private final AttributeBoost boost = new AttributeBoost(EntityAttributes.STEP_HEIGHT, "step");

	public StepModule() {
		super("Step", "key.actest.step", GLFW.GLFW_KEY_J);
	}

	@Override
	public String getHudInfo() {
		return String.format(Locale.ROOT, "%.1f", ActestConfig.get().step.height);
	}

	@Override
	public void onTick(MinecraftClient client) {
		boost.apply(client.player, ActestConfig.get().step.height);
	}

	@Override
	protected void onDisable() {
		boost.remove(MinecraftClient.getInstance().player);
	}
}
