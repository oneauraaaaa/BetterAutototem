package com.oneaura.betterautototem.mixin;

import com.oneaura.betterautototem.BetterAutototemClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
// This import is no longer needed
// import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.effect.StatusEffects;
// This import is no longer needed
// import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
// This import is no longer needed
// import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
// This import is no longer needed
// import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class TotemPopMixin {

    @Shadow @Final private MinecraftClient client;

    // --- State Machine for multistep restock ---
    @Unique
    private enum RestockState {
        IDLE,
        WAITING_FOR_RESTOCK_DELAY, // Waiting for the *first* delay (restockDelayTicks)
        WAITING_FOR_INVENTORY_CLOSE  // Waiting for the *second* delay (inventoryDelayTicks)
    }

    @Unique
    private RestockState currentState = RestockState.IDLE;
    @Unique
    private int restockTimer = 0;
    @Unique
    private int inventoryCloseTimer = 0;
    @Unique
    private int totemSlotToRestock = -1;
    // --- End State Machine ---


    /**
     * This tick listener now runs our state machine.
     */
    @Inject(at = @At("HEAD"), method = "tick")
    private void onTick(CallbackInfo ci) {
        // Ensure we are in a valid state to tick
        if (this.currentState == RestockState.IDLE || this.client.player == null || this.client.getNetworkHandler() == null) {
            return;
        }

        ClientPlayerInteractionManager interactionManager = this.client.interactionManager;
        // The networkHandler is no longer needed here
        // ClientPlayNetworkHandler networkHandler = this.client.getNetworkHandler();
        if (interactionManager == null) {
            this.currentState = RestockState.IDLE;
            return;
        }

        switch (this.currentState) {
            case WAITING_FOR_RESTOCK_DELAY:
                if (this.restockTimer > 0) {
                    this.restockTimer--;
                    break;
                }

                // Timer is 0, time to act.
                // Safety check
                if (this.totemSlotToRestock == -1) {
                    this.currentState = RestockState.IDLE;
                    break;
                }

                int inventoryDelay = BetterAutototemClient.CONFIG.inventoryDelayTicks;

                if (inventoryDelay <= 0) {
                    // --- PATH 1: FAST SWAP (Inventory Delay is 0) ---
                    // This is the "instant" swap.
                    interactionManager.clickSlot(
                            this.client.player.playerScreenHandler.syncId,
                            this.totemSlotToRestock,
                            40, // 40 is the offhand slot
                            SlotActionType.SWAP,
                            this.client.player
                    );
                    this.currentState = RestockState.IDLE;
                } else {
                    // --- PATH 2: SLOW SWAP (Inventory Delay > 0) ---
                    // 1. Open inventory
                    // This opens the screen on the client
                    this.client.setScreen(new InventoryScreen(this.client.player));
                    // This tells the server we opened it - THIS LINE IS REMOVED
                    // networkHandler.sendPacket(new ClientCommandC2SPacket(this.client.player, ClientCommandC2SPacket.Mode.OPEN_INVENTORY_TAB));

                    // 2. Perform swap
                    interactionManager.clickSlot(
                            this.client.player.playerScreenHandler.syncId,
                            this.totemSlotToRestock,
                            40, // 40 is the offhand slot
                            SlotActionType.SWAP,
                            this.client.player
                    );
                    // 3. Set close timer and new state
                    this.inventoryCloseTimer = inventoryDelay;
                    this.currentState = RestockState.WAITING_FOR_INVENTORY_CLOSE;
                }
                this.totemSlotToRestock = -1; // Clear slot
                break;

            case WAITING_FOR_INVENTORY_CLOSE:
                if (this.inventoryCloseTimer > 0) {
                    this.inventoryCloseTimer--;
                    break;
                }

                // Timer is 0, time to close.
                // 4. Close inventory
                // We check if the screen is open and then close it.
                // This method also sends the correct CloseHandledScreenC2SPacket packet.
                if (this.client.currentScreen instanceof InventoryScreen) {
                    this.client.player.closeHandledScreen();
                }
                this.currentState = RestockState.IDLE;
                break;

            case IDLE:
            default:
                // Do nothing
                break;
        }
    }

    /**
     * This method just *starts* the process by setting the state.
     */
    @Inject(at = @At("TAIL"), method = "showFloatingItem")
    private void onTotemUse(ItemStack floatingItem, CallbackInfo ci) {
        // Check if we are already busy
        if (this.currentState != RestockState.IDLE) {
            return;
        }

        // Check if mod is enabled in config
        if (!BetterAutototemClient.CONFIG.modEnabled) {
            return;
        }

        // Standard checks
        if (!floatingItem.isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (this.client.player == null) {
            return;
        }
        if (!this.client.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) || !this.client.player.hasStatusEffect(StatusEffects.REGENERATION)) {
            return;
        }

        // Find totem
        int spareTotemSlot = getSlotWithSpareTotem(this.client.player.getInventory());
        if (spareTotemSlot == -1) {
            return;
        }

        // --- START THE STATE MACHINE ---
        // Instead of acting, we just set the timers and state.
        // The onTick() method will handle the rest.
        this.totemSlotToRestock = spareTotemSlot;
        this.restockTimer = BetterAutototemClient.CONFIG.restockDelayTicks;
        this.currentState = RestockState.WAITING_FOR_RESTOCK_DELAY;
    }

    @Unique
    private int getSlotWithSpareTotem(PlayerInventory inventory) {
        // Find totem in main inventory (9-35) or hotbar (0-8)
        for (int i = 0; i < inventory.size(); i++) {
            // We skip the offhand slot (40) because that's where we're putting it
            if (i == 40) continue;

            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                // We must convert the inventory slot to the correct slot ID for clickSlot
                // Hotbar slots 0-8 are mapped to 36-44
                if (i >= 0 && i <= 8) {
                    return 36 + i;
                }
                // Main inventory 9-35 are mapped directly
                if (i >= 9 && i <= 35) {
                    return i;
                }
            }
        }
        return -1; // No totem found
    }
}

