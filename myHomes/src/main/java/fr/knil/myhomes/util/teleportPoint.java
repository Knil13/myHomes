package fr.knil.myhomes.util;

import net.minecraft.server.world.ServerWorld;

public class teleportPoint {
	private ServerWorld world; 
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public teleportPoint(ServerWorld world, double x, double y, double z, float yaw, float pitch) {
    	this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    // Getters
    public ServerWorld getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
