package rustcraft.guns;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;

/**
 * Характеристики оружия. Урон указан в «единицах Minecraft» (20 = полное здоровье игрока = 100 HP в Rust-шкале).
 */
public final class GunStats {
    public enum Ammo { PISTOL, RIFLE, SHOTGUN, ROCKET }

    public final String id;
    public final Ammo ammo;
    public final float damage;        // урон одной пули/дробины на ближней дистанции
    public final int pellets;         // дробин за выстрел
    public final int fireInterval;    // тиков между выстрелами
    public final boolean auto;        // автоматический огонь при зажатой кнопке
    public final int magSize;
    public final int reloadTicks;
    public final float range;         // максимальная дальность (блоки)
    public final float falloffStart;  // с какой дистанции урон начинает падать
    public final float minDamageMul;  // множитель урона на максимальной дальности
    public final float hipSpread;     // разброс от бедра (градусы)
    public final float adsMul;        // множитель разброса при прицеливании (ПКМ)
    public final float bloomPerShot;  // рост разброса при непрерывной стрельбе
    public final float maxBloom;
    public final float recoilPitch;   // подброс ствола вверх (градусы)
    public final float recoilYaw;     // случайный увод в стороны
    public final float headMul;       // множитель урона в голову
    public final float bleedChance;
    public final float zoom;          // множитель FOV при прицеливании
    public final SoundEvent shotSound;
    public final float shotPitch;
    public final SoundEvent shotSound2;
    public final float shotPitch2;
    public final float shotVolume;

    private GunStats(Builder b) {
        this.id = b.id; this.ammo = b.ammo; this.damage = b.damage; this.pellets = b.pellets;
        this.fireInterval = b.fireInterval; this.auto = b.auto; this.magSize = b.magSize; this.reloadTicks = b.reloadTicks;
        this.range = b.range; this.falloffStart = b.falloffStart; this.minDamageMul = b.minDamageMul;
        this.hipSpread = b.hipSpread; this.adsMul = b.adsMul; this.bloomPerShot = b.bloomPerShot; this.maxBloom = b.maxBloom;
        this.recoilPitch = b.recoilPitch; this.recoilYaw = b.recoilYaw; this.headMul = b.headMul; this.bleedChance = b.bleedChance;
        this.zoom = b.zoom; this.shotSound = b.shotSound; this.shotPitch = b.shotPitch; this.shotSound2 = b.shotSound2;
        this.shotPitch2 = b.shotPitch2; this.shotVolume = b.shotVolume;
    }

    public boolean isRocket() {
        return ammo == Ammo.ROCKET;
    }

    /** Множитель урона от дистанции: 1.0 до falloffStart, далее линейно до minDamageMul на range. */
    public float falloff(double dist) {
        if (dist <= falloffStart) return 1.0F;
        if (dist >= range) return minDamageMul;
        double t = (dist - falloffStart) / Math.max(1.0, range - falloffStart);
        return (float) (1.0 - t * (1.0 - minDamageMul));
    }

    static Builder builder(String id, Ammo ammo) {
        return new Builder(id, ammo);
    }

    static final class Builder {
        final String id; final Ammo ammo;
        float damage = 5; int pellets = 1; int fireInterval = 4; boolean auto = false; int magSize = 10; int reloadTicks = 40;
        float range = 80; float falloffStart = 30; float minDamageMul = 0.5F;
        float hipSpread = 2.0F; float adsMul = 0.35F; float bloomPerShot = 0.0F; float maxBloom = 0.0F;
        float recoilPitch = 2.0F; float recoilYaw = 0.6F; float headMul = 2.0F; float bleedChance = 0.25F; float zoom = 0.85F;
        SoundEvent shotSound = SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST; float shotPitch = 1.5F;
        SoundEvent shotSound2 = SoundEvents.ENTITY_GENERIC_EXPLODE; float shotPitch2 = 2.0F; float shotVolume = 4.0F;

        Builder(String id, Ammo ammo) { this.id = id; this.ammo = ammo; }
        Builder damage(float d, int pellets) { this.damage = d; this.pellets = pellets; return this; }
        Builder fire(int interval, boolean auto) { this.fireInterval = interval; this.auto = auto; return this; }
        Builder mag(int size, int reload) { this.magSize = size; this.reloadTicks = reload; return this; }
        Builder range(float range, float falloffStart, float minMul) { this.range = range; this.falloffStart = falloffStart; this.minDamageMul = minMul; return this; }
        Builder spread(float hip, float adsMul, float bloom, float maxBloom) { this.hipSpread = hip; this.adsMul = adsMul; this.bloomPerShot = bloom; this.maxBloom = maxBloom; return this; }
        Builder recoil(float pitch, float yaw) { this.recoilPitch = pitch; this.recoilYaw = yaw; return this; }
        Builder head(float mul) { this.headMul = mul; return this; }
        Builder bleed(float chance) { this.bleedChance = chance; return this; }
        Builder zoom(float z) { this.zoom = z; return this; }
        Builder sound(SoundEvent s, float p, SoundEvent s2, float p2, float vol) { this.shotSound = s; this.shotPitch = p; this.shotSound2 = s2; this.shotPitch2 = p2; this.shotVolume = vol; return this; }
        GunStats build() { return new GunStats(this); }
    }

    // ---- Rust-подобные характеристики ----
    public static final GunStats REVOLVER = builder("revolver", Ammo.PISTOL)
            .damage(7.0F, 1).fire(5, false).mag(8, 50).range(60, 20, 0.5F)
            .spread(1.6F, 0.35F, 0.4F, 1.5F).recoil(3.0F, 0.8F).head(2.0F).bleed(0.25F).zoom(0.85F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.3F, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0F, 4.0F).build();
    public static final GunStats SEMI_PISTOL = builder("semi_pistol", Ammo.PISTOL)
            .damage(6.0F, 1).fire(3, false).mag(10, 40).range(60, 20, 0.5F)
            .spread(1.4F, 0.35F, 0.35F, 1.5F).recoil(2.2F, 0.7F).head(2.0F).bleed(0.25F).zoom(0.85F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.7F, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0F, 4.0F).build();
    public static final GunStats THOMPSON = builder("thompson", Ammo.PISTOL)
            .damage(5.5F, 1).fire(3, true).mag(20, 50).range(70, 25, 0.45F)
            .spread(2.0F, 0.4F, 0.25F, 2.5F).recoil(1.3F, 0.7F).head(1.8F).bleed(0.25F).zoom(0.8F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.15F, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.9F, 4.5F).build();
    public static final GunStats MP5 = builder("mp5", Ammo.PISTOL)
            .damage(4.6F, 1).fire(2, true).mag(30, 50).range(70, 25, 0.45F)
            .spread(1.8F, 0.4F, 0.2F, 2.5F).recoil(1.0F, 0.6F).head(1.8F).bleed(0.22F).zoom(0.8F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.55F, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0F, 4.0F).build();
    public static final GunStats AK47 = builder("ak47", Ammo.RIFLE)
            .damage(7.5F, 1).fire(3, true).mag(30, 60).range(110, 45, 0.6F)
            .spread(2.2F, 0.25F, 0.3F, 3.0F).recoil(2.0F, 1.1F).head(2.0F).bleed(0.35F).zoom(0.7F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.25F, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6F, 6.0F).build();
    public static final GunStats BOLT_RIFLE = builder("bolt_rifle", Ammo.RIFLE)
            .damage(16.0F, 1).fire(30, false).mag(4, 70).range(160, 80, 0.75F)
            .spread(3.0F, 0.05F, 0.0F, 0.0F).recoil(5.0F, 1.0F).head(2.0F).bleed(0.5F).zoom(0.3F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.75F, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.3F, 8.0F).build();
    public static final GunStats PUMP_SHOTGUN = builder("pump_shotgun", Ammo.SHOTGUN)
            .damage(2.2F, 8).fire(20, false).mag(6, 70).range(35, 6, 0.15F)
            .spread(6.5F, 0.75F, 0.0F, 0.0F).recoil(6.0F, 1.5F).head(1.5F).bleed(0.15F).zoom(0.9F)
            .sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.45F, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8F, 6.0F).build();
    public static final GunStats ROCKET_LAUNCHER = builder("rocket_launcher", Ammo.ROCKET)
            .damage(0.0F, 1).fire(20, false).mag(1, 100).range(200, 200, 1.0F)
            .spread(0.6F, 0.5F, 0.0F, 0.0F).recoil(6.0F, 1.0F).head(1.0F).bleed(0.0F).zoom(0.75F)
            .sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5F, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.8F, 5.0F).build();
}
