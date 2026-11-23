package com.example.autotrade.mixin;

import com.example.autotrade.AutoTradeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.stream.Collectors;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<net.minecraft.world.inventory.MerchantMenu> {

    protected MerchantScreenMixin(net.minecraft.world.inventory.MerchantMenu menu, net.minecraft.world.entity.player.Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private EditBox searchBox;
    private CycleButton<ResourceKey<Enchantment>> enchantmentSelector;
    private CycleButton<Integer> levelSelector;
    private EditBox maxPriceInput;
    private Button toggleButton;
    private StringWidget rangeWidget;
    private List<ResourceKey<Enchantment>> allEnchantments;
    private boolean isLibrarian = false;

    @Inject(method = "init", at = @At("TAIL"))
    protected void onInit(CallbackInfo ci) {
        this.isLibrarian = checkIsLibrarian();
        if (!this.isLibrarian) return;

        int x = (this.width - 276) / 2;
        int y = (this.height - 166) / 2;

        var registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        this.allEnchantments = registry.listElements().map(h -> h.key()).collect(Collectors.toList());
        this.allEnchantments.sort(Comparator.comparing(k -> k.identifier().getPath()));

        this.searchBox = new EditBox(this.font, x + 5, y - 68, 90, 20, Component.literal("Search..."));
        this.searchBox.setResponder(this::updateSearch);
        this.addRenderableWidget(this.searchBox);

        this.rangeWidget = new StringWidget(x + 100, y - 68, 100, 20, Component.empty(), this.font);
        this.addRenderableWidget(this.rangeWidget);

        updateSelectors(x, y);

        this.maxPriceInput = new EditBox(this.font, x + 155, y - 45, 30, 20, Component.literal("Max"));
        this.maxPriceInput.setValue(String.valueOf(AutoTradeClient.maxEmeralds));
        this.addRenderableWidget(this.maxPriceInput);

        this.toggleButton = Button.builder(Component.literal(AutoTradeClient.isActive ? "Stop" : "Start"), (button) -> {
            if (isLocked()) {
                AutoTradeClient.isActive = false;
                button.setMessage(Component.literal("Locked"));
                return;
            }
            AutoTradeClient.isActive = !AutoTradeClient.isActive;
            this.toggleButton.setMessage(Component.literal(AutoTradeClient.isActive ? "Stop" : "Start"));
            if (AutoTradeClient.isActive) {
                try {
                    AutoTradeClient.maxEmeralds = Integer.parseInt(this.maxPriceInput.getValue());
                } catch (NumberFormatException e) {
                    AutoTradeClient.maxEmeralds = 64;
                }
            }
        }).bounds(x + 190, y - 45, 45, 20).build();
        
        if (isLocked()) {
            this.toggleButton.active = false;
            this.toggleButton.setMessage(Component.literal("Locked"));
        }
        
        this.addRenderableWidget(this.toggleButton);
        updateRangeText();
    }

    private boolean checkIsLibrarian() {
        var entity = Minecraft.getInstance().crosshairPickEntity;
        if (entity instanceof Villager villager) {
            return villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
        }
        return false;
    }

    private boolean isLocked() {
        return this.getMenu().getTraderLevel() > 1 || this.getMenu().getTraderXp() > 0;
    }

    @Override
    public void removed() {
        super.removed();
        AutoTradeClient.isActive = false;
    }

    private void updateSearch(String query) {
        int x = (this.width - 276) / 2;
        int y = (this.height - 166) / 2;
        updateSelectors(x, y);
    }

    private void updateSelectors(int x, int y) {
        String query = this.searchBox != null ? this.searchBox.getValue().toLowerCase() : "";
        List<ResourceKey<Enchantment>> filtered = allEnchantments.stream()
                .filter(k -> k.identifier().getPath().toLowerCase().contains(query))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) filtered = allEnchantments;

        ResourceKey<Enchantment> current = null;
        if (AutoTradeClient.targetEnchantment != null) {
            current = ResourceKey.create(Registries.ENCHANTMENT, AutoTradeClient.targetEnchantment);
        }
        
        final ResourceKey<Enchantment> initial = (current != null && filtered.contains(current)) ? current : filtered.get(0);
        AutoTradeClient.targetEnchantment = initial.identifier();

        if (this.enchantmentSelector != null) this.removeWidget(this.enchantmentSelector);
        if (this.levelSelector != null) this.removeWidget(this.levelSelector);

        this.enchantmentSelector = new CycleButton.Builder<ResourceKey<Enchantment>>(
                (ResourceKey<Enchantment> key) -> Component.literal(key.identifier().getPath()),
                () -> initial
        ).withValues(filtered).displayOnlyValue().create(x + 5, y - 45, 90, 20, Component.empty(), (button, value) -> {
            AutoTradeClient.targetEnchantment = value.identifier();
            updateLevelSelector(x, y);
            updateRangeText();
        });

        this.addRenderableWidget(this.enchantmentSelector);
        updateLevelSelector(x, y);
        updateRangeText();
    }

    private void updateLevelSelector(int x, int y) {
        if (this.levelSelector != null) this.removeWidget(this.levelSelector);

        var registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var enchantment = registry.get(ResourceKey.create(Registries.ENCHANTMENT, AutoTradeClient.targetEnchantment)).get().value();
        int maxLvl = enchantment.getMaxLevel();

        List<Integer> levels = new ArrayList<>();
        for (int i = 1; i <= maxLvl; i++) levels.add(i);
        if (AutoTradeClient.targetLevel > maxLvl) AutoTradeClient.targetLevel = maxLvl;

        this.levelSelector = new CycleButton.Builder<Integer>(
                (Integer level) -> Component.literal("Lvl: " + level),
                () -> AutoTradeClient.targetLevel
        ).withValues(levels).displayOnlyValue().create(x + 100, y - 45, 50, 20, Component.empty(), (button, value) -> {
            AutoTradeClient.targetLevel = value;
            updateRangeText();
        });

        this.addRenderableWidget(this.levelSelector);
    }

    private void updateRangeText() {
        if (this.rangeWidget != null && AutoTradeClient.targetEnchantment != null) {
            int[] range = AutoTradeClient.getPriceRange(AutoTradeClient.targetEnchantment, AutoTradeClient.targetLevel);
            this.rangeWidget.setMessage(Component.literal("Range: " + range[0] + "-" + range[1]));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isLibrarian && (this.searchBox.isFocused() || this.maxPriceInput.isFocused())) {
            if (this.searchBox.keyPressed(event) || this.maxPriceInput.keyPressed(event)) {
                return true;
            }
            if (Minecraft.getInstance().options.keyInventory.matches(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.isLibrarian && AutoTradeClient.isActive) {
            if (isLocked()) {
                AutoTradeClient.isActive = false;
                this.toggleButton.setMessage(Component.literal("Locked"));
                this.toggleButton.active = false;
                return;
            }
            MerchantOffers offers = this.getMenu().getOffers();
            if (offers != null && !offers.isEmpty()) {
                boolean found = false;
                for (MerchantOffer offer : offers) {
                    ItemStack result = offer.getResult();
                    if (result.is(Items.ENCHANTED_BOOK)) {
                        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
                        for (var entry : enchantments.entrySet()) {
                            var key = entry.getKey().unwrapKey();
                            if (key.isPresent() && key.get().identifier().equals(AutoTradeClient.targetEnchantment)) {
                                if (entry.getIntValue() >= AutoTradeClient.targetLevel) {
                                    int price = offer.getBaseCostA().getCount();
                                    if (price <= AutoTradeClient.maxEmeralds) {
                                        AutoTradeClient.isActive = false;
                                        this.toggleButton.setMessage(Component.literal("Start"));
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (found) break;
                }
                if (!found && AutoTradeClient.isActive) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new CycleTradesPacket());
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (this.isLibrarian && AutoTradeClient.targetEnchantment != null) {
            int[] range = AutoTradeClient.getPriceRange(AutoTradeClient.targetEnchantment, AutoTradeClient.targetLevel);
            graphics.text(this.font, "Range: " + range[0] + " - " + range[1], 5, -25, 0xFFFF55);
        }
    }
}
