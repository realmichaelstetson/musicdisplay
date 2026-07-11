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
in vec4 CustomData;
in vec4 Color2;
in vec4 ShadowProps;

out vec2 FragCoord;
out vec2 TexCoord;
out vec4 FragColor;
out vec4 vCustomData;
out vec4 vColor2;
out vec4 vShadowProps;

void main() {
    // Obliczamy finalną pozycję na ekranie we współrzędnych NDC (-1 do 1)
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);

    // FragCoord to lokalne UV prostokata dla zaokrągleń (0..1)
    FragCoord = UV0;
    
    // TexCoord to pozycja na ekranie wyliczona bezpośrednio z pozycji wierzchołka.
    // To GWARANTUJE, że prostokąt patrzy dokładnie na to, co jest pod nim,
    // niezależnie od rozdzielczości okna czy GUI Scale.
    TexCoord = gl_Position.xy * 0.5 + 0.5;
    
    FragColor = Color * ColorModulator;
    vCustomData = CustomData;
    vColor2 = Color2;
    vShadowProps = ShadowProps;
}
