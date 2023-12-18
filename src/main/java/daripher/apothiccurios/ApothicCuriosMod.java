package daripher.apothiccurios;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.datafixers.util.Either;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shadows.apotheosis.Apotheosis;
import shadows.apotheosis.adventure.affix.AffixHelper;
import shadows.apotheosis.adventure.affix.AffixInstance;
import shadows.apotheosis.adventure.affix.socket.SocketHelper;
import shadows.apotheosis.adventure.affix.socket.gem.GemInstance;
import shadows.apotheosis.adventure.affix.socket.gem.bonus.GemBonus;
import shadows.apotheosis.adventure.client.SocketTooltipRenderer;
import shadows.apotheosis.adventure.loot.LootCategory;
import shadows.apotheosis.core.attributeslib.AttributesLib;
import shadows.apotheosis.core.attributeslib.api.IFormattableAttribute;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

@Mod(ApothicCuriosMod.MOD_ID)
public class ApothicCuriosMod {
  public static final String MOD_ID = "apothiccurios";
  public static final EquipmentSlot FAKE_SLOT = EquipmentSlot.LEGS;

  public ApothicCuriosMod() {
    if (!adventureModuleEnabled()) return;
    IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
    forgeEventBus.addListener(EventPriority.LOW, this::addCurioSocketTooltip);
    forgeEventBus.addListener(this::applyCurioShieldBlockAffixes);
    forgeEventBus.addListener(this::applyCurioBlockBreakAffixes);
    forgeEventBus.addListener(this::applyCurioArrowAffixes);
    forgeEventBus.addListener(this::applyCurioAttributeAffixes);
    forgeEventBus.addListener(this::applyCurioDamageAffixes);
    forgeEventBus.addListener(EventPriority.LOWEST, this::removeFakeCurioAttributes);
    forgeEventBus.addListener(EventPriority.LOWEST, this::removeGemAttributeTooltips);
  }

  private void addCurioSocketTooltip(RenderTooltipEvent.GatherComponents event) {
    ItemStack stack = event.getItemStack();
    if (isNonCurio(stack)) return;
    Predicate<TooltipComponent> isSocketComponent =
        SocketTooltipRenderer.SocketComponent.class::isInstance;
    event.getTooltipElements().removeIf(c -> c.right().filter(isSocketComponent).isPresent());
    SocketTooltipRenderer.SocketComponent component =
        new SocketTooltipRenderer.SocketComponent(stack, SocketHelper.getGems(stack));
    event.getTooltipElements().add(Either.right(component));
  }

  @SubscribeEvent
  public void applyCurioShieldBlockAffixes(ShieldBlockEvent event) {
    AtomicReference<Float> blocked = new AtomicReference<>(event.getBlockedDamage());
    LivingEntity entity = event.getEntity();
    DamageSource damageSource = event.getDamageSource();
    getCuriosAffixes(entity)
        .forEach(affix -> blocked.set(affix.onShieldBlock(entity, damageSource, blocked.get())));
    if (blocked.get() != event.getOriginalBlockedDamage()) {
      event.setBlockedDamage(blocked.get());
    }
  }

  @SubscribeEvent
  public void applyCurioBlockBreakAffixes(BlockEvent.BreakEvent event) {
    Player player = event.getPlayer();
    LevelAccessor level = event.getLevel();
    BlockPos pos = event.getPos();
    BlockState state = event.getState();
    getCuriosAffixes(player).forEach(affix -> affix.onBlockBreak(player, level, pos, state));
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public void applyCurioArrowAffixes(EntityJoinLevelEvent event) {
    if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
    if (arrow.getPersistentData().getBoolean("apoth.generated")) return;
    Entity shooter = arrow.getOwner();
    if (!(shooter instanceof LivingEntity living)) return;
    getCuriosAffixes(living).forEach(affix -> affix.onArrowFired(living, arrow));
    getEquippedCurios(living).forEach(curio -> AffixHelper.copyFrom(curio, arrow));
  }

  private void applyCurioAttributeAffixes(CurioAttributeModifierEvent event) {
    ItemStack stack = event.getItemStack();
    if (!stack.hasTag()) return;
    if (!CuriosApi.getCuriosHelper().isStackValid(event.getSlotContext(), stack)) return;
    AffixHelper.getAffixes(stack).forEach((a, i) -> i.addModifiers(FAKE_SLOT, event::addModifier));
  }

  private void applyCurioDamageAffixes(LivingHurtEvent event) {
    DamageSource source = event.getSource();
    LivingEntity entity = event.getEntity();
    AtomicDouble amount = new AtomicDouble(event.getAmount());
    getCuriosAffixes(entity)
        .forEach(affix -> amount.set(affix.onHurt(source, entity, amount.floatValue())));
    event.setAmount(amount.floatValue());
  }

  private void removeFakeCurioAttributes(ItemAttributeModifierEvent event) {
    if (isNonCurio(event.getItemStack())) return;
    if (event.getSlotType() == FAKE_SLOT) event.clearModifiers();
  }

  private void removeGemAttributeTooltips(ItemTooltipEvent event) {
    ItemStack stack = event.getItemStack();
    if (!stack.hasTag()) return;
    if (isNonCurio(stack)) return;
    SocketHelper.getGemInstances(stack).forEach(g -> removeTooltip(event, g, stack));
  }

  private void removeTooltip(ItemTooltipEvent event, GemInstance gem, ItemStack stack) {
    Optional<GemBonus> bonus = gem.gem().getBonus(LootCategory.forItem(stack));
    if (bonus.isEmpty()) return;
    getGemModifiersTooltips(gem, bonus.get()).forEach(c -> removeTooltip(event, c));
    removeTooltip(event, bonus.get().getSocketBonusTooltip(gem.gemStack(), gem.rarity()));
  }

  private static List<Component> getGemModifiersTooltips(GemInstance gem, GemBonus bonus) {
    List<Component> tooltips = new ArrayList<>();
    bonus.addModifiers(
        gem.gemStack(),
        gem.rarity(),
        (a, m) -> {
          MutableComponent tooltip =
              IFormattableAttribute.toComponent(a, m, AttributesLib.getTooltipFlag());
          tooltips.add(tooltip);
        });
    return tooltips;
  }

  private static void removeTooltip(ItemTooltipEvent event, Component tooltip) {
    event.getToolTip().removeIf(c -> c.getString().equals(tooltip.getString()));
  }

  public static void registerCurioLootCategory(String id) {
    String slotId = id.replace("curios:", "");
    SlotContext slotContext = new SlotContext(slotId, null, 0, false, false);
    Predicate<ItemStack> validator = s -> CuriosApi.getCuriosHelper().isStackValid(slotContext, s);
    EquipmentSlot[] fakeSlots = {FAKE_SLOT};
    LootCategory.register(null, id, validator, fakeSlots);
  }

  private boolean adventureModuleEnabled() {
    return Apotheosis.enableAdventure;
  }

  private static boolean isNonCurio(ItemStack stack) {
    return CuriosApi.getCuriosHelper().getCurioTags(stack.getItem()).isEmpty();
  }

  private static List<ItemStack> getEquippedCurios(LivingEntity entity) {
    List<ItemStack> curios = new ArrayList<>();
    CuriosApi.getCuriosHelper()
        .getEquippedCurios(entity)
        .ifPresent(
            i -> {
              for (int slot = 0; slot < i.getSlots(); slot++) {
                curios.add(i.getStackInSlot(slot));
              }
            });
    return curios;
  }

  public static List<AffixInstance> getCuriosAffixes(LivingEntity entity) {
    return getEquippedCurios(entity).stream()
        .map(AffixHelper::getAffixes)
        .map(Map::values)
        .flatMap(Collection::stream)
        .toList();
  }
}
