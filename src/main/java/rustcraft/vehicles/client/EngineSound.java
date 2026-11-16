package rustcraft.vehicles.client;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.TickableSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

/**
 * Зацикленный ванильный звук двигателя (без собственного sounds.json): высота тона и громкость
 * зависят от оборотов ротора/винта и газа.
 */
public class EngineSound extends TickableSound {
    private final AbstractVehicleEntity vehicle;
    private final float basePitch, pitchGain, throttleGain, volumeGain;
    private final boolean master;

    /**
     * @param master only the master layer clears {@code vehicle.soundActive} when it stops
     */
    public EngineSound(AbstractVehicleEntity vehicle, SoundEvent event, float basePitch, float pitchGain,
                       float throttleGain, float volumeGain, boolean master) {
        super(event, SoundCategory.NEUTRAL);
        this.vehicle = vehicle;
        this.basePitch = basePitch;
        this.pitchGain = pitchGain;
        this.throttleGain = throttleGain;
        this.volumeGain = volumeGain;
        this.master = master;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        this.volume = 0.01F;
        this.pitch = basePitch;
        this.x = vehicle.getPosX();
        this.y = vehicle.getPosY();
        this.z = vehicle.getPosZ();
    }

    @Override
    public boolean canBeSilent() {
        return true;
    }

    @Override
    public boolean shouldPlaySound() {
        return !this.vehicle.isSilent();
    }

    @Override
    public void tick() {
        float rotor = vehicle.getRotorSpeed();
        if (!vehicle.isAlive() || (rotor < 0.01F && !vehicle.isEngineOn())) {
            if (master) vehicle.soundActive = false;
            this.finishPlaying();
            return;
        }
        float thr = Math.abs(vehicle.getThrottleVisual());
        this.x = vehicle.getPosX();
        this.y = vehicle.getPosY() + 1.0D;
        this.z = vehicle.getPosZ();
        this.volume = Math.max(0.0F, rotor * volumeGain);
        this.pitch = Math.max(0.5F, Math.min(2.0F, basePitch + rotor * pitchGain + thr * throttleGain));
    }
}
