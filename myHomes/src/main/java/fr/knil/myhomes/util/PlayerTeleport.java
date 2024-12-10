package fr.knil.myhomes.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class PlayerTeleport {

    public static void teleportPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (player == null || world == null || pos == null) {
            System.out.println("Invalid teleport parameters.");
            return;
        }

        player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
    }
    
    public static void teleportPlayer(ServerPlayerEntity player, teleportPoint tp) {
    	if (player == null ||  tp == null) {
            System.out.println("Invalid teleport parameters.");
            return;
        }
    	
    	String w = tp.getWorld();    	
    	ServerWorld world = player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(w)));    	
    	player.teleport(world , tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5, (float) tp.getYaw(), (float) tp.getPitch());
    }
}
