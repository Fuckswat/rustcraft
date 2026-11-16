package rustcraft.vehicles.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import rustcraft.vehicles.VehiclesModule;

/** Транспортный вертолёт: пилот + 5 пассажиров, медленнее, прочнее, большой бак. */
public class ScrapHeliEntity extends AbstractHeliEntity {
    private static final double[][] SEATS = {
            {0.44D, 0.75D, 1.81D},    // pilot
            {-0.44D, 0.75D, 1.81D},   // co-pilot
            {0.53D, 0.75D, 0.375D},
            {-0.53D, 0.75D, 0.375D},
            {0.53D, 0.75D, -0.625D},
            {-0.53D, 0.75D, -0.625D},
    };

    public ScrapHeliEntity(EntityType<? extends ScrapHeliEntity> type, World world) {
        super(type, world);
    }

    @Override public float getMaxHealth() { return 400.0F; }
    @Override public float getFuelCapacity() { return 120 * 100.0F; }    // 120 fuel items
    @Override protected float getFuelBurn() { return 0.5F; }
    @Override public int getMaxPassengers() { return 6; }
    @Override protected double[] getSeat(int index) { return SEATS[Math.min(index, SEATS.length - 1)]; }
    @Override public Item getVehicleItem() { return VehiclesModule.SCRAP_HELI_ITEM.get(); }
    @Override protected float getExplosionPower() { return 4.0F; }
    @Override protected int getScrapDrop() { return 20; }
    @Override protected int getFragmentDrop() { return 150; }
    @Override protected float getSpoolUp() { return 0.012F; }
    @Override protected float getSpoolDown() { return 0.007F; }
    @Override protected double getCrashDamageFactor() { return 200.0D; }
    @Override protected double[] getEnginePos() { return new double[]{0.0D, 2.0D, -1.4D}; }
    @Override protected float getRotorDegreesPerTick() { return 37.0F; }

    @Override protected double getMaxSpeed() { return 0.72D; }   // ~14 blocks/s
    @Override protected double getClimbRate() { return 0.26D; }
    @Override protected double getDescendRate() { return 0.25D; }
    @Override protected float getMaxYawRate() { return 2.5F; }
    @Override protected float getMaxPitch() { return 15.0F; }
    @Override protected float getPitchRate() { return 1.2F; }
}
