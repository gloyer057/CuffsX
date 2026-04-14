package org.gloyer057.cuffsx;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.gloyer057.cuffsx.command.CuffsxCommand;
import org.gloyer057.cuffsx.cuff.CuffManager;
import org.gloyer057.cuffsx.cuff.CuffState;
import org.gloyer057.cuffsx.cuff.CuffType;
import org.gloyer057.cuffsx.item.HandcuffsItem;
import org.gloyer057.cuffsx.item.ModItemGroups;
import org.gloyer057.cuffsx.item.ModItems;

public class Cuffsx implements ModInitializer {

    @Override
    public void onInitialize() {
        // 10.1 — register items and creative tab
        ModItems.register();
        ModItemGroups.register();

        // 10.2 — apply/remove cuffs via right-click on player
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity applier)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            if (applier == target) return ActionResult.PASS;

            ItemStack stack = applier.getStackInHand(hand);
            if (stack.getItem() instanceof HandcuffsItem hi) {
                CuffManager.applyCuffs(applier, target, hi.getCuffType());
                return ActionResult.SUCCESS;
            }

            if (!CuffManager.isCuffed(applier) && CuffManager.isCuffed(target)) {
                CuffManager.removeCuffs(applier, target);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

        // 10.3 — block breaking and block interaction blocked for HANDS
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 10.4 — item use blocked for HANDS
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return TypedActionResult.pass(player.getStackInHand(hand));
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS)) {
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // 10.5 — teleport LEGS-cuffed players back to locked position each tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CuffState state = CuffState.getOrCreate(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                state.getRecords(player.getUuid()).stream()
                    .filter(r -> r.cuffType() == CuffType.LEGS)
                    .findFirst()
                    .ifPresent(r -> {
                        if (player.getPos().distanceTo(r.lockedPos()) > 0.1) {
                            player.teleport(r.lockedPos().x, r.lockedPos().y, r.lockedPos().z);
                        }
                    });
            }
        });

        // 10.6 — re-apply restrictions when a player joins or respawns
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            CuffState state = CuffState.getOrCreate(server);
            state.getRecords(player.getUuid()).forEach(r ->
                CuffManager.applyRestrictions(player, r.cuffType()));
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            CuffState state = CuffState.getOrCreate(newPlayer.getServer());
            state.getRecords(newPlayer.getUuid()).forEach(r ->
                CuffManager.applyRestrictions(newPlayer, r.cuffType()));
        });

        // 10.7 — register /cuffsx commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            CuffsxCommand.register(dispatcher));
    }
}
