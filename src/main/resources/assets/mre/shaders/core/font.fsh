#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    float sampledAlpha = texture(Sampler0, texCoord).r;
    
    // Antialiasing / Sharpening using derivatives
    // This creates a sharp edge that is still smooth at the pixel level
    float edge = 0.5;
    float width = fwidth(sampledAlpha);
    float alpha = smoothstep(edge - width, edge + width, sampledAlpha);
    
    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
    if (fragColor.a < 0.001) discard;
}
