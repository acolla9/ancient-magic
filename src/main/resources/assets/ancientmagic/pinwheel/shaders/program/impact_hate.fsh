// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/impact_hate.fsh   (REPLACES the existing file)
// Hate impact frame: inverted blood-ink, speed lines, shockwave warp, chromatic split, red flash.
// NEW in this version (all gated by ImpactInk, so ImpactInk = 0 gives exactly the old look):
//   - radial zoom blur streaming toward the impact point
//   - Sobel ink outlines (manga line-art)
//   - rotated halftone dots in the mid-tones
//   - a 1-3 frame hard black/white "impact frame" at the very start of the hit (disabled in reduced-motion mode)
// Uniforms are uploaded by ImpactFrames.java.
uniform sampler2D DiffuseSampler0;
uniform float ImpactIntensity;
uniform float ImpactFlash;
uniform float ImpactProgress;
uniform vec2 ImpactCenter;
uniform float ImpactChroma;   // chromatic aberration multiplier (client config x proximity)
uniform float ImpactMotion;   // 1 = full warp / speed lines / inversion, ~0.25 = reduced-motion mode
uniform float ImpactInk;      // 1 = radial blur + outlines + halftone + hard frame, 0 = classic look
uniform vec3 FlashColor;      // colour of the short full-screen flash
uniform float FlashAmount;    // 0..1 opacity of that flash

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise1(float x) {
    float i = floor(x), f = fract(x);
    return mix(hash(vec2(i, 0.0)), hash(vec2(i + 1.0, 0.0)), f * f * (3.0 - 2.0 * f));
}
float lumAt(vec2 p) { return dot(texture(DiffuseSampler0, p).rgb, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec4 original = texture(DiffuseSampler0, texCoord);
    if (ImpactIntensity <= 0.001) { fragColor = original; return; }

    vec2 ts = vec2(textureSize(DiffuseSampler0, 0));
    float aspect = ts.x / ts.y;
    vec2 d = texCoord - ImpactCenter;
    vec2 da = vec2(d.x * aspect, d.y);
    float dist = length(da);
    vec2 dir = dist > 1e-4 ? da / dist : vec2(0.0);
    vec2 dirUv = vec2(dir.x / aspect, dir.y);
    float ang = atan(da.y, da.x);

    // expanding shockwave warps the image
    float ringR = ImpactProgress * 1.7;
    float ring = exp(-pow((dist - ringR) * 8.0, 2.0));
    vec2 uv = texCoord - dirUv * ring * 0.04 * ImpactIntensity * ImpactMotion;

    // chromatic split: strongest on the first frames, grows toward the screen edges
    float ca = (0.004 + 0.014 * dist) * ImpactIntensity * ImpactChroma * (1.0 + 1.2 * ImpactFlash);

    // radial zoom blur: smear the picture along the line toward the impact point (strongest at the edges)
    float blurLen = 0.10 * ImpactIntensity * ImpactMotion * ImpactInk * (0.35 + 0.65 * clamp(ImpactFlash, 0.0, 1.0));
    vec3 scene;
    if (blurLen > 0.0005) {
        vec2 toC = ImpactCenter - uv;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 10; i++) {
            float t = float(i) / 9.0;
            vec2 p = uv + toC * (t * blurLen);
            acc += vec3(texture(DiffuseSampler0, p + dirUv * ca).r,
                        texture(DiffuseSampler0, p).g,
                        texture(DiffuseSampler0, p - dirUv * ca).b);
        }
        scene = acc / 10.0;
    } else {
        scene = vec3(texture(DiffuseSampler0, uv + dirUv * ca).r,
                     texture(DiffuseSampler0, uv).g,
                     texture(DiffuseSampler0, uv - dirUv * ca).b);
    }

    float lum = dot(scene, vec3(0.299, 0.587, 0.114));
    float negative = smoothstep(0.45, 1.0, ImpactIntensity);
    float l = mix(lum, 1.0 - lum, negative * mix(0.4, 0.9, ImpactMotion));
    float bands = floor(l * 3.0 + 0.5) / 3.0;
    vec3 ink = mix(vec3(0.02, 0.0, 0.0), vec3(0.82, 0.02, 0.05), smoothstep(0.15, 0.55, bands));
    ink = mix(ink, vec3(1.0, 0.82, 0.82), smoothstep(0.75, 1.0, bands));

    // ---- manga halftone: 45-degree dot grid in the mid-tones, dots get bigger where the picture is darker
    float cell = max(5.0, ts.y / 110.0);
    vec2 pix = texCoord * ts;
    vec2 g = vec2(0.7071 * pix.x - 0.7071 * pix.y, 0.7071 * pix.x + 0.7071 * pix.y) / cell;
    float dotDist = length(fract(g) - 0.5);
    float rad = sqrt(clamp(1.0 - l, 0.0, 1.0)) * 0.62;
    float dotMask = 1.0 - smoothstep(rad - 0.08, rad + 0.08, dotDist);      // 1 inside a dot
    float midBand = smoothstep(0.10, 0.35, l) * (1.0 - smoothstep(0.62, 0.88, l));
    vec3 dotted = mix(vec3(0.82, 0.03, 0.06), vec3(0.03, 0.0, 0.0), dotMask);
    ink = mix(ink, dotted, midBand * ImpactInk * 0.9);

    // ---- Sobel ink outlines (thicker on the first frames of the hit)
    float edgeMask = 0.0;
    if (ImpactInk > 0.001) {
        vec2 px = (1.0 / ts) * (1.0 + 1.6 * ImpactIntensity);
        float tl = lumAt(uv + px * vec2(-1.0,  1.0)), tc = lumAt(uv + px * vec2(0.0,  1.0)), tr = lumAt(uv + px * vec2(1.0,  1.0));
        float ml = lumAt(uv + px * vec2(-1.0,  0.0)),                                         mr = lumAt(uv + px * vec2(1.0,  0.0));
        float bl = lumAt(uv + px * vec2(-1.0, -1.0)), bc = lumAt(uv + px * vec2(0.0, -1.0)), br = lumAt(uv + px * vec2(1.0, -1.0));
        float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
        float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
        edgeMask = smoothstep(0.18, 0.50, length(vec2(gx, gy))) * ImpactInk;
        vec3 lineCol = mix(vec3(0.0), vec3(1.0, 0.12, 0.12), step(l, 0.28));  // black on light areas, hot red on dark ones
        ink = mix(ink, lineCol, edgeMask * 0.92);
    }

    // blood speed lines, re-rolled every few frames
    float lines = noise1(ang * 40.0 + floor(ImpactProgress * 16.0 * ImpactMotion) * 7.31);
    lines = smoothstep(0.7, 0.95, lines) * smoothstep(0.06, 0.5, dist);
    ink = mix(ink, vec3(1.0, 0.08, 0.1), lines * 0.9 * ImpactMotion);

    ink += vec3(0.95, 0.05, 0.06) * exp(-dist * 4.5) * ImpactFlash;
    ink = mix(ink, vec3(0.22, 0.0, 0.0), smoothstep(0.35, 1.3, dist) * 0.55);

    // ---- hard impact frame: for ~1-3 frames the picture is pure threshold black/white with ink outlines.
    // Skipped in reduced-motion mode (ImpactMotion < 0.5) because a hard flip between frames can be uncomfortable.
    float strobe = step(0.5, ImpactMotion) * ImpactInk * (1.0 - smoothstep(0.035, 0.085, ImpactProgress));
    if (strobe > 0.001) {
        float th = step(0.5, lum);
        vec3 hard = mix(vec3(0.0), vec3(1.0, 0.94, 0.94), th);
        hard = mix(hard, vec3(0.85, 0.0, 0.04), (1.0 - th) * exp(-dist * 2.5) * 0.8);
        hard = mix(hard, vec3(0.0), edgeMask);
        ink = mix(ink, hard, strobe);
    }

    // Concentrate the blood-ink and flash around the impact. The prior full-screen
    // blend washed the entire view red on every Hate spell hit.
    float hitMask = exp(-dist * 4.0);
    float amt = clamp(ImpactIntensity * hitMask, 0.0, 1.0);
    vec3 outCol = mix(scene, ink, amt);
    float localFlash = clamp(FlashAmount * exp(-dist * 5.0), 0.0, 1.0);
    outCol = mix(outCol, FlashColor, localFlash);
    fragColor = vec4(outCol, 1.0);
}
