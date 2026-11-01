#version 150
// City aftermath smoke. The vanilla particle program discards every fragment under 10 % alpha, which
// turns a faint (12-45 % alpha) puff into a hard-edged pixel blob. Here only fully transparent texels
// are dropped, so the sprite's soft falloff survives; the atlas is sampled linearly while this runs.

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
