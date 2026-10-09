package dev.actest.mixin;

import dev.actest.module.JesusModule;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин для Jesus: для своего игрока вода/лава получают коллизию полного блока, если он стоит выше неё.
 */
@Mixin({AbstractBlock.AbstractBlockState.class})
public abstract class AbstractBlockStateMixin {
   @Inject(
      method = {"getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void actest$jesus(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
      // Метод вызывается очень часто (все коллизии мира), поэтому сначала дешёвая проверка флага.
      if (JesusModule.isActive() && JesusModule.solidFor((AbstractBlock.AbstractBlockState)(Object)this, pos, context)) {
         cir.setReturnValue(VoxelShapes.fullCube());
      }
   }
}
