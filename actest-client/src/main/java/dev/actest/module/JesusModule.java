package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Jesus — ходьба по воде и лаве.
 *
 * Миксин AbstractBlockStateMixin даёт жидкости коллизию полного блока для своего игрока, пока его ноги
 * выше верха этого блока: клиент ходит по поверхности обычной наземной физикой и шлёт onGround=true.
 * Если игрок уже в жидкости, модуль выталкивает его вверх. Присесть (Shift) — нырнуть.
 */
public final class JesusModule extends AbstractModule {
   /** Читается из миксина в горячем пути коллизий — volatile-флаг вместо поиска модуля. */
   private static volatile boolean active;

   public JesusModule() {
      super("Jesus", "key.actest.jesus", 77); // M
   }

   public static boolean isActive() {
      return active;
   }

   @Override
   public String getHudInfo() {
      return ActestConfig.get().jesus.lava ? "W+L" : "W";
   }

   @Override
   protected void onEnable() {
      active = true;
   }

   @Override
   protected void onDisable() {
      active = false;
   }

   /** Сделать ли этот блок жидкости твёрдым в данном контексте коллизии. */
   public static boolean solidFor(AbstractBlock.AbstractBlockState state, BlockPos pos, ShapeContext context) {
      if (!(state.getBlock() instanceof FluidBlock)) {
         return false; // не вода/лава (жидкость в блоках с водой — waterlogged — не трогаем)
      }

      if (!ActestConfig.get().jesus.lava && state.getFluidState().getFluid() instanceof LavaFluid) {
         return false;
      }

      // Только для своего игрока: серверные сущности встроенного сервера и мобы не затрагиваются.
      if (!(context instanceof EntityShapeContext entityContext) || !(entityContext.getEntity() instanceof ClientPlayerEntity player) || player.isSneaking()) {
         return false;
      }

      return entityContext.isAbove(VoxelShapes.fullCube(), pos, false);
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      ActestConfig.Jesus cfg = ActestConfig.get().jesus;
      boolean inFluid = player.isTouchingWater() || cfg.lava && player.isInLava();
      if (cfg.swimUp && inFluid && !player.isSneaking()) {
         Vec3d v = player.getVelocity();
         player.setVelocity(v.x, Math.max(v.y, 0.11), v.z);
      }
   }
}
