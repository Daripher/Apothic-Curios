package daripher.apothiccurios;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.datafixers.util.Either;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.client.SocketTooltipRenderer;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketedGems;
import dev.shadowsoffire.attributeslib.AttributesLib;
import dev.shadowsoffire.attributeslib.api.AttributeHelper;
import dev.shadowsoffire.attributeslib.api.IFormattableAttribute;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

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
    SlotContext slotContext = event.getSlotContext();
    if (!CuriosApi.isStackValid(slotContext, stack)) return;
    LootCategory category = LootCategory.BY_ID.get("curios:" + slotContext.identifier());
    if (LootCategory.forItem(stack) != category) return;
    AffixHelper.getAffixes(stack).forEach((a, i) -> i.addModifiers(FAKE_SLOT, event::addModifier));
    SocketHelper.getGems(stack)
        .addModifiers(LootCategory.forItem(stack), FAKE_SLOT, event::addModifier);
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
    getGemTooltips(stack).forEach(component -> removeTooltip(event, component));
  }

  private List<Component> getGemTooltips(ItemStack stack) {
    TooltipFlag flag = TooltipFlag.NORMAL;
    List<Component> components = new ArrayList<>();
    SocketedGems gems = SocketHelper.getGems(stack);
    Multimap<Attribute, AttributeModifier> modifierMap = HashMultimap.create();
    gems.forEach(gemInstance -> gemInstance.addModifiers(FAKE_SLOT, modifierMap::put));
    for (Attribute attr : modifierMap.keySet()) {
      Collection<AttributeModifier> modifiers = modifierMap.get(attr);

      if (modifiers.size() > 1) {
        double[] sums = new double[3];
        boolean[] merged = new boolean[3];
        Map<AttributeModifier.Operation, List<AttributeModifier>> shiftExpands = new HashMap<>();
        for (AttributeModifier modifier : modifierMap.values()) {
          if (modifier.getAmount() == 0) continue;
          if (sums[modifier.getOperation().ordinal()] != 0)
            merged[modifier.getOperation().ordinal()] = true;
          sums[modifier.getOperation().ordinal()] += modifier.getAmount();
          shiftExpands
              .computeIfAbsent(modifier.getOperation(), k -> new LinkedList<>())
              .add(modifier);
        }
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
          int i = op.ordinal();
          if (sums[i] == 0) continue;
          if (merged[i]) {
            TextColor color =
                sums[i] < 0 ? TextColor.fromRgb(0xF93131) : TextColor.fromRgb(0x7A7AF9);
            if (sums[i] < 0) sums[i] *= -1;
            var fakeModif =
                new AttributeModifier(
                    UUID.randomUUID(), () -> AttributesLib.MODID + ":merged", sums[i], op);
            MutableComponent comp = IFormattableAttribute.toComponent(attr, fakeModif, flag);
            components.add(comp.withStyle(comp.getStyle().withColor(color)));
            if (merged[i] && Screen.hasShiftDown()) {
              shiftExpands
                  .get(AttributeModifier.Operation.fromValue(i))
                  .forEach(
                      modif ->
                          components.add(
                              AttributeHelper.list()
                                  .append(IFormattableAttribute.toComponent(attr, modif, flag))));
            }
          } else {
            var fakeModif =
                new AttributeModifier(
                    UUID.randomUUID(), () -> AttributesLib.MODID + ":merged", sums[i], op);
            components.add(IFormattableAttribute.toComponent(attr, fakeModif, flag));
          }
        }
      } else
        modifiers.forEach(
            m -> {
              if (m.getAmount() != 0)
                components.add(IFormattableAttribute.toComponent(attr, m, flag));
            });
    }
    return components;
  }

  private static void removeTooltip(ItemTooltipEvent event, Component tooltip) {
    event.getToolTip().removeIf(c -> c.getString().equals(tooltip.getString()));
  }

  public static void registerCurioLootCategory(String id) {
    String slotId = id.replace("curios:", "");
    SlotContext slotContext = new SlotContext(slotId, null, 0, false, false);
    Predicate<ItemStack> validator = s -> CuriosApi.isStackValid(slotContext, s);
    EquipmentSlot[] fakeSlots = {FAKE_SLOT};
    LootCategory.register(null, id, validator, fakeSlots);
  }

  private boolean adventureModuleEnabled() {
    return Apotheosis.enableAdventure;
  }

  private static boolean isNonCurio(ItemStack stack) {
    return CuriosApi.getItemStackSlots(stack).isEmpty();
  }

  private static List<ItemStack> getEquippedCurios(LivingEntity entity) {
    List<ItemStack> curios = new ArrayList<>();
    CuriosApi.getCuriosInventory(entity)
        .map(ICuriosItemHandler::getEquippedCurios)
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
