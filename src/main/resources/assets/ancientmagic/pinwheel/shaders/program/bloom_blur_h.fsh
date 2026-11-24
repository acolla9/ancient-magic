// FILE: src/main/resources/assets/ancientmagic/pinwheel/shaders/program/bloom_blur_h.fsh   (NEW file)
// Bloom stage 2/3: separable 9-tap gaussian (horizontal). Runs on the downsampled bloom buffers.
uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler0, 0));
    vec2 step1 = vec2(texel.x, 0.0) * 1.35;
    vec3 c = texture(DiffuseSampler0, texCoord).rgb * 0.227027;
    c += (texture(DiffuseSampler0, texCoord + step1 * 1.0).rgb + texture(DiffuseSampler0, texCoord - step1 * 1.0).rgb) * 0.1945946;
    c += (texture(DiffuseSampler0, texCoord + step1 * 2.0).rgb + texture(DiffuseSampler0, texCoord - step1 * 2.0).rgb) * 0.1216216;
    c += (texture(DiffuseSampler0, texCoord + step1 * 3.0).rgb + texture(DiffuseSampler0, texCoord - step1 * 3.0).rgb) * 0.0540540;
    c += (texture(DiffuseSampler0, texCoord + step1 * 4.0).rgb + texture(DiffuseSampler0, texCoord - step1 * 4.0).rgb) * 0.0162162;
    fragColor = vec4(c, 1.0);
}
