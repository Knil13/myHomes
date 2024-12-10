package fr.knil.myhomes.util;

public class MutablePosition {
    public int x, y, z;
    public float yaw, pitch;

    public MutablePosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public MutablePosition() {
        
    }
    
    public MutablePosition(int x, int y, int z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public void set(int x, int y, int z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void add(int dx, int dy, int dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }
    
    public Integer getX() {    	
    	return this.x;
    }
    
    public Integer getY() {    	
    	return this.y;
    }
    
    public Integer getZ() {    	
    	return this.z;
    }
    
    public float getYaw() {    	
    	return this.yaw;
    }
    
    public float getPitch() {    	
    	return this.pitch;
    }
    

    @Override
    public String toString() {
        return "MutablePosition{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
