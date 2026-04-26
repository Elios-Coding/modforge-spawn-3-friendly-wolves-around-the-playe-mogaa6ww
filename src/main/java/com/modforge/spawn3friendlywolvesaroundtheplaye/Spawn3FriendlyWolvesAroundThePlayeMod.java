package com.modforge.spawn3friendlywolvesaroundtheplaye;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Spawn3FriendlyWolvesAroundThePlayeMod implements ModInitializer {
    public static final String MOD_ID = "spawn3friendlywolvesaroundtheplaye";
    private static final Logger LOGGER = LoggerFactory.getLogger(Spawn3FriendlyWolvesAroundThePlayeMod.class);

    private final Map<UUID, Boolean> lastSneaking = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        try {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            Set<UUID> online = new HashSet<>();

            for (ServerPlayerEntity player : players) {
                UUID id = player.getUuid();
                online.add(id);

                boolean isSneakingNow = player.isSneaking();
                boolean wasSneaking = lastSneaking.getOrDefault(id, false);

                if (isSneakingNow && !wasSneaking && isHoldingBone(player)) {
                    try {
                        spawnWolvesAroundPlayer(player);
                        player.sendMessage(Text.literal("Three friendly wolves have joined you!"), true);
                    } catch (Exception e) {
                        LOGGER.error("Failed to spawn wolves for player {}: {}", player.getGameProfile().getName(), e.toString());
                    }
                }

                lastSneaking.put(id, isSneakingNow);
            }

            // Clean up entries for players no longer online
            lastSneaking.keySet().retainAll(online);
        } catch (Exception e) {
            LOGGER.error("Error during END_SERVER_TICK processing: {}", e.toString());
        }
    }

    private boolean isHoldingBone(ServerPlayerEntity player) {
        try {
            return player.getMainHandStack().isOf(Items.BONE) || player.getOffHandStack().isOf(Items.BONE);
        } catch (Exception e) {
            LOGGER.error("Error checking held item for player {}: {}", player.getGameProfile().getName(), e.toString());
            return false;
        }
    }

    private void spawnWolvesAroundPlayer(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double px = player.getX();
        double pz = player.getZ();

        // Three positions around the player
        double[][] offsets = new double[][]{
                {1.5, 0.0},
                {-1.5, 0.0},
                {0.0, 1.5}
        };

        for (double[] off : offsets) {
            double ox = off[0];
            double oz = off[1];
            spawnSingleWolf(world, player, px + ox, pz + oz);
        }
    }

    private void spawnSingleWolf(ServerWorld world, ServerPlayerEntity owner, double x, double z) {
        try {
            int bx = MathHelper.floor(x);
            int bz = MathHelper.floor(z);
            int by = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            double sx = bx + 0.5;
            double sz = bz + 0.5;
            double sy = by;

            WolfEntity wolf = EntityType.WOLF.create(world);
            if (wolf == null) {
                LOGGER.warn("Could not create WolfEntity instance in world {}", world.getRegistryKey().getValue());
                return;
            }

            wolf.refreshPositionAndAngles(sx, sy, sz, owner.getYaw(), 0.0f);
            wolf.setTamed(true);
            wolf.setOwnerUuid(owner.getUuid());
            wolf.setSitting(false);
            wolf.setHealth(wolf.getMaxHealth());

            boolean spawned = world.spawnEntity(wolf);
            if (!spawned) {
                LOGGER.warn("World refused to spawn wolf at {},{},{} in {}", sx, sy, sz, world.getRegistryKey().getValue());
            }
        } catch (Exception e) {
            LOGGER.error("Exception while spawning wolf near player {}: {}", owner.getGameProfile().getName(), e.toString());
        }
    }
}
