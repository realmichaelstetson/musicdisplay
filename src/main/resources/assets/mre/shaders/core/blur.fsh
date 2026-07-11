#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive (0..1)
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size; // rectangle size
uniform vec4 Radius; // radius for each vertex
uniform float Smoothness; // edge smoothness (not used directly in new AA logic, but kept for compatibility)
uniform float BlurRadius;

// New uniforms matching musicdisplay logic
uniform vec4 Color2;
uniform float GradientAngle;
uniform float Bloom;
uniform float CornerMask;

out vec4 OutColor;

void main() {
    float blurStrength = BlurRadius;
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 screenUV = TexCoord;
    
    vec3 average = vec3(0.0);

    if (blurStrength > 0.05) {
        float radius = min(blurStrength, 30.0);
        float sigma = radius / 2.0; 
        float sigma2 = 2.0 * sigma * sigma;
        float totalWeight = 0.0;
        float stepAmount = max(1.0, radius / 12.0); 

        for (float x = -radius; x <= radius; x += stepAmount) {
            for (float y = -radius; y <= radius; y += stepAmount) {
                float weight = exp(-(x * x + y * y) / sigma2);
                vec2 offset = vec2(x, y) / texSize;
                average += texture(Sampler0, screenUV + offset).rgb * weight;
                totalWeight += weight;
            }
        }
        average /= totalWeight;
    } else {
        average = texture(Sampler0, screenUV).rgb;
    }

    vec2 size = Size;
    float bloom = Bloom;
    float cornerMask = CornerMask;

    // Because the mesh/quad is expanded in Java by bloom on each side:
    vec2 expandedSize = size + vec2(bloom * 2.0);
    // Center-offset coordinate system
    vec2 p = (FragCoord - 0.5) * expandedSize;
    // Bounding box of the actual rectangle
    vec2 b = size * 0.5;

    // Determine the corner radius for the current quadrant
    float r = Radius.x;
    if (p.x > 0.0) {
        r = (p.y > 0.0) ? Radius.x : Radius.y;
    } else {
        r = (p.y > 0.0) ? Radius.w : Radius.z;
    }

    // Apply corner mask
    if (cornerMask == 1.0 && p.x > 0.0) r = 0.0;
    else if (cornerMask == 2.0 && p.x < 0.0) r = 0.0;
    else if (cornerMask == 3.0 && p.y < 0.0) r = 0.0;
    else if (cornerMask == 4.0 && p.y > 0.0) r = 0.0;

    // SDF for rounded rect
    vec2 q = abs(p) - b + r;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;

    // --- START GRADIENT TINT LOGIC ---
    float ga = radians(GradientAngle);
    vec2 gDir = vec2(sin(ga), cos(ga));
    float gt = dot(FragCoord - 0.5, gDir) + 0.5;
    gt = smoothstep(0.0, 1.0, clamp(gt, 0.0, 1.0));
    
    vec4 col1 = FragColor;
    vec4 col2 = Color2;
    
    col1.rgb *= col1.a;
    col2.rgb *= col2.a;
    
    vec4 mixedTint = mix(col1, col2, gt);
    if (mixedTint.a > 0.001) mixedTint.rgb /= mixedTint.a;
    // --- END GRADIENT TINT LOGIC ---

    // Mix blurred background with custom tint
    vec3 tintedBlur = mix(average, mixedTint.rgb, mixedTint.a);
    vec4 finalColor = vec4(tintedBlur, 1.0);

    // 1. Calculate main shape alpha (with pixel-perfect Anti-Aliasing)
    float aa = fwidth(dist);
    float mainAlpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, dist);

    // 2. Bloom (Outer Glow)
    float bloomAlpha = 0.0;
    if (bloom > 0.1) {
        float sigma = bloom * 0.5;
        bloomAlpha = exp(-max(dist, 0.0) * max(dist, 0.0) / (sigma * sigma));
    }

    // 3. Combine
    finalColor.a = max(mainAlpha, bloomAlpha);

    if (finalColor.a <= 0.001) discard;

    OutColor = finalColor;
}