#version 150

#moj_import <kamshi:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;
in vec4 vCustomData;
in vec4 vColor2;
in vec4 vShadowProps;

uniform sampler2D Sampler0;

out vec4 OutColor;

void main() {
    float blurStrength = vShadowProps.x;
    vec2 texSize = textureSize(Sampler0, 0);
    vec2 screenUV = TexCoord;
    
    vec3 average = vec3(0.0);

    if (blurStrength > 0.05) {
        // Prawdziwy, matematyczny, w pełni dwuwymiarowy Gaussian Blur
        // Capped at 30 to prevent GPU meltdown at absurd sizes
        float radius = min(blurStrength, 30.0);
        
        // Obliczamy odchylenie standardowe tak, aby krawędzie dzwonu pokrywały się z promieniem
        float sigma = radius / 2.0; 
        float sigma2 = 2.0 * sigma * sigma;
        
        float totalWeight = 0.0;
        
        // Optymalizacja kroku - dla ogromnych wartości blura nie musimy brać KAŻDEGO piksela
        // Zachowuje fizyczny kształt kręgu bez widocznej struktury
        float stepAmount = max(1.0, radius / 12.0); 

        for (float x = -radius; x <= radius; x += stepAmount) {
            for (float y = -radius; y <= radius; y += stepAmount) {
                // Równanie dystrybucji Gaussa (Dzwon Gaussa)
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

    vec2 size = vCustomData.xy;
    float rectRadius = vCustomData.z;
    float bloom = vShadowProps.y;
    float cornerMask = vShadowProps.w;

    // Ponieważ powiększyliśmy siatkę w Javie o bloom z każdej strony:
    vec2 expandedSize = size + vec2(bloom * 2.0);
    // Przesuń FragCoord (które mapuje od 0.0 do 1.0 na powiększonej siatce) na środek
    vec2 p = (FragCoord - 0.5) * expandedSize;
    // Bounding box samego właściwego prostokąta
    vec2 b = size * 0.5;

    float r = rectRadius;
    if (cornerMask == 1.0 && p.x > 0.0) r = 0.0;
    else if (cornerMask == 2.0 && p.x < 0.0) r = 0.0;
    else if (cornerMask == 3.0 && p.y < 0.0) r = 0.0;
    else if (cornerMask == 4.0 && p.y > 0.0) r = 0.0;

    // SDF dla uciętych rogów
    vec2 q = abs(p) - b + r;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;

    // --- START GRADIENT TINT LOGIC ---
    float gradientAngle = vShadowProps.z;
    float ga = radians(gradientAngle);
    vec2 gDir = vec2(sin(ga), cos(ga));
    float gt = dot(FragCoord - 0.5, gDir) + 0.5;
    gt = smoothstep(0.0, 1.0, clamp(gt, 0.0, 1.0));
    
    vec4 col1 = FragColor;
    vec4 col2 = vColor2;
    
    // Premultiply both for clean tint blending
    col1.rgb *= col1.a;
    col2.rgb *= col2.a;
    
    vec4 mixedTint = mix(col1, col2, gt);
    if (mixedTint.a > 0.001) mixedTint.rgb /= mixedTint.a;
    // --- END GRADIENT TINT LOGIC ---

    // Miksujemy rozmyte tło (average) z podanym w Javie kolorem (mixedTint.rgb) zależnie od Alfy (mixedTint.a).
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
