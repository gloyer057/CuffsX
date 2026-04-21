package org.gloyer057.cuffsx.cuff;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Проверяет является ли позиция приватом FTB Chunks.
 * API: manager.getChunk(ChunkDimPos) -> ClaimedChunk (null если не заклеймен)
 */
public class ClaimChecker {

    public static boolean isClaimed(ServerPlayerEntity applier, BlockPos pos) {
        if (!FabricLoader.getInstance().isModLoaded("ftbchunks")) return false;
        try {
            // FTBChunksAPI.api().getManager()
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            Object manager = api.getClass().getMethod("getManager").invoke(api);

            // Создаём ChunkDimPos(registryKey, chunkX, chunkZ)
            Class<?> chunkDimPosClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;

            Object chunkDimPos = null;
            for (Constructor<?> c : chunkDimPosClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 3) {
                    // ChunkDimPos(RegistryKey, int, int)
                    try {
                        chunkDimPos = c.newInstance(applier.getWorld().getRegistryKey(), chunkX, chunkZ);
                        break;
                    } catch (Exception ignored) {}
                }
                if (params.length == 2 && params[1] == long.class) {
                    // ChunkDimPos(RegistryKey, long) — packed chunk pos
                    try {
                        long packed = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                        chunkDimPos = c.newInstance(applier.getWorld().getRegistryKey(), packed);
                        break;
                    } catch (Exception ignored) {}
                }
            }

            if (chunkDimPos == null) {
                // Пробуем статический фабричный метод
                for (Method m : chunkDimPosClass.getMethods()) {
                    if (m.getParameterCount() == 3) {
                        try {
                            chunkDimPos = m.invoke(null, applier.getWorld().getRegistryKey(), chunkX, chunkZ);
                            if (chunkDimPos != null) break;
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (chunkDimPos == null) return false;

            // manager.getChunk(chunkDimPos)
            Method getChunk = null;
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getChunk") && m.getParameterCount() == 1) {
                    getChunk = m;
                    break;
                }
            }
            if (getChunk == null) return false;

            Object chunk = getChunk.invoke(manager, chunkDimPos);
            if (chunk == null) return false; // чанк не заклеймен

            // Получаем teamData из chunk
            Method getTeamData = null;
            for (Method m : chunk.getClass().getMethods()) {
                if (m.getName().equals("getTeamData") && m.getParameterCount() == 0) {
                    getTeamData = m;
                    break;
                }
            }
            if (getTeamData == null) return true; // заклеймен, но не можем проверить команду

            Object teamData = getTeamData.invoke(chunk);
            if (teamData == null) return true;

            // Получаем UUID владельца команды
            UUID ownerUuid = null;
            for (Method m : teamData.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                String name = m.getName();
                if (name.equals("getOwner") || name.equals("getOwnerUUID") || name.equals("getOwnerUuid")) {
                    Object result = m.invoke(teamData);
                    if (result instanceof UUID) { ownerUuid = (UUID) result; break; }
                }
            }
            // Через getTeam().getOwner()
            if (ownerUuid == null) {
                for (Method m : teamData.getClass().getMethods()) {
                    if (m.getName().equals("getTeam") && m.getParameterCount() == 0) {
                        Object team = m.invoke(teamData);
                        if (team != null) {
                            for (Method tm : team.getClass().getMethods()) {
                                if (tm.getParameterCount() != 0) continue;
                                String n = tm.getName();
                                if (n.equals("getOwner") || n.equals("getOwnerUUID") || n.equals("getOwnerUuid")) {
                                    Object r = tm.invoke(team);
                                    if (r instanceof UUID) { ownerUuid = (UUID) r; break; }
                                }
                            }
                        }
                        break;
                    }
                }
            }

            // Если applier — владелец, разрешаем
            if (ownerUuid != null && ownerUuid.equals(applier.getUuid())) return false;

            // Проверяем членство applier в команде владельца
            for (Method m : manager.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                String name = m.getName();
                if (!name.toLowerCase().contains("team") && !name.toLowerCase().contains("player")) continue;
                try {
                    Object applierTeamData = m.invoke(manager, applier.getUuid());
                    if (applierTeamData == null) continue;
                    // Сравниваем команды
                    for (Method tm : applierTeamData.getClass().getMethods()) {
                        if (tm.getName().equals("getTeam") && tm.getParameterCount() == 0) {
                            Object applierTeam = tm.invoke(applierTeamData);
                            for (Method otm : teamData.getClass().getMethods()) {
                                if (otm.getName().equals("getTeam") && otm.getParameterCount() == 0) {
                                    Object ownerTeam = otm.invoke(teamData);
                                    if (applierTeam != null && applierTeam.equals(ownerTeam)) return false;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            return true; // заклеймен чужой командой

        } catch (Exception e) {
            return false;
        }
    }
}
