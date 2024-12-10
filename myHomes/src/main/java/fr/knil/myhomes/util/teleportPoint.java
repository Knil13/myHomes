package fr.knil.myhomes.util;

public class teleportPoint {
	private String world; 
    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;
    
    public teleportPoint() {
    	
    }

    public teleportPoint(String world, double x, double y, double z, double yaw, double pitch) {
    	this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    public void set(String world, double x, double y, double z, double yaw, double pitch) {
    	this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    // Getters
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getYaw() { return yaw; }
    public double getPitch() { return pitch; }
}
