package com.oneaura.betterautototem.mixin;

import com.oneaura.betterautototem.BetterAutototemClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class TotemPopMixin {

    // Timer to handle the delay. -1 means inactive.
    private int restockDelayTimer = -1;
    private int totemSlotToRestock = -1;

    /**
     * Injects at the head of the client tick method.
     * Manages the delay timer for restocking.
     */
    @Inject(at = @At("HEAD"), method = "tick")
    private void onTick(CallbackInfo ci) {
        // If the timer is inactive, do nothing.
        if (this.restockDelayTimer < 0) {
            return;
        }

        // If the timer is active but not yet finished, count down.
        if (this.restockDelayTimer > 0) {
            this.restockDelayTimer--;
            return;
        }

        // Timer has hit 0, execute the restock.
        this.restockDelayTimer = -1; // Reset the timer
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;

        if (player == null || interactionManager == null || this.totemSlotToRestock == -1) {
            return;
        }

        System.out.println("[BetterAutoTotem] Executing restock from slot " + this.totemSlotToRestock);

        // Calculate the correct slot ID for the click packet
        int slotId = this.totemSlotToRestock < 9 ? this.totemSlotToRestock + 36 : this.totemSlotToRestock;

        // Perform the client-side swap action.
        interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                slotId,      // The slot where the totem is
                40,          // The button code for swapping with the offhand slot
                SlotActionType.SWAP,
                player
        );

        // Reset the slot cache
        this.totemSlotToRestock = -1;
    }

    /**
     * Injects at the tail of the showFloatingItem method (totem pop).
     * This now checks the config and queues the restock with a delay.
     */
    @Inject(at = @At("TAIL"), method = "showFloatingItem")
    private void onTotemUse(ItemStack floatingItem, CallbackInfo ci) {
        // Check if the mod is enabled in the config
        if (!BetterAutototemClient.CONFIG.modEnabled) {
            return;
        }

        if (!floatingItem.isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;

        // Don't queue another restock if one is already pending
        if (player == null || this.restockDelayTimer != -1) {
            return;
        }

        // Check for totem effects
        if (!player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) || !player.hasStatusEffect(StatusEffects.REGENERATION)) {
            return;
        }

        int spareTotemSlot = getSlotWithSpareTotem(player.getInventory());
        if (spareTotemSlot == -1) {
            System.out.println("[BetterAutoTotem] No spare totem found.");
            return;
        }

        // Queue the restock by setting the delay timer from the config
        System.out.println("[BetterAutoTotem] Queuing restock from slot " + spareTotemSlot + " with a delay of " + BetterAutototemClient.CONFIG.restockDelayTicks + " ticks.");
        this.restockDelayTimer = BetterAutototemClient.CONFIG.restockDelayTicks;
        this.totemSlotToRestock = spareTotemSlot;
    }

    /**
     * Finds the first available totem of undying in the player's inventory.
     *
     * @param inventory The player's inventory.
     * @return The slot index of the totem, or -1 if none is found.
     */
    private int getSlotWithSpareTotem(PlayerInventory inventory) {
        // Check main inventory (slots 0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }
}


