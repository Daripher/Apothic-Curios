package daripher.apothiccurios.mixin;

import daripher.apothiccurios.ApothicCuriosMod;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shadows.apotheosis.adventure.loot.LootCategory;

@Mixin(value = LootCategory.class, remap = false)
public class LootCategoryMixin {
  /** {@link LootCategory#byId(String)} */
  @Inject(method = "byId", at = @At("HEAD"))
  private static void registerCurioCategory(
      String id, CallbackInfoReturnable<LootCategory> callbackInfo) {
    if (id.startsWith("curios:") && LootCategory.BY_ID.get(id) == null) {
      ApothicCuriosMod.registerCurioLootCategory(id);
    }
  }

  /** {@link LootCategory#forItem(ItemStack)} */
  @Inject(method = "forItem", at = @At("HEAD"), cancellable = true)
  private static void handleEmptyCurio(
      ItemStack stack, CallbackInfoReturnable<LootCategory> callbackInfo) {
    if (stack.isEmpty()) {
      callbackInfo.setReturnValue(LootCategory.NONE);
    }
  }
}
