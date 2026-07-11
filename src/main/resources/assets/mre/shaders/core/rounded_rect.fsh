#version 330

in vec4 vertexColor;
in vec4 vertexColor2;
in vec2 texCoord;
in vec2 rectSize;
in float radius;
in float borderThickness;
in vec4 shadowProps;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float shadowBlur = shadowProps.x;
    vec2 expandedSize = rectSize + vec2(shadowBlur * 2.0);
    vec2 p = (texCoord - 0.5) * expandedSize;
    vec2 b = rectSize * 0.5;

    float dist = sdRoundedBox(p, b, radius);
    float aa = fwidth(dist);

    // 1. Calculate main shape alpha (with optional border)
    float mainAlpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, dist);
    if (borderThickness > 0.01) {
        float innerDist = dist + borderThickness;
        float borderAlpha = smoothstep(-aa * 0.5, aa * 0.5, dist) - smoothstep(-aa * 0.5, aa * 0.5, innerDist);
        float interiorAlpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, innerDist);
        mainAlpha = interiorAlpha * 0.9 + borderAlpha; 
    }

    // 3. MIX COLORS FOR GRADIENT with Angle support
    float gradientAngle = shadowProps.w;
    float ga = radians(gradientAngle);
    vec2 gDir = vec2(sin(ga), cos(ga));
    float t = dot(texCoord - 0.5, gDir) + 0.5;
    t = smoothstep(0.0, 1.0, clamp(t, 0.0, 1.0));
    
    vec4 col1 = vertexColor;
    vec4 col2 = vertexColor2;
    
    // Premultiply
    col1.rgb *= col1.a;
    col2.rgb *= col2.a;
    
    // Mix in premultiplied space to maintain hue during alpha transition
    vec4 mixed = mix(col1, col2, t);
    
    // Un-premultiply for the output
    vec4 finalColor = mixed;
    if (finalColor.a > 0.001) {
        finalColor.rgb /= finalColor.a;
    }

    // 4. Bloom (Outer Glow)
    float bloomAlpha = 0.0;
    if (shadowBlur > 0.1) {
        float sigma = shadowBlur * 0.5;
        bloomAlpha = exp(-max(dist, 0.0) * max(dist, 0.0) / (sigma * sigma));
    }

    // 5. Combine - Use the maximum alpha to ensure no gaps or weird overlaps
    finalColor.a *= max(mainAlpha, bloomAlpha);

    if (finalColor.a <= 0.001) discard;

    fragColor = finalColor;
}
