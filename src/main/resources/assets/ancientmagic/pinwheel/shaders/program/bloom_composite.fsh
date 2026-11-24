// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/bloom_composite.fsh   (NEW file)
// Bloom stage 4: add the blurred bright layer back on top of the scene (veil:post), with a soft shoulder so
// blown-out cores roll off towards white instead of clipping. BloomSampler = ancientmagic:bloom_c (see bloom_composite.json).
uniform sampler2D DiffuseSampler0;   // scene
uniform sampler2D BloomSampler;      // blurred bright pass
uniform float BloomStrength;         // 0..~2, uploaded by BloomFx.java

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 col = base.rgb + bloom * BloomStrength * 1.6;
    vec3 hi = max(col - 0.9, vec3(0.0));
    col = min(col, vec3(0.9)) + 0.1 * (hi / (hi + 0.1));
    fragColor = vec4(col, base.a);
}
