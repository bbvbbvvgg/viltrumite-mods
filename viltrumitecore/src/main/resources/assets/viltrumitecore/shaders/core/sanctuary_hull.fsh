#version 150
// Sanctuary II hull: daylight (ColorModulator), aerial haze toward the fog colour (Haze.a) and the
// glow of its own guns and the burning city on the belly (Flash.a).

uniform vec4 ColorModulator;
uniform vec4 Haze;
uniform vec4 Flash;

in vec4 vertexColor;
in float underside;

out vec4 fragColor;

void main() {
    vec3 c = vertexColor.rgb * ColorModulator.rgb;
    c += Flash.rgb * (Flash.a * underside) * vertexColor.rgb * 2.0;
    c = mix(c, Haze.rgb, clamp(Haze.a, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
}
