#version 150
// City aftermath smoke: the vanilla particle vertex stage (see MeteorParticleRenderTypes.SOFT_SMOKE).

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Trimmed puffs inside the fog ramp send the fog distance of the original full quad in the lightmap's high
    // bits (MeteorSmokeParticle / TrimGeometry.packLight); code 0 = plain vertex, computed as vanilla does.
    ivec2 fogBits = (UV2 >> 8) & 0x7F;
    int fogCode = fogBits.x | (fogBits.y << 7);
    vertexDistance = fogCode == 0 ? fog_distance(ModelViewMat, Position, FogShape) : float(fogCode - 1) / 64.0;
    texCoord0 = UV0;
    vertexColor = Color * texelFetch(Sampler2, (UV2 & 0xFF) / 16, 0);
}
