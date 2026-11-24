// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/sky_grade.fsh   (REPLACES the existing file)
// Sky / fog grade for Blood Moon (red) and Heaven's Gate (gold). Used by the "ancientmagic:sky_tint" post pipeline.
// Reads the main framebuffer's colour AND depth: depth == 1.0 means "sky", values just below it are far terrain.
//
// NEW: when SkyContent > 0.5 the sky is no longer just recoloured, it is PAINTED:
//   Blood Moon   : a huge cratered blood moon with corona, crimson stars, drifting dark clouds rim-lit by the moon
//   Heaven's Gate: layered aurora curtains, a radiant sun with god rays, gold-lit clouds
// The view direction comes from veil:space_helper (viewDirFromUv), so everything is locked to the WORLD and
// stays put while the camera turns. Uniforms are uploaded every frame by SkyEffects.java.
#include veil:space_helper

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform vec3 SkyTint;      // target hue
uniform float SkyAmount;   // 0..1 overall strength (already includes distance + fade in/out)
uniform float SkyDarken;   // >0 darkens the scene, <0 brightens it
uniform float SkyHaze;     // how strongly far terrain sinks into the tint
uniform float SkyPulse;    // 0..1 slow throb (0 in reduced-motion mode)
uniform float SkyTime;     // seconds (wraps hourly)
uniform float SkyKind;     // 0 = Blood Moon, 1 = Heaven's Gate (blends in between)
uniform vec3 SkyDir;       // world direction of the moon / sun (unit vector)
uniform float SkyContent;  // 1 = paint moon / aurora / clouds, 0 = classic recolour
uniform float SkyMotion;   // 1 = normal drift speed, ~0.25 in reduced-motion mode

in vec2 texCoord;
out vec4 fragColor;

// ------------------------------------------------------------------ noise
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return s;
}

// ------------------------------------------------------------------ shared pieces
// Tangent basis around SkyDir (for painting the moon disc and sun rays).
void sunBasis(out vec3 tx, out vec3 ty) {
    vec3 up = abs(SkyDir.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    tx = normalize(cross(up, SkyDir));
    ty = cross(SkyDir, tx);
}

// Cloud deck projected onto a plane above the viewer. Returns coverage 0..1, `lit` = how much this bit faces the moon/sun.
float clouds(vec3 dir, float t, out float lit) {
    // Bounded spherical-direction mapping. The previous dir.xz / h projection
    // approached a singularity at the horizon (h ~= 0.14), so a tiny camera turn
    // stretched the cloud field across the whole sky and produced jagged bands.
    vec2 p = dir.xz * 5.0 + vec2(t * 0.018, t * 0.007);
    float n = fbm(p);
    vec2 toLight = normalize(SkyDir.xz + vec2(1e-4)) * 0.035;
    float n2 = fbm(p + toLight);
    lit = clamp(0.5 + (n - n2) * 5.0, 0.0, 1.0);
    float aa = max(fwidth(n) * 1.5, 0.012);
    float cover = smoothstep(0.52 - aa, 0.76 + aa, n);
    return cover * smoothstep(-0.015, 0.13, dir.y);
}

// ------------------------------------------------------------------ Blood Moon
vec3 bloodSky(vec3 dir, float t, out float cloudCover) {
    float horizon = exp(-max(dir.y, 0.0) * 3.2);
    vec3 col = SkyTint * (0.10 + 0.55 * horizon);                      // dark crimson dome, glowing horizon

    // crimson stars (twinkle), only high in the sky
    vec2 sp = dir.xz / (abs(dir.y) + 1.1) * 150.0;
    float star = step(0.9965, hash21(floor(sp)));
    star *= 0.55 + 0.45 * sin(t * 2.4 + hash21(floor(sp) + 3.7) * 6.28);
    col += vec3(1.0, 0.35, 0.28) * star * smoothstep(0.1, 0.5, dir.y) * 0.8;

    // the moon
    vec3 tx, ty;
    sunBasis(tx, ty);
    float c = clamp(dot(dir, SkyDir), -1.0, 1.0);
    float ang = acos(c);
    const float R = 0.17;                                              // angular radius, about 10 degrees
    vec3 moon = vec3(0.0);
    if (c > 0.0) {
        vec2 q = vec2(dot(dir, tx), dot(dir, ty)) / R;
        float r = length(q);
        // smoothstep with reversed edges is undefined in GLSL and caused large
        // driver-dependent filled polygons/artifact patches in the painted sky.
        float disc = 1.0 - smoothstep(0.965, 1.0, r);
        vec3 n = vec3(q, sqrt(max(0.0, 1.0 - r * r)));
        float dif = clamp(dot(n, normalize(vec3(-0.45, 0.50, 0.75))), 0.0, 1.0);
        float maria = fbm(q * 2.3 + 3.0);
        float crater = smoothstep(0.55, 0.75, fbm(q * 6.0 + 11.0)) * 0.25;
        vec3 surf = mix(vec3(0.32, 0.015, 0.025), vec3(1.0, 0.30, 0.18), dif * (0.50 + 0.80 * maria) - crater);
        surf *= 0.55 + 0.45 * n.z;                                     // limb darkening
        moon = surf * disc * (1.0 + 0.18 * SkyPulse);
    }
    float outside = max(ang - R, 0.0);
    vec3 corona = vec3(1.0, 0.10, 0.05) * (exp(-outside * 9.0) * 0.95 + exp(-outside * 2.4) * 0.28) * (1.0 + 0.30 * SkyPulse);
    corona *= step(0.0, c);

    float lit;
    cloudCover = clouds(dir, t, lit);
    vec3 cloudCol = mix(vec3(0.045, 0.0, 0.012), vec3(0.80, 0.14, 0.08), lit);
    cloudCol += corona * 0.9 * lit;                                    // clouds scatter the moon glow

    col += (corona + moon) * (1.0 - cloudCover * 0.72);
    col = mix(col, cloudCol, cloudCover * 0.88);
    return col;
}

// ------------------------------------------------------------------ Heaven's Gate
vec3 aurora(vec3 dir, float t) {
    float az = atan(dir.z, dir.x);
    float el = asin(clamp(dir.y, -1.0, 1.0));
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float base = 0.30 + 0.20 * fi + 0.07 * sin(az * (2.0 + fi) + t * (0.30 + 0.12 * fi) + fi * 1.7);
        float d = el - base;
        float prof = smoothstep(-0.03, 0.0, d) * exp(-max(d, 0.0) * (7.0 - 1.5 * fi));   // sharp bottom edge, long fade up
        float streak = vnoise(vec2(az * (22.0 + 6.0 * fi) + t * 0.15, fi * 9.0 + t * 0.07));
        streak = 0.30 + 0.70 * streak * streak;
        float shimmer = 0.65 + 0.35 * sin(t * 1.3 + az * 9.0 + fi);
        vec3 col = mix(vec3(1.0, 0.82, 0.35), vec3(0.45, 1.0, 0.85), smoothstep(0.0, 0.5, d + 0.10 * fi));
        col = mix(col, vec3(1.0, 0.52, 0.82), smoothstep(0.35, 0.80, d * 3.0) * 0.35);
        acc += col * prof * streak * shimmer;
    }
    return acc * smoothstep(0.0, 0.18, dir.y);
}

vec3 gateSky(vec3 dir, float t, out float cloudCover) {
    float horizon = exp(-max(dir.y, 0.0) * 2.2);
    vec3 col = mix(vec3(0.42, 0.34, 0.20), vec3(1.0, 0.86, 0.52), horizon) * 0.80;

    col += aurora(dir, t) * 1.15;

    vec3 tx, ty;
    sunBasis(tx, ty);
    float c = clamp(dot(dir, SkyDir), -1.0, 1.0);
    float ang = acos(c);
    float phi = atan(dot(dir, ty), dot(dir, tx));
    float rays = vnoise(vec2(cos(phi), sin(phi)) * 6.0 + vec2(t * 0.10, -t * 0.07));
    rays = pow(rays, 2.2) * 1.8 + 0.12;
    float core = exp(-ang * 15.0) * 1.5 + (1.0 - smoothstep(0.035, 0.045, ang)) * 2.0;
    float halo = exp(-ang * 3.4) * 0.32;
    float shafts = rays * exp(-ang * 1.7) * 0.55 * step(0.0, c);
    col += vec3(1.0, 0.92, 0.70) * (core + halo) * step(0.0, c) + vec3(1.0, 0.85, 0.55) * shafts * (1.0 + 0.2 * SkyPulse);

    float lit;
    cloudCover = clouds(dir, t, lit);
    vec3 cloudCol = mix(vec3(0.72, 0.52, 0.30), vec3(1.30, 1.04, 0.70), lit);
    cloudCol += vec3(1.0, 0.85, 0.55) * halo * lit;
    col = mix(col, cloudCol, cloudCover * 0.80);
    return col;
}

// ------------------------------------------------------------------ main
void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float a = clamp(SkyAmount, 0.0, 1.0);
    if (a <= 0.001) { fragColor = base; return; }

    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float sky = step(0.9999, depth);                                  // sky dome, stars, sun, moon, clouds behind terrain
    float far = smoothstep(0.9970, 0.9999, depth) * (1.0 - sky);     // distant terrain
    float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
    vec3 col = base.rgb;

    // 1) the sky
    if (sky > 0.5 && SkyContent > 0.5) {
        // Do not use Veil's viewDirFromUv here: it reconstructs at depth 1.0,
        // the far-plane limit. Inverse projection can produce w=0 there, which
        // yields Inf/NaN directions and the characteristic missing/nodraw sky.
        // Reconstruct at a finite depth, then normalize to get the same camera ray.
        vec3 dir = normalize(screenToLocalSpace(texCoord, 0.5).xyz);
        float t = SkyTime * SkyMotion;
        float k = clamp(SkyKind, 0.0, 1.0);
        vec3 blood = vec3(0.0), gold = vec3(0.0);
        float cb = 0.0, cg = 0.0;
        if (k < 0.99) blood = bloodSky(dir, t, cb);
        if (k > 0.01) gold = gateSky(dir, t, cg);
        vec3 painted = mix(blood, gold, k);
        painted += SkyTint * lum * 0.20;                              // keep a whisper of the vanilla sky (sun, clouds)
        painted = painted / (1.0 + painted * 0.30);                   // gentle tone-map so the moon / sun do not clip
        col = mix(col, painted, a * 0.96);
    } else {
        // classic: keep the luminance (so clouds, stars and the moon still read) but swap the hue
        float lowSky = 0.85 + 0.30 * (1.0 - texCoord.y);
        vec3 skyCol = SkyTint * (0.30 + 1.15 * lum) * lowSky + SkyTint * SkyPulse * 0.12;
        col = mix(col, skyCol, sky * a * 0.92);
    }

    // 2) far terrain sinks into the mist
    vec3 hazeCol = SkyTint * 0.55 + vec3(lum) * 0.15;
    col = mix(col, hazeCol, far * SkyHaze * a);

    // Keep the effect on the sky and distant terrain. A full-frame tint here made
    // ordinary attacks turn the entire screen crimson/gold, including nearby blocks.
    col *= 1.0 - SkyDarken * a * (sky + far * 0.35);

    fragColor = vec4(col, base.a);
}
