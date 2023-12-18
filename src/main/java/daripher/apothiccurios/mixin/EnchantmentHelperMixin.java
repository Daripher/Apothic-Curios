package daripher.apothiccurios.mixin;

import daripher.apothiccurios.ApothicCuriosMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
  /** Injection to {@link EnchantmentHelper#doPostDamageEffects(LivingEntity, Entity)} */
  @Inject(
      at = @At("TAIL"),
      method =
          "doPostDamageEffects(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;)V")
  private static void affixPostDamageEffects(LivingEntity user, Entity target, CallbackInfo ci) {
    if (user == null) return;
    ApothicCuriosMod.getCuriosAffixes(user)
        .forEach(
            affix -> {
              int old = target.invulnerableTime;
              target.invulnerableTime = 0;
              affix.doPostAttack(user, target);
              target.invulnerableTime = old;
            });
  }

  /** Injection to {@link EnchantmentHelper#doPostHurtEffects(LivingEntity, Entity)} */
  @Inject(
      at = @At("TAIL"),
      method =
          "doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;)V")
  private static void affixPostHurtEffects(LivingEntity user, Entity attacker, CallbackInfo ci) {
    if (user == null) return;
    ApothicCuriosMod.getCuriosAffixes(user).forEach(affix -> affix.doPostHurt(user, attacker));
  }
}
