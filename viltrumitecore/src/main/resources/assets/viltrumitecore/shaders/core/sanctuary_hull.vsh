#version 150
// Sanctuary II hull: baked vertex colours (own lighting, no texture), see SanctuaryRenderer.

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out float underside;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    // model-space height: the belly takes the light of the guns and the fires below
    underside = clamp(-Position.y / 22.0, 0.0, 1.0);
}
