package com.ancientstudios.ancientmagic;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Server-side logic for all twenty abilities (ten per staff).
 * Visuals are played client-side from one {@link SpellFxPayload} per cast; delayed effects (meteor, judgment, starfall...)
 * use a tiny tick scheduler whose timing matches the client animation.
 *
 * Damage is attributed to the caster (custom damage types ancientmagic:hate / holy / hate_erasure / holy_erasure),
 * so kills give loot/XP credit, mobs aggro on the caster and death messages name the staff.
 * Every target goes through {@link #canTarget}, which applies the config (PvP, creative, teams, pets, immune list).
 */
@EventBusSubscriber(modid = AncientMagic.MODID)
public final class StaffSpells {
    private static final int WELL = 0, CHAINS = 1, CURSE = 2, SANCTUARY = 3;

    private static final ResourceKey<DamageType> HATE_DAMAGE = damageKey("hate");
    private static final ResourceKey<DamageType> HOLY_DAMAGE = damageKey("holy");
    private static final ResourceKey<DamageType> HATE_ERASURE = damageKey("hate_erasure");
    private static final ResourceKey<DamageType> HOLY_ERASURE = damageKey("holy_erasure");

    private record Zone(ServerLevel level, UUID owner, Vec3 center, int expiresAt, double radius, double impact, int kind) {}
    private record Task(ServerLevel level, int runAt, Runnable run) {}

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();
    /** Active cast's validated reach; server thread only. */
    private static double activeCastRange;

    private StaffSpells() {}

    private static ResourceKey<DamageType> damageKey(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, path));
    }

    public static void cast(Level level, Player caster, StaffType type, int ability) {
        cast(level, caster, type, ability, 0);
    }

    public static void cast(Level level, Player caster, StaffType type, int ability, int clientRenderDistance) {
        if (!(level instanceof ServerLevel world)) return;
        double previousRange = activeCastRange;
        double serverRange = Math.max(32.0, world.getServer().getPlayerList().getViewDistance() * 16.0);
        double requested = clientRenderDistance > 0 ? Math.max(32, Math.min(1024, clientRenderDistance)) : serverRange;
        double range = world.getServer().isDedicatedServer() ? serverRange : requested;
        int configuredCap = Config.MAX_RANGE.get();
        activeCastRange = configuredCap > 0 ? Math.min(range, configuredCap) : range;
        try {
        if (type == StaffType.HATE) {
            switch (ability) {
                case 0 -> bloodLance(world, caster);
                case 1 -> crimsonNova(world, caster);
                case 2 -> hemorrhageWell(world, caster);
                case 3 -> bloodPact(world, caster);
                case 4 -> rendingChains(world, caster);
                case 5 -> wrathMeteor(world, caster);
                case 6 -> reapersSweep(world, caster);
                case 7 -> thornEruption(world, caster);
                case 8 -> curseOfRuin(world, caster);
                default -> bloodMoon(world, caster);
            }
        } else {
            switch (ability) {
                case 0 -> haloLance(world, caster);
                case 1 -> judgment(world, caster);
                case 2 -> seraphWings(world, caster);
                case 3 -> sanctuary(world, caster);
                case 4 -> divineShield(world, caster);
                case 5 -> radiantBurst(world, caster);
                case 6 -> starfall(world, caster);
                case 7 -> seraphBlades(world, caster);
                case 8 -> resurgence(world, caster);
                default -> heavensGate(world, caster);
            }
        }
        } finally {
            activeCastRange = previousRange;
        }
    }

    // ------------------------------------------------------------------ lifecycle (no stale state between worlds)

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) { clearState(); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) { clearState(); }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ZONES.removeIf(z -> z.level() == level);
            TASKS.removeIf(t -> t.level() == level);
        }
    }

    private static void clearState() {
        ZONES.clear();
        TASKS.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static Vec3 aimPoint(ServerLevel level, Player caster, double range) {
        double capped = Math.min(range, maxSpellRange(level));
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(capped));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getLocation();
    }

    private static double maxSpellRange(ServerLevel world) {
        if (activeCastRange > 0) return activeCastRange;
        double serverRange = Math.max(32.0, world.getServer().getPlayerList().getViewDistance() * 16.0);
        int configured = Config.MAX_RANGE.get();
        return configured > 0 ? Math.min(serverRange, configured) : serverRange;
    }

    private static float impactScale(Vec3 from, Vec3 to) {
        return (float) Math.min(5.0, 1.0 + from.distanceTo(to) / 48.0);
    }

    private static void fx(ServerLevel w, StaffType t, int ability, Vec3 from, Vec3 to) {
        SpellFxPayload.send(w, t.fxId(ability), from, to);
    }

    /** Applies the config rules about who a spell may affect. {@code caster} may be null (owner of a zone went offline). */
    private static boolean canTarget(@Nullable Player caster, LivingEntity e) {
        if (e == caster || !e.isAlive()) return false;
        if (e instanceof Player p) {
            if (p.isSpectator() || !Config.AFFECT_PLAYERS.get()) return false;
            if (p.isCreative() && !Config.AFFECT_CREATIVE.get()) return false;
            if (caster != null && Config.SPARE_TEAMMATES.get() && caster.isAlliedTo(p)) return false;
        }
        if (Config.PROTECT_PETS.get()) {
            if (e instanceof TamableAnimal tamable && tamable.isTame()) return false;
            if (e instanceof AbstractHorse horse && horse.isTamed()) return false;
        }
        List<? extends String> immune = Config.IMMUNE_ENTITIES.get();
        if (!immune.isEmpty()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            for (String s : immune) {
                if (id.equals(s.trim())) return false;
            }
        }
        return true;
    }

    private static List<LivingEntity> near(ServerLevel w, Player caster, Vec3 c, double r) {
        return w.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> canTarget(caster, e) && e.getBoundingBox().getCenter().distanceTo(c) <= r);
    }

    // ---- damage: attributed to the caster through the mod's own damage types ----

    private static DamageSource source(ServerLevel w, @Nullable Player caster, StaffType staff, boolean erasure) {
        boolean trueHit = erasure && Config.TRUE_DAMAGE.get();
        ResourceKey<DamageType> key = staff.isHate() ? (trueHit ? HATE_ERASURE : HATE_DAMAGE) : (trueHit ? HOLY_ERASURE : HOLY_DAMAGE);
        return w.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key)
                .<DamageSource>map(holder -> new DamageSource((Holder<DamageType>) holder, null, caster))
                // Datapack entry missing (should not happen): fall back to attributed vanilla magic damage.
                .orElseGet(() -> caster != null ? w.damageSources().indirectMagic(caster, caster) : w.damageSources().magic());
    }

    private static void deal(ServerLevel w, @Nullable Player caster, StaffType staff, LivingEntity e, float amount, boolean erasure) {
        float scaled = (float) (amount * Config.DAMAGE_MULTIPLIER.get());
        if (scaled <= 0) return;
        e.hurt(source(w, caster, staff, erasure), scaled);
    }

    private static void hate(ServerLevel w, @Nullable Player caster, LivingEntity e, float amount) { deal(w, caster, StaffType.HATE, e, amount, false); }
    private static void holy(ServerLevel w, @Nullable Player caster, LivingEntity e, float amount) { deal(w, caster, StaffType.DETERMINATION, e, amount, false); }

    private static void knock(Entity e, Vec3 from, double horizontal, double vertical) {
        Vec3 out = e.position().subtract(from);
        if (out.lengthSqr() < .01) out = new Vec3(0, 0, 1);
        out = new Vec3(out.x, 0, out.z).normalize().scale(horizontal);
        e.push(out.x, vertical, out.z);
        e.hurtMarked = true;
    }

    private static void effect(LivingEntity e, Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp));
    }

    private static boolean isUndead(LivingEntity e) { return e.getType().is(EntityTypeTags.UNDEAD); }

    private static boolean hostile(LivingEntity e) { return e instanceof Enemy; }

    private static void sound(ServerLevel world, Vec3 at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void later(ServerLevel w, int delay, Runnable r) {
        TASKS.add(new Task(w, w.getServer().getTickCount() + delay, r));
    }

    private static void addZone(Zone zone) {
        int max = Config.MAX_ACTIVE_ZONES.get();
        while (ZONES.size() >= max) ZONES.remove(0);
        ZONES.add(zone);
    }

    /** Spell explosion honouring the blockDamage config. */
    private static void explode(ServerLevel world, Player caster, StaffType staff, int ability, Vec3 c, float radius) {
        Level.ExplosionInteraction mode = switch (Config.BLOCK_DAMAGE.get()) {
            case NONE -> Level.ExplosionInteraction.NONE;
            case MOB_GRIEFING -> Level.ExplosionInteraction.MOB;
            case ALWAYS -> Level.ExplosionInteraction.BLOCK;
        };
        String abilityKey = (staff.isHate() ? "hate:" : "determination:") + (ability + 1);
        if (Config.BLOCK_DAMAGE_DISABLED_ABILITIES.get().stream().anyMatch(abilityKey::equalsIgnoreCase)
                || !caster.createCommandSourceStack().hasPermission(Config.BLOCK_DAMAGE_PERMISSION_LEVEL.get())) {
            mode = Level.ExplosionInteraction.NONE;
        }
        world.explode(caster, c.x, c.y, c.z, radius, mode);
    }

    private static List<LivingEntity> onRay(ServerLevel w, Player caster, Vec3 start, Vec3 end, double width) {
        List<LivingEntity> hits = new ArrayList<>(w.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(width + .5),
                e -> canTarget(caster, e) && distanceToRay(e.getBoundingBox().getCenter(), start, end) < width));
        hits.sort((a, b) -> Double.compare(a.distanceToSqr(caster), b.distanceToSqr(caster)));
        return hits;
    }

    private static double distanceToRay(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 ray = end.subtract(start);
        double t = Math.max(0, Math.min(1, point.subtract(start).dot(ray) / ray.lengthSqr()));
        return point.distanceTo(start.add(ray.scale(t)));
    }

    // ================================================================== STAFF OF HATE

    /** 0: execute-strike on the first target, everything else along the line is cursed. */
    private static void bloodLance(ServerLevel world, Player caster) {
        Vec3 start = caster.getEyePosition().add(caster.getLookAngle().scale(0.6));
        Vec3 end = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 0, start, end);
        List<LivingEntity> hits = onRay(world, caster, start, end, 1.1);
        for (int i = 0; i < hits.size(); i++) {
            LivingEntity e = hits.get(i);
            if (i == 0) deal(world, caster, StaffType.HATE, e, 1000, true);
            else { hate(world, caster, e, 30); effect(e, MobEffects.WITHER, 100, 2); }
        }
        sound(world, caster.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.3F, .75F);
    }

    /** 1: huge crimson detonation at the aim point. */
    private static void crimsonNova(ServerLevel world, Player caster) {
        Vec3 center = aimPoint(world, caster, maxSpellRange(world));
        float scale = impactScale(caster.getEyePosition(), center);
        float blastRadius = 8.0F * scale;
        fx(world, StaffType.HATE, 1, caster.getEyePosition(), center);
        explode(world, caster, StaffType.HATE, 1, center, blastRadius);
        double impactRadius = blastRadius * 1.5;
        float impactDamage = 8 + scale * 8;
        for (LivingEntity target : near(world, caster, center, impactRadius)) {
            hate(world, caster, target, impactDamage);
            effect(target, MobEffects.WITHER, 80, 1);
            knock(target, center, 1.25, .55);
        }
        sound(world, center, SoundEvents.GENERIC_EXPLODE.value(), 1.8F, 1.15F);
    }

    /** 2: a blood whirlpool that drags every entity in. */
    private static void hemorrhageWell(ServerLevel world, Player caster) {
        Vec3 center = aimPoint(world, caster, maxSpellRange(world));
        double distance = caster.getEyePosition().distanceTo(center);
        float scale = impactScale(caster.getEyePosition(), center);
        double radius = Math.min(64.0, 7.0 * scale);
        int duration = 50 + (int) Math.min(50, distance * 0.5);
        fx(world, StaffType.HATE, 2, caster.getEyePosition(), center);
        addZone(new Zone(world, caster.getUUID(), center, world.getServer().getTickCount() + duration, radius, distance, WELL));
        explode(world, caster, StaffType.HATE, 2, center, (float) Math.min(35.0, 7.0 * scale));
        sound(world, center, SoundEvents.ENDERMAN_TELEPORT, 1.4F, .55F);
    }

    /** 3: drain everyone nearby, heal and empower the caster. */
    private static void bloodPact(ServerLevel world, Player caster) {
        Vec3 center = caster.position().add(0, .9, 0);
        fx(world, StaffType.HATE, 3, caster.getEyePosition(), center);
        float blastRadius = 4.5F * impactScale(caster.getEyePosition(), center);
        explode(world, caster, StaffType.HATE, 3, center, blastRadius);
        float drained = 0;
        for (LivingEntity target : near(world, caster, center, blastRadius * 1.5)) {
            float dmg = 12 + blastRadius;
            hate(world, caster, target, dmg);
            drained += dmg * .25F;
            knock(target, caster.position(), 1.4, .7);
        }
        caster.heal(12 + Math.min(20, drained));
        effect(caster, MobEffects.ABSORPTION, 20 * 12, 1);
        effect(caster, MobEffects.MOVEMENT_SPEED, 20 * 8, 1);
        effect(caster, MobEffects.DAMAGE_BOOST, 20 * 8, 0);
        sound(world, center, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.4F);
    }

    /** 4: chains of blood bind everything in a circle. */
    private static void rendingChains(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 4, caster.getEyePosition(), c);
        for (LivingEntity e : near(world, caster, c, 7)) {
            hate(world, caster, e, 6);
            effect(e, MobEffects.MOVEMENT_SLOWDOWN, 100, 6);
            effect(e, MobEffects.WEAKNESS, 100, 1);
        }
        addZone(new Zone(world, caster.getUUID(), c, world.getServer().getTickCount() + 60, 7, 0, CHAINS));
        sound(world, c, SoundEvents.WITHER_BREAK_BLOCK, 1.2F, .6F);
    }

    /** 5: a meteor of burning hatred lands after 1.2 seconds. */
    private static void wrathMeteor(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 5, caster.getEyePosition(), c);
        sound(world, c, SoundEvents.WITHER_SHOOT, 1.6F, .5F);
        later(world, 24, () -> {
            explode(world, caster, StaffType.HATE, 5, c, 9.0F);
            for (LivingEntity e : near(world, caster, c, 13)) {
                float falloff = (float) (1.0 - e.position().distanceTo(c) / 16.0);
                hate(world, caster, e, 18 + 30 * Math.max(0, falloff));
                e.setRemainingFireTicks(160);
                knock(e, c, 1.6, .8);
            }
            sound(world, c, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, .7F);
        });
    }

    /** 6: crescent scythe slash in front of the caster. */
    private static void reapersSweep(ServerLevel world, Player caster) {
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        Vec3 end = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 6, eye, end);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        for (LivingEntity e : onRay(world, caster, eye, end, 2.4)) {
            Vec3 to = e.position().subtract(caster.position());
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            if (toFlat.lengthSqr() > 1e-4 && toFlat.normalize().dot(flat) < Math.cos(Math.toRadians(75))) continue;
            hate(world, caster, e, 30);
            effect(e, MobEffects.WITHER, 60, 1);
            knock(e, caster.position(), 1.9, .45);
        }
        sound(world, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, .5F);
        sound(world, caster.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, .6F);
    }

    /** 7: three waves of thorns burst from the ground. */
    private static void thornEruption(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 7, caster.getEyePosition(), c);
        double[] radii = {5, 8, 11};
        for (int w = 0; w < 3; w++) {
            final double r = radii[w];
            final double inner = w == 0 ? 0 : radii[w - 1] - 1;
            later(world, w * 8, () -> {
                for (LivingEntity e : near(world, caster, c, r)) {
                    if (e.position().distanceTo(c) < inner) continue;
                    hate(world, caster, e, 14);
                    effect(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                    e.push(0, .9, 0);
                    e.hurtMarked = true;
                }
                sound(world, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4F, .6F);
            });
        }
    }

    /** 8: lingering curse field. */
    private static void curseOfRuin(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.HATE, 8, caster.getEyePosition(), c);
        addZone(new Zone(world, caster.getUUID(), c, world.getServer().getTickCount() + 130, 8, 0, CURSE));
        sound(world, c, SoundEvents.ELDER_GUARDIAN_CURSE, 1.2F, .7F);
    }

    /** 9: the blood moon rises; two shockwave pulses and a power-up. */
    private static void bloodMoon(ServerLevel world, Player caster) {
        Vec3 c = caster.position().add(0, .9, 0);
        fx(world, StaffType.HATE, 9, caster.getEyePosition(), c);
        effect(caster, MobEffects.DAMAGE_BOOST, 20 * 12, 1);
        effect(caster, MobEffects.DAMAGE_RESISTANCE, 20 * 12, 0);
        sound(world, c, SoundEvents.ENDER_DRAGON_GROWL, 1.6F, .6F);
        for (int pulse = 0; pulse < 2; pulse++) {
            final float power = pulse == 0 ? 1f : .6f;
            later(world, 14 + pulse * 14, () -> {
                for (LivingEntity e : near(world, caster, c, 16)) {
                    float falloff = (float) (1.0 - e.position().distanceTo(c) / 18.0);
                    hate(world, caster, e, (8 + 18 * Math.max(0, falloff)) * power);
                    effect(e, MobEffects.WITHER, 60, 1);
                    effect(e, MobEffects.DARKNESS, 100, 0);
                    knock(e, c, 1.8 * power, .6);
                }
                sound(world, c, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, .6F);
            });
        }
    }

    // ================================================================== STAFF OF DETERMINATION

    /** 0: beam of light that pierces everything. */
    private static void haloLance(ServerLevel world, Player caster) {
        Vec3 start = caster.getEyePosition().add(caster.getLookAngle().scale(0.6));
        Vec3 end = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 0, start, end);
        for (LivingEntity e : onRay(world, caster, start, end, 1.2)) {
            holy(world, caster, e, isUndead(e) ? 90 : 45);
            effect(e, MobEffects.GLOWING, 200, 0);
        }
        sound(world, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.6F, 1.5F);
        sound(world, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .8F, 1.6F);
    }

    /** 1: a pillar of holy light slams down after 0.7s (true damage, see config trueDamage). */
    private static void judgment(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 1, caster.getEyePosition(), c);
        sound(world, c, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.8F);
        later(world, 14, () -> {
            for (LivingEntity e : near(world, caster, c, 6)) {
                deal(world, caster, StaffType.DETERMINATION, e, isUndead(e) ? 120 : 60, true);
                e.setRemainingFireTicks(80);
                e.push(0, .8, 0);
                e.hurtMarked = true;
            }
            sound(world, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8F, 1.3F);
        });
    }

    /** 2: dash forward on wings of light, striking everything on the way. */
    private static void seraphWings(ServerLevel world, Player caster) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 2, start, end);
        for (LivingEntity e : onRay(world, caster, start, end, 1.8)) {
            holy(world, caster, e, 18);
            knock(e, caster.position(), 1.2, .5);
        }
        caster.setDeltaMovement(look.scale(1.35).add(0, .3, 0));
        caster.hurtMarked = true;
        caster.fallDistance = 0;
        effect(caster, MobEffects.MOVEMENT_SPEED, 60, 1);
        effect(caster, MobEffects.SLOW_FALLING, 200, 0);
        sound(world, caster.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.5F);
    }

    /** 3: hallowed dome that heals allies and repels the wicked. */
    private static void sanctuary(ServerLevel world, Player caster) {
        Vec3 c = caster.position().add(0, .9, 0);
        fx(world, StaffType.DETERMINATION, 3, caster.getEyePosition(), c);
        addZone(new Zone(world, caster.getUUID(), c, world.getServer().getTickCount() + 160, 9, 0, SANCTUARY));
        sound(world, c, SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.2F);
    }

    /** 4: feathered shield: absorption, resistance and a repelling pulse. */
    private static void divineShield(ServerLevel world, Player caster) {
        Vec3 c = caster.position().add(0, .9, 0);
        fx(world, StaffType.DETERMINATION, 4, caster.getEyePosition(), c);
        effect(caster, MobEffects.ABSORPTION, 20 * 12, 4);
        effect(caster, MobEffects.DAMAGE_RESISTANCE, 20 * 10, 1);
        effect(caster, MobEffects.FIRE_RESISTANCE, 20 * 10, 0);
        caster.heal(6);
        for (LivingEntity e : near(world, caster, c, 6)) {
            holy(world, caster, e, 6);
            knock(e, caster.position(), 1.6, .4);
        }
        sound(world, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.0F);
        sound(world, c, SoundEvents.TOTEM_USE, .6F, 1.6F);
    }

    /** 5: blinding burst of light. */
    private static void radiantBurst(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 5, caster.getEyePosition(), c);
        for (LivingEntity e : near(world, caster, c, 12)) {
            holy(world, caster, e, isUndead(e) ? 40 : 20);
            effect(e, MobEffects.BLINDNESS, 100, 0);
            effect(e, MobEffects.GLOWING, 200, 0);
            knock(e, c, 1.3, .5);
        }
        sound(world, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F, 1.8F);
        sound(world, c, SoundEvents.BEACON_DEACTIVATE, 1.4F, 1.6F);
    }

    /** 6: twelve stars rain down in a spiral pattern. */
    private static void starfall(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 6, caster.getEyePosition(), c);
        int n = 12;
        for (int i = 0; i < n; i++) {
            final Vec3 sp = StaffType.starPos(c, i, n, 9.0);
            later(world, StaffType.starStrikeTick(i), () -> {
                for (LivingEntity e : near(world, caster, sp, 2.8)) {
                    holy(world, caster, e, isUndead(e) ? 28 : 14);
                    e.push(0, .4, 0);
                    e.hurtMarked = true;
                }
                sound(world, sp, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F + (float) Math.random() * .6F);
            });
        }
    }

    /** 7: six blades orbit, then converge on the aim point. */
    private static void seraphBlades(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 7, caster.getEyePosition(), c);
        sound(world, caster.position(), SoundEvents.EVOKER_CAST_SPELL, 1.4F, 1.6F);
        later(world, 20, () -> {
            for (LivingEntity e : near(world, caster, c, 4.5)) {
                holy(world, caster, e, isUndead(e) ? 72 : 36);
                effect(e, MobEffects.GLOWING, 160, 0);
                knock(e, c, 1.0, .5);
            }
            sound(world, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, 1.7F);
        });
    }

    /** 8: rise again: heal, cleanse, regenerate. */
    private static void resurgence(ServerLevel world, Player caster) {
        Vec3 c = caster.position().add(0, .9, 0);
        fx(world, StaffType.DETERMINATION, 8, caster.getEyePosition(), c);
        caster.heal(20);
        for (MobEffectInstance inst : new ArrayList<>(caster.getActiveEffects())) {
            if (!inst.getEffect().value().isBeneficial()) caster.removeEffect(inst.getEffect());
        }
        effect(caster, MobEffects.REGENERATION, 100, 2);
        effect(caster, MobEffects.DAMAGE_RESISTANCE, 100, 0);
        caster.getFoodData().eat(6, .8F);
        for (LivingEntity e : near(world, caster, c, 4)) {
            holy(world, caster, e, 8);
            knock(e, caster.position(), 1.5, .5);
        }
        sound(world, c, SoundEvents.TOTEM_USE, 1.2F, 1.4F);
        sound(world, c, SoundEvents.PLAYER_LEVELUP, 1.2F, 1.2F);
    }

    /** 9: the gate opens, then a colossal column of light falls (the core is true damage, see config trueDamage). */
    private static void heavensGate(ServerLevel world, Player caster) {
        Vec3 c = aimPoint(world, caster, maxSpellRange(world));
        fx(world, StaffType.DETERMINATION, 9, caster.getEyePosition(), c);
        sound(world, c, SoundEvents.BEACON_ACTIVATE, 1.8F, .8F);
        later(world, 20, () -> {
            for (LivingEntity e : near(world, caster, c, 14)) {
                double dist = e.position().distanceTo(c);
                boolean core = dist < 7;
                float dmg = core ? 80 : 25;
                deal(world, caster, StaffType.DETERMINATION, e, isUndead(e) ? dmg * 1.5F : dmg, core);
                effect(e, MobEffects.GLOWING, 200, 0);
                knock(e, c, 1.6, core ? 1.1 : .5);
            }
            effect(caster, MobEffects.REGENERATION, 100, 0);
            sound(world, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.2F);
            sound(world, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.6F);
        });
    }

    // ================================================================== ticking: zones + delayed tasks

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();

        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(t -> { if (t.runAt() <= tick) { due.add(t); return true; } return false; });
            for (Task t : due) {
                try {
                    t.run().run();
                } catch (RuntimeException ex) {
                    // One broken delayed spell must never stop the others (or the server tick).
                    AncientMagic.LOGGER.error("Delayed Ancient Magic spell failed", ex);
                }
            }
        }

        if (ZONES.isEmpty()) return;
        Iterator<Zone> iterator = ZONES.iterator();
        while (iterator.hasNext()) {
            Zone z = iterator.next();
            if (tick >= z.expiresAt()) { iterator.remove(); continue; }
            Vec3 center = z.center();
            Player owner = z.level().getPlayerByUUID(z.owner()); // null if the caster went offline
            switch (z.kind()) {
                case WELL -> tickWell(z, owner, center, tick);
                case CHAINS -> {
                    for (LivingEntity e : z.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(z.radius()),
                            e -> canTarget(owner, e) && !e.getUUID().equals(z.owner()))) {
                        Vec3 mo = e.getDeltaMovement();
                        e.setDeltaMovement(0, Math.min(mo.y, 0), 0);
                        e.hurtMarked = true;
                        if (tick % 20 == 0) hate(z.level(), owner, e, 3);
                    }
                }
                case CURSE -> {
                    if (tick % 10 != 0) break;
                    for (LivingEntity e : z.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(z.radius()),
                            e -> canTarget(owner, e) && !e.getUUID().equals(z.owner()))) {
                        hate(z.level(), owner, e, 2);
                        effect(e, MobEffects.WITHER, 40, 1);
                        effect(e, MobEffects.WEAKNESS, 40, 1);
                        effect(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                    }
                }
                case SANCTUARY -> {
                    if (tick % 10 != 0) break;
                    for (LivingEntity e : z.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(z.radius()), LivingEntity::isAlive)) {
                        if (e instanceof Player) {
                            effect(e, MobEffects.REGENERATION, 40, 1);
                            e.heal(1);
                        } else if ((hostile(e) || isUndead(e)) && canTarget(owner, e)) {
                            knock(e, center, .5, .1);
                            effect(e, MobEffects.WEAKNESS, 40, 0);
                            if (isUndead(e)) holy(z.level(), owner, e, 3);
                        }
                    }
                }
                default -> {}
            }
        }
    }

    private static void tickWell(Zone field, @Nullable Player owner, Vec3 center, int tick) {
        AABB bounds = new AABB(center, center).inflate(field.radius());
        for (Entity target : field.level().getEntitiesOfClass(Entity.class, bounds, entity -> pullable(field, owner, entity))) {
            Vec3 delta = center.subtract(target.getBoundingBox().getCenter());
            double distance = delta.length();
            if (distance < .15 || distance > field.radius()) continue;
            double falloff = 1.0 - distance / field.radius();
            double strength = .12 + .34 * falloff + field.impact() * .002;
            Vec3 pull = delta.scale(strength / distance);
            target.setDeltaMovement(target.getDeltaMovement().add(pull.x, Math.max(.025, pull.y), pull.z));
            target.hurtMarked = true;
            if (target instanceof LivingEntity living && tick % 10 == 0) {
                hate(field.level(), owner, living, (float) Math.min(4.0, 1.0 + field.impact() * .01));
            }
        }
    }

    /** The well pulls valid targets plus loose items and XP orbs; never the caster, pets, spared players or random entities. */
    private static boolean pullable(Zone field, @Nullable Player owner, Entity entity) {
        if (entity.isRemoved() || entity.getUUID().equals(field.owner())) return false;
        if (entity instanceof LivingEntity living) return canTarget(owner, living);
        return entity instanceof ItemEntity || entity instanceof ExperienceOrb;
    }
}
