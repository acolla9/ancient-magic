#version 150

in vec3 worldDir;

uniform vec3 SkyColor;
uniform vec3 SunDir;
uniform vec4 SkyParams; // x: star brightness, y: rain level, z: sky rotation angle

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

void main() {
    vec3 d = normalize(worldDir);
    float rain = SkyParams.y;

    // base gradient: horizon is lighter than the zenith while it is light out
    float light = clamp(length(SkyColor) * 1.4, 0.0, 1.0);
    vec3 horizon = mix(SkyColor, vec3(0.92, 0.96, 1.0), 0.45 * light);
    vec3 col = mix(horizon, SkyColor, pow(clamp(d.y, 0.0, 1.0), 0.55));
    if (d.y < 0.0) {
        col = mix(horizon, horizon * 0.35, clamp(-d.y * 3.0, 0.0, 1.0));
    }

    // stars, rotating with the sky
    float c = cos(SkyParams.z);
    float s = sin(SkyParams.z);
    vec3 cel = vec3(d.x * c + d.y * s, -d.x * s + d.y * c, d.z);
    vec3 cell = floor(cel * 110.0);
    vec3 f = fract(cel * 110.0) - 0.5;
    float star = step(0.985, hash(cell)) * smoothstep(0.28, 0.0, length(f));
    col += vec3(star) * SkyParams.x * step(0.0, d.y) * (1.0 - rain);

    // sun: disc + warm glow
    float sd = dot(d, SunDir);
    col += vec3(1.0, 0.82, 0.5) * pow(max(sd, 0.0), 48.0) * 0.30 * (1.0 - rain);
    col = mix(col, vec3(1.0, 0.96, 0.78), smoothstep(0.9915, 0.9925, sd) * (1.0 - rain * 0.85));

    // moon: opposite the sun
    float md = dot(d, -SunDir);
    col = mix(col, vec3(0.88, 0.91, 0.97), smoothstep(0.9945, 0.9955, md) * (1.0 - rain * 0.85));

    // overcast
    float grey = dot(col, vec3(0.333));
    col = mix(col, vec3(grey * 0.75), rain * 0.8);

    fragColor = vec4(col, 1.0);
}
