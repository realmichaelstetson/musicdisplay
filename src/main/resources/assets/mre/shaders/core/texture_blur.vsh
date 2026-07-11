#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec4 CustomData;
in vec4 Color2;
in vec4 ShadowProps;

out vec2 TexCoord;
out vec4 FragColor;
out vec4 vCustomData;
out vec4 vColor2;
out vec4 vShadowProps;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
    TexCoord = UV0;
    FragColor = Color * ColorModulator;
    vCustomData = CustomData;
    vColor2 = Color2 * ColorModulator;
    vShadowProps = ShadowProps;
}
