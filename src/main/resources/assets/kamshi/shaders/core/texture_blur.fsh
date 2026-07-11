#version 150

in vec2 TexCoord;
in vec4 FragColor;
in vec4 vCustomData;
in vec4 vColor2;
in vec4 vShadowProps;

uniform sampler2D Sampler0;

out vec4 OutColor;

void main() {
    float blurStrength = vCustomData.x;
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 uv = TexCoord;
    
    // Usunięto early return, aby gradient zawsze mógł zostać obliczony, nawet przy braku rozmycia.
    // Jeśli blurStrength <= 0.05, logika poniżej po prostu pominie pętlę Gaussa.

    // Sprawdzenie sylwetki postaci oparty o modyfikacje z poprzedniego zadania
    float isInside = (texture(Sampler0, uv).a > 0.1) ? 1.0 : 0.0;

    // --- START GAUSSIAN BLUR LOGIC ---
    // Promień rozmycia (blur) i siła blooma
    float blurRadius = min(vCustomData.x, 30.0);
    float bloomStrength = vShadowProps.y; 
    
    vec4 average = vec4(0.0);
    
    if (blurRadius > 0.1) {
        float sigma = blurRadius / 2.0; 
        float sigma2 = 2.0 * sigma * sigma;
        float totalWeight = 0.0;
        float stepAmount = max(1.0, blurRadius / 12.0); 

        for (float x = -blurRadius; x <= blurRadius; x += stepAmount) {
            for (float y = -blurRadius; y <= blurRadius; y += stepAmount) {
                float weight = exp(-(x * x + y * y) / sigma2);
                vec2 offset = vec2(x, y) / texSize;
                average += texture(Sampler0, uv + offset) * weight;
                totalWeight += weight;
            }
        }
        average /= totalWeight;
    } else {
        average = texture(Sampler0, uv);
    }
    // --- END GAUSSIAN BLUR LOGIC ---

    // --- START GRADIENT TINT LOGIC ---
    float gradientAngle = vShadowProps.z;
    float ga = radians(gradientAngle);
    vec2 gDir = vec2(sin(ga), cos(ga));
    
    // TexCoord na pełnym ekranie zachowuje się jak FragCoord dla GUI quadów z pliku rounded_blur.
    float gt = dot(uv - 0.5, gDir) + 0.5;
    gt = smoothstep(0.0, 1.0, clamp(gt, 0.0, 1.0));
    
    vec4 col1 = FragColor;
    vec4 col2 = vColor2;
    
    // Premultiply both for clean tint blending
    col1.rgb *= col1.a;
    col2.rgb *= col2.a;
    
    vec4 mixedTint = mix(col1, col2, gt);
    if (mixedTint.a > 0.001) mixedTint.rgb /= mixedTint.a;
    // --- END GRADIENT TINT LOGIC ---

    // --- FINAL BLEND LOGIC ---
    // Gracz chce widzieć TYLKO solidny model gracza, "cały pokolorowany", "bez outline".
    // Ponieważ źródło (Sampler0) może zawierać tylko krawędzie (outline), stosujemy "Solidification":
    // Wykorzystujemy rozmycie (average.a) do wypełnienia wnętrza modelu.
    
    vec4 t = texture(Sampler0, uv);
    // Sprawdzamy wszystkie kanały, na wypadek gdyby maska była w RGB (częste w Minecraft)
    float baseMask = max(max(t.r, t.g), max(t.b, t.a));
    
    // Wzmocnienie rozmycia, aby zaliczyło "środek" modelu jako pełny kolor
    float fill = max(baseMask, average.a * 2.0);
    
    // Twarde odcięcie (smoothstep z niskim progiem), aby uzyskać solidny kształt bez poświaty (glow)
    float finalAlpha = smoothstep(0.01, 0.05, fill);
    
    vec4 finalColor = vec4(mixedTint.rgb, 1.0);
    finalColor.a = finalAlpha * mixedTint.a;
    
    if (finalColor.a <= 0.01) discard;

    OutColor = finalColor;
}
