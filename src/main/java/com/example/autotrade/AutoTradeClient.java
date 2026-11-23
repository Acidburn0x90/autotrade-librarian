package com.example.autotrade;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;

public class AutoTradeClient implements ClientModInitializer {
    public static boolean isActive = false;
    public static Identifier targetEnchantment = null;
    public static int targetLevel = 1;
    public static int maxEmeralds = 64;

    public static final TagKey<Enchantment> DOUBLE_TRADE_PRICE = TagKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", "double_trade_price"));

    @Override
    public void onInitializeClient() {
    }

    public static int[] getPriceRange(Identifier enchantmentId, int level) {
        int min = 5 + (level - 1) * 3;
        int max = 19 + (level - 1) * 13;

        try {
            var registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var holder = registry.get(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
            
            if (holder.isPresent() && holder.get().is(DOUBLE_TRADE_PRICE)) {
                min *= 2;
                max *= 2;
            }
        } catch (Exception e) {
            // Default to non-doubled if registry lookup fails
        }

        return new int[]{Math.min(min, 64), Math.min(max, 64)};
    }
}
