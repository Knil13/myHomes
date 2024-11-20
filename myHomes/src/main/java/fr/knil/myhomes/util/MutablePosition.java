package fr.knil.myhomes.util;

public class MutablePosition {
    public int x, y, z;

    public MutablePosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public MutablePosition() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
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

    @Override
    public String toString() {
        return "MutablePosition{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
