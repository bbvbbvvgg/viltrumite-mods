#version 150
// Ported from the Roundabout mod (Hydraheads and contributors), The Hand erasure, Roundabout License v1.2.
// See CREDITS.md. Only change: the Scale uniform (sizes in blocks scale with it; Scale = 1 is the original).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float Scale;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;
out vec3 viewTangent;
out vec3 axisPos;
out vec2 screenTangent;

// Must match ErasureStroke.SHELL_SCALE.
const float SHELL_SCALE = 0.5;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    texCoord0 = UV0;
    viewPos = view.xyz;
    // Glass: the surface normal. Shell: the unnormalised direction from the axis point; interpolated linearly
    // together with the position, position - normal * shell radius stays on the axis between vertices too.
    viewNormal = mat3(ModelViewMat) * Normal;
    viewTangent = mat3(ModelViewMat) * (Color.gba * 2.0 - 1.0);
    axisPos = view.xyz - viewNormal * Color.r * SHELL_SCALE * Scale;
    // The path tangent on screen, in pixels per 0.05 block.
    vec4 ahead = ProjMat * vec4(view.xyz + viewTangent * 0.05, 1.0);
    screenTangent = (ahead.xy / max(ahead.w, 1e-3) - gl_Position.xy / max(gl_Position.w, 1e-3)) * ScreenSize * 0.5;
}
