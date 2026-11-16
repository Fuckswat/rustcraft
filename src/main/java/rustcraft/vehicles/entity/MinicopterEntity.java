package rustcraft.vehicles.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import rustcraft.vehicles.VehiclesModule;

/** Миникоптер: пилот + 1 пассажир, быстрый и вёрткий. */
public class MinicopterEntity extends AbstractHeliEntity {
    private static final double[][] SEATS = {
            {-0.31D, 0.75D, 0.44D},   // pilot
            {0.31D, 0.75D, 0.44D},
    };

    public MinicopterEntity(EntityType<? extends MinicopterEntity> type, World world) {
        super(type, world);
    }

    @Override public float getMaxHealth() { return 150.0F; }
    @Override public float getFuelCapacity() { return 50 * 100.0F; }     // 50 fuel items
    @Override protected float getFuelBurn() { return 0.3F; }              // ~1 item / 17 s
    @Override public int getMaxPassengers() { return 2; }
    @Override protected double[] getSeat(int index) { return SEATS[Math.min(index, SEATS.length - 1)]; }
    @Override public Item getVehicleItem() { return VehiclesModule.MINICOPTER_ITEM.get(); }
    @Override protected float getExplosionPower() { return 3.0F; }
    @Override protected int getScrapDrop() { return 8; }
    @Override protected int getFragmentDrop() { return 50; }
    @Override protected float getSpoolUp() { return 0.02F; }
    @Override protected float getSpoolDown() { return 0.01F; }
    @Override protected double getCrashDamageFactor() { return 100.0D; }
    @Override protected double[] getEnginePos() { return new double[]{0.0D, 1.6D, -0.5D}; }

    @Override protected double getMaxSpeed() { return 0.95D; }   // ~19 blocks/s
    @Override protected double getClimbRate() { return 0.35D; }
    @Override protected double getDescendRate() { return 0.3D; }
    @Override protected float getMaxYawRate() { return 4.0F; }
    @Override protected float getMaxPitch() { return 22.0F; }
    @Override protected float getPitchRate() { return 2.0F; }
}
