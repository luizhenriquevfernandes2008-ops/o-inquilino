#version 330

#moj_import <minecraft:globals.glsl>

// Filtro de fita VHS: lente curva, sangramento de cor, aberração cromática,
// linhas de varredura, faixa de tracking rolando, rasgos horizontais e chiado.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VhsConfig {
    float Intensity;
    float Glitch;
};

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    return mix(hash(vec2(i, 0.0)), hash(vec2(i + 1.0, 0.0)), smoothstep(0.0, 1.0, f));
}

vec3 sampleTape(vec2 uv, float ca) {
    vec3 c;
    c.r = texture(InSampler, uv + vec2(ca, 0.0)).r;
    c.g = texture(InSampler, uv).g;
    c.b = texture(InSampler, uv - vec2(ca, 0.0)).b;
    return c;
}

void main() {
    // tempo em segundos (relógio do mundo)
    float t = GameTime * 1200.0;
    vec2 uv = texCoord;

    // lente levemente curva (câmera barata)
    vec2 cc = uv - 0.5;
    float r2 = dot(cc, cc);
    uv = 0.5 + cc * (1.0 + 0.07 * r2 * Intensity);

    // tremido horizontal por linha (fita gasta)
    float line = floor(uv.y * InSize.y / 2.0);
    float wobble = (hash(vec2(line, floor(t * 24.0))) - 0.5) * 0.0012 * Intensity;
    wobble += sin(uv.y * 9.0 + t * 1.7) * 0.0006 * Intensity;

    // faixa de tracking que desce pela tela
    float bandPos = 1.0 - fract(t * 0.045 + noise1(t * 0.25) * 0.15);
    float bandDist = abs(uv.y - bandPos);
    float band = 1.0 - smoothstep(0.0, 0.035 + Glitch * 0.03, bandDist);
    wobble += band * (hash(vec2(line, floor(t * 60.0))) - 0.5) * (0.012 + 0.03 * Glitch);

    // rasgos: de vez em quando a imagem desliza para o lado
    float tick = floor(t * 7.0);
    float tearOn = step(0.975 - Glitch * 0.35, hash(vec2(tick, 3.17)));
    float tearY = hash(vec2(tick, 7.71));
    float tearH = 0.015 + hash(vec2(tick, 1.3)) * (0.03 + Glitch * 0.08);
    float tear = tearOn * step(abs(uv.y - tearY), tearH) * (hash(vec2(tick, 9.1)) - 0.5) * (0.05 + Glitch * 0.12);
    uv.x += wobble + tear;

    // aberração cromática + sangramento de cor
    float ca = (0.0012 + 0.0022 * r2 * 4.0 + 0.004 * Glitch) * Intensity;
    vec3 col = sampleTape(uv, ca);
    vec2 px = vec2(1.0 / InSize.x, 0.0);
    vec3 bleed = (sampleTape(uv + px * 2.0, ca) + sampleTape(uv - px * 2.0, ca) + sampleTape(uv + px * 4.0, ca)) / 3.0;
    col = mix(col, bleed, 0.32 * Intensity);

    // cores de fita: menos saturadas, pretos lavados, leve tom quente/esverdeado
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, 0.68);
    col *= vec3(1.03, 1.01, 0.92);
    col = pow(max(col, 0.0), vec3(1.1)) * 0.95 + vec3(0.028, 0.03, 0.035);

    // linhas de varredura
    float scan = 0.5 + 0.5 * sin(uv.y * InSize.y * 3.14159);
    col *= mix(1.0, 0.84 + 0.16 * scan, Intensity);

    // chiado
    float grain = hash(uv * InSize + vec2(fract(t * 17.0) * 311.0, fract(t * 23.0) * 173.0)) - 0.5;
    col += grain * (0.05 + 0.14 * Glitch) * Intensity;

    // neve na faixa de tracking
    float snow = step(0.6, hash(uv * InSize * 0.5 + floor(t * 50.0)));
    col = mix(col, vec3(snow * 0.85), band * (0.18 + 0.4 * Glitch) * Intensity);

    // vinheta da lente
    col *= 1.0 - smoothstep(0.3, 0.8, sqrt(r2)) * 0.6 * Intensity;

    // bordas pretas depois da curvatura
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        col = vec3(0.0);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
