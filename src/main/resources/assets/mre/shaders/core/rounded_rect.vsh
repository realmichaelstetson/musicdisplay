#version 330

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
in vec4 CustomData; // [RectSize.x, RectSize.y, Radius, BorderThickness]
in vec4 Color2;
in vec4 ShadowProps; // [ShadowBlur, ShadowOffsetX, ShadowOffsetY, _]

out vec4 vertexColor;
out vec4 vertexColor2;
out vec2 texCoord;
out vec2 rectSize;
out float radius;
out float borderThickness;
out vec4 shadowProps;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    vertexColor2 = Color2;
    texCoord = UV0;
    rectSize = CustomData.xy;
    radius = CustomData.z;
    borderThickness = CustomData.w;
    shadowProps = ShadowProps;
}
