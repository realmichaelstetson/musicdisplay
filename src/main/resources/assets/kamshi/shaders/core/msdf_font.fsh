#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform float Range; // distance field range of the msdf font texture
uniform float Thickness; // text thickness
uniform float Smoothness; // edge smoothness
uniform bool Outline; // if false, outline computation will be ignored
uniform float OutlineThickness;
uniform vec4 OutlineColor;

out vec4 OutColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 msd = texture(Sampler0, TexCoord).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    // Dynamic, screen-space crisp pixel range computation using derivatives
    vec2 msdfUnit = Range / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(TexCoord);
    float screenPxRange = max(0.5 * dot(msdfUnit, screenTexSize), 1.0);

    float dist = sd - 0.5 + Thickness;
    float alpha = clamp(screenPxRange * dist + 0.5, 0.0, 1.0);

    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (Outline) {
        float outlineDist = dist + OutlineThickness;
        float outlineAlpha = clamp(screenPxRange * outlineDist + 0.5, 0.0, 1.0);
        color = mix(OutlineColor, FragColor, alpha);
        color.a *= outlineAlpha;
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}