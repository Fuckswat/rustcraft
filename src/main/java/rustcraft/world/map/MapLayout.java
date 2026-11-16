package rustcraft.world.map;

/** The saved v2 layout. Keep generation deterministic so interrupted builds resume safely. */
public final class MapLayout {
    public static final int VERSION = 2;
    public static final int RADIUS = 128;
    public static final int MIN_X = -165, MAX_X = 230, MIN_Z = -165, MAX_Z = 165;
    public static final int SEA = 62;
    private MapLayout() {}

    public static double edge(int x, int z) {
        return Math.pow(Math.pow(Math.abs(x) / 128.0, 6) + Math.pow(Math.abs(z) / 128.0, 6), 1.0 / 6.0);
    }

    public static boolean isLand(int x, int z) { return edge(x, z) <= 1.0; }

    private static boolean rect(int x, int z, int cx, int cz, int rx, int rz) {
        return Math.abs(x-cx)<=rx && Math.abs(z-cz)<=rz;
    }

    /** Monument pads and their approach paths, deliberately separate from mountains and resources. */
    public static boolean pad(int x, int z) {
        return rect(x,z,0,0,12,12) || rect(x,z,50,-54,22,22)
            || rect(x,z,-52,40,14,13) || rect(x,z,-55,-43,14,13)
            || rect(x,z,48,49,12,12) || rect(x,z,113,0,20,20)
            || rect(x,z,96,39,15,14);
    }

    /** Broad, empty building clearings, with the mountain ridges kept near the island edges. */
    public static boolean buildingPlot(int x,int z) {
        return rect(x,z,-10,-55,18,16)||rect(x,z,-14,46,22,18)
            ||rect(x,z,-89,9,18,20)||rect(x,z,-74,83,22,17)||rect(x,z,91,-83,20,19);
    }

    public static boolean road(int x, int z) {
        return segment(x,z,-55,-43,-52,40,2.3) || segment(x,z,-52,0,113,0,2.3)
            || segment(x,z,50,-54,50,49,2.3) || segment(x,z,-52,40,48,49,2.3)
            || segment(x,z,96,0,96,39,2.3)
            || segment(x,z,-10,-55,-10,0,1.6)||segment(x,z,-14,46,-14,0,1.6)
            || segment(x,z,-89,9,-52,9,1.6)||segment(x,z,-74,83,-52,40,1.6)
            || segment(x,z,91,-83,91,0,1.6);
    }

    private static boolean segment(int x,int z,int ax,int az,int bx,int bz,double width) {
        double dx=bx-ax,dz=bz-az,t=((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz);
        t=Math.max(0,Math.min(1,t));
        double ex=x-ax-t*dx,ez=z-az-t*dz;
        return ex*ex+ez*ez<=width*width;
    }

    public static int surfaceHeight(int x,int z) {
        double e=edge(x,z);
        if(e>1.0) return SEA-5-(int)Math.min(10,(e-1)*22);
        if(pad(x,z)||road(x,z)||buildingPlot(x,z)) return 64;
        double coast=Math.min(1,Math.max(0,(1-e)/0.13));
        double mountain=43*Math.exp(-((x+66.0)*(x+66.0)/1000+(z+88.0)*(z+88.0)/700));
        double hill=16*Math.exp(-((x-72.0)*(x-72.0)/850+(z-91.0)*(z-91.0)/500));
        double waves=2.0+2.0*Math.sin(x*0.075)*Math.cos(z*0.065)+1.4*Math.sin((x+z)*0.11);
        // Smoothly blend into every pad/road, avoiding vertical cliffs around playable areas.
        double blend=1;
        for(int dx=-8;dx<=8;dx+=2)for(int dz=-8;dz<=8;dz+=2)
            if(pad(x+dx,z+dz)||road(x+dx,z+dz)||buildingPlot(x+dx,z+dz)) blend=Math.min(blend,Math.sqrt(dx*dx+dz*dz)/8.0);
        return SEA+(int)Math.round(coast*(2+(mountain+hill+waves)*blend));
    }
}
