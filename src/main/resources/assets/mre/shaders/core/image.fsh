#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;   // Lokalne współrzędne quada (0..1)
in vec2 rectSize;   // Wymiary prostokąta [width, height]
in float radius;    // Promień zaokrąglenia
in vec4 uvBounds;   // [uMin, vMin, uMax, vMax]

out vec4 fragColor;

// Signed Distance Field dla prostokąta z zaokrąglonymi rogami
float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    // 1. Oblicz UV tekstury z zakresu uvBounds (obsługa FILL/FIT crop)
    //    texCoord jest w zakresie 0..1, mapujemy na uMin..uMax / vMin..vMax
    vec2 imageUV = mix(uvBounds.xy, uvBounds.zw, texCoord);

    // 2. Próbkuj teksturę z filtrowaniem LINEAR (ustawionym w samplerze)
    vec4 texColor = texture(Sampler0, imageUV);

    // 3. Aplikuj tint (mnożymy kolor tekstury przez kolor wierzchołka)
    //    vertexColor = (1,1,1,1) oznacza brak tintowania
    vec4 tinted = texColor * vertexColor;

    // 4. SDF zaokrąglonych rogów
    vec2 p = (texCoord - 0.5) * rectSize;
    vec2 b = rectSize * 0.5;
    float dist = sdRoundedBox(p, b, radius);

    // 5. Pixel-perfect anti-aliasing krawędzi
    float aa = fwidth(dist);
    float cornerAlpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, dist);

    // 6. Finalna alfa = alfa tekstury * alfa zaokrągleń
    tinted.a *= cornerAlpha;

    if (tinted.a <= 0.001) discard;

    fragColor = tinted;
}
