package org.gloyer057.cuffsx.cuff;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.lang.reflect.Method;

public class ClaimChecker {

    private static Boolean ftbChunksPresent = null;

    private static boolean isFtbChunksPresent() {
        if (ftbChunksPresent == null) {
            try {
                Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
                ftbChunksPresent = true;
            } catch (ClassNotFoundException e) {
                ftbChunksPresent = false;
            }
        }
        return ftbChunksPresent;
    }

    public static boolean isClaimed(ServerPlayerEntity applier, BlockPos pos) {
        if (!isFtbChunksPresent()) return false;
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Object api = apiClass.getMethod("api").invoke(null);

            Method getManagerMethod = null;
            for (Class<?> iface : api.getClass().getInterfaces()) {
                try { getManagerMethod = iface.getMethod("getManager"); break; }
                catch (NoSuchMethodException ignored) {}
            }
            if (getManagerMethod == null)
                getManagerMethod = api.getClass().getMethod("getManager");
            Object manager = getManagerMethod.invoke(api);

            ChunkPos chunkPos = new ChunkPos(pos);
            Object chunkDimPos = buildChunkDimPos(applier, pos, chunkPos);
            if (chunkDimPos == null) return false;

            Object claimedChunk = null;
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getChunk") && m.getParameterCount() == 1) {
                    try { claimedChunk = m.invoke(manager, chunkDimPos); break; }
                    catch (Exception ignored) {}
                }
            }
            if (claimedChunk == null) return false;

            Object chunkTeamData = invokeNoArg(claimedChunk, "getTeamData");
            if (chunkTeamData == null) return true;
            Object chunkTeam = invokeNoArg(chunkTeamData, "getTeam");
            if (chunkTeam == null) return true;
            Object chunkTeamId = invokeNoArg(chunkTeam, "getId");

            Object applierTeamData = null;
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getOrCreateData") && m.getParameterCount() == 1) {
                    try {
                        applierTeamData = m.invoke(manager, applier);
                        if (applierTeamData != null) break;
                    } catch (Exception ignored) {}
                }
            }
            if (applierTeamData == null) return true;

            Object applierTeam = invokeNoArg(applierTeamData, "getTeam");
            if (applierTeam == null) return true;
            Object applierTeamId = invokeNoArg(applierTeam, "getId");

            return chunkTeamId == null || !chunkTeamId.equals(applierTeamId);

        } catch (Exception e) {
            return false;
        }
    }

    private static Object buildChunkDimPos(ServerPlayerEntity applier, BlockPos pos, ChunkPos chunkPos) {
        String[] classes = {
            "dev.ftb.mods.ftblibrary.math.ChunkDimPos",
            "dev.ftb.mods.ftbchunks.api.ChunkDimPos"
        };
        for (String className : classes) {
            try {
                Class<?> cls = Class.forName(className);
                try {
                    return cls.getConstructor(net.minecraft.registry.RegistryKey.class, ChunkPos.class)
                            .newInstance(applier.getWorld().getRegistryKey(), chunkPos);
                } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException |
                         InstantiationException | IllegalAccessException ignored) {}
                try {
                    return cls.getConstructor(net.minecraft.world.World.class, BlockPos.class)
                            .newInstance(applier.getWorld(), pos);
                } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException |
                         InstantiationException | IllegalAccessException ignored) {}
                try {
                    return cls.getConstructors()[0].newInstance(
                            applier.getWorld().getRegistryKey(), chunkPos);
                } catch (Exception ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static Object invokeNoArg(Object obj, String methodName) {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                try { return m.invoke(obj); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
