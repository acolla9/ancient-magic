// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/impact_determination.fsh   (REPLACES the existing file)
// Determination impact frame: holy white-out, bloom, god rays, soft halo shockwave, white flash.
// New uniforms (all set from ImpactFrames.java): ImpactChroma, ImpactMotion, FlashColor, FlashAmount.
uniform sampler2D DiffuseSampler0;
uniform float ImpactIntensity;
uniform float ImpactFlash;
uniform float ImpactProgress;
uniform vec2 ImpactCenter;
uniform float ImpactChroma;   // chromatic aberration multiplier (client config x proximity)
uniform float ImpactMotion;   // 1 = full warp / white-out, ~0.25 = reduced-motion mode
uniform vec3 FlashColor;      // colour of the short full-screen flash
uniform float FlashAmount;    // 0..1 opacity of that flash

in vec2 texCoord;
out vec4 fragColor;

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

    float ringR = ImpactProgress * 1.6;
    float ring = exp(-pow((dist - ringR) * 9.0, 2.0));
    vec2 uv = texCoord - dirUv * ring * 0.03 * ImpactIntensity * ImpactMotion;

    float ca = (0.003 + 0.010 * dist) * ImpactIntensity * ImpactChroma * (1.0 + 1.2 * ImpactFlash);
    vec3 scene = vec3(texture(DiffuseSampler0, uv + dirUv * ca).r,
                      texture(DiffuseSampler0, uv).g,
                      texture(DiffuseSampler0, uv - dirUv * ca).b);

    // bloom: gather bright neighbours
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float a = 6.28318 * float(i) / 12.0;
        vec2 o = vec2(cos(a) / aspect, sin(a)) * (0.012 + 0.02 * ImpactIntensity);
        vec3 s = texture(DiffuseSampler0, uv + o).rgb;
        bloom += s * max(dot(s, vec3(0.333)) - 0.45, 0.0);
    }
    bloom /= 12.0;

    // god rays marching toward the impact point
    vec2 toC = ImpactCenter - uv;
    vec3 rays = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float t = float(i) / 20.0;
        vec3 s = texture(DiffuseSampler0, uv + toC * t * 0.6).rgb;
        rays += s * max(dot(s, vec3(0.333)) - 0.55, 0.0) * (1.0 - t);
    }
    rays /= 20.0;

    vec3 col = scene * (1.0 + ImpactFlash * 1.2) + bloom * 2.6 + rays * 3.4 * ImpactIntensity;
    float halo = exp(-pow((dist - ringR * 0.8) * 12.0, 2.0)) * ImpactIntensity;
    col += vec3(1.0, 0.95, 0.8) * (halo * 0.6 + exp(-dist * 3.5) * ImpactFlash * 1.4);
    float g = dot(col, vec3(0.333));
    col = mix(vec3(g), col, 0.75);
    col = mix(col, vec3(1.0, 0.98, 0.9), smoothstep(0.4, 1.3, dist) * 0.25 * ImpactIntensity);
    col = mix(col, vec3(1.0), clamp(ImpactFlash * 0.55 * mix(0.4, 1.0, ImpactMotion), 0.0, 0.75));

    vec3 outCol = mix(scene, col, clamp(ImpactIntensity, 0.0, 1.0));
    outCol = mix(outCol, FlashColor, clamp(FlashAmount, 0.0, 1.0));
    fragColor = vec4(outCol, 1.0);
}
