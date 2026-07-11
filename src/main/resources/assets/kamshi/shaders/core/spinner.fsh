#version 150

#moj_import <kamshi:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform float StartAngle;
uniform float SweepAngle;
uniform float Radius;
uniform float Thickness;

out vec4 OutColor;

float sdArc(in vec2 p, in float startAngle, in float sweepAngle, in float r, in float t) {
    float angle = atan(p.y, p.x);
    float relAngle = mod(angle - startAngle, 2.0 * 3.14159265);
    if (relAngle < 0.0) relAngle += 2.0 * 3.14159265;
    
    vec2 startPt = r * vec2(cos(startAngle), sin(startAngle));
    vec2 endPt = r * vec2(cos(startAngle + sweepAngle), sin(startAngle + sweepAngle));
    
    if (relAngle > sweepAngle) {
        return min(length(p - startPt), length(p - endPt)) - t * 0.5;
    }
    
    return abs(length(p) - r) - t * 0.5;
}

void main() {
    // FragCoord maps from 0 to 1 across the quad
    vec2 p = (FragCoord - 0.5) * Size;
    
    float dist = sdArc(p, StartAngle, SweepAngle, Radius, Thickness);
    float aa = fwidth(dist);
    
    float alpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, dist);
    
    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);
    
    if (finalColor.a <= 0.001) {
        discard;
    }
    
    OutColor = finalColor;
}
