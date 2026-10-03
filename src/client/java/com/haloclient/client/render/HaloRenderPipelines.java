package com.haloclient.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class HaloRenderPipelines {

    public static final String CUSTOM_DATA = "CustomData";
    public static final String COLOR2 = "Color2";
    public static final String SHADOW_PROPS = "ShadowProps";
    
    public static final VertexFormat ROUNDED_RECT_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(CUSTOM_DATA, GpuFormat.RGBA32_FLOAT)
            .addAttribute(COLOR2, GpuFormat.RGBA8_UNORM)
            .addAttribute(SHADOW_PROPS, GpuFormat.RGBA32_FLOAT)
            .build();

    private static final RenderPipeline.Snippet BASE_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withVertexBinding(0, ROUNDED_RECT_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .buildSnippet();

    private static final RenderPipeline.Snippet SAMPLED_SNIPPET = RenderPipeline.builder(BASE_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .buildSnippet();

    public static final RenderPipeline ROUNDED_RECT = RenderPipelines.register(
            RenderPipeline.builder(BASE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_rect"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .build()
    );

    public static final RenderPipeline ROUNDED_RECT_OUTLINE = RenderPipelines.register(
            RenderPipeline.builder(BASE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_rect_outline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect_outline"))
                    .build()
    );

    public static final RenderPipeline ROUNDED_BLUR = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .build()
    );

    public static final RenderPipeline ROUNDED_BLUR_OUTLINE = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_blur_outline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur_outline"))
                    .build()
    );

    public static final RenderPipeline LIQUID_GLASS = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/liquid_glass"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/liquid_glass"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/liquid_glass"))
                    .build()
    );

    public static final RenderPipeline FONT = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/font"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/font"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/font"))
                    .build()
    );

    public static final RenderPipeline MSDF_FONT = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/msdf_font"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/msdf_font"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/msdf_font"))
                    .build()
    );


    public static final RenderPipeline TEXTURE_BLUR = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/texture_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/texture_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/texture_blur"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build()
    );

    public static final RenderPipeline IMAGE = RenderPipelines.register(
            RenderPipeline.builder(SAMPLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/image"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/image"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/image"))
                    .build()
    );

    public static final RenderPipeline SPINNER = RenderPipelines.register(
            RenderPipeline.builder(BASE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/spinner"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/spinner"))
                    .build()
    );

    public static void init() {
    }
}
