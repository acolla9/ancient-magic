// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/bloom_extract.fsh   (NEW file)
// Bloom stage 1: keep only the bright parts of the scene (half resolution target, 4-tap box so it does not shimmer).
// Reads minecraft:main so it can see depth: sky pixels use LUMINANCE (a blue daytime sky never blooms, a white-hot
// spell core does), everything else uses the brightest colour channel (so saturated red / gold spell colours bloom).
uniform sampler2D DiffuseSampler0;      // scene colour
uniform sampler2D DiffuseDepthSampler;  // scene depth
uniform float BloomThreshold;           // ~0.8

in vec2 texCoord;
out vec4 fragColor;

vec3 bright(vec3 c, bool sky) {
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    float br = sky ? lum - 0.12 : max(c.r, max(c.g, c.b));
    float w = smoothstep(BloomThreshold, BloomThreshold + 0.25, br);
    return c * w;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler0, 0));
    bool sky = texture(DiffuseDepthSampler, texCoord).r >= 0.9999;
    vec3 acc = vec3(0.0);
    acc += bright(texture(DiffuseSampler0, texCoord + texel * vec2(-1.0, -1.0)).rgb, sky);
    acc += bright(texture(DiffuseSampler0, texCoord + texel * vec2( 1.0, -1.0)).rgb, sky);
    acc += bright(texture(DiffuseSampler0, texCoord + texel * vec2(-1.0,  1.0)).rgb, sky);
    acc += bright(texture(DiffuseSampler0, texCoord + texel * vec2( 1.0,  1.0)).rgb, sky);
    fragColor = vec4(acc * 0.25, 1.0);
}
