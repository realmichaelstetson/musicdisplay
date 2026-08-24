package com.haloclient.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class HaloRenderPipelines {
    
    public static final VertexFormatElement CUSTOM_DATA = registerCustomElement(0, VertexFormatElement.Type.FLOAT, false, 4);
    public static final VertexFormatElement COLOR2 = registerCustomElement(0, VertexFormatElement.Type.UBYTE, true, 4);
    public static final VertexFormatElement SHADOW_PROPS = registerCustomElement(0, VertexFormatElement.Type.FLOAT, false, 4);

    private static VertexFormatElement registerCustomElement(int index, VertexFormatElement.Type type, boolean normalized, int count) {
        // Allocate IDs starting from 31 downwards to avoid conflicting with vanilla (0-6)
        // and other mods (such as Iris shaders which register elements starting from 7).
        for (int id = 31; id >= 0; id--) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(id, index, type, normalized, count);
            }
        }
        throw new IllegalStateException("No available VertexFormatElement ID remaining!");
    }
    
    public static final VertexFormat ROUNDED_RECT_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("CustomData", CUSTOM_DATA)
            .add("Color2", COLOR2)
            .add("ShadowProps", SHADOW_PROPS)
            .build();

    public static final RenderPipeline ROUNDED_RECT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_rect"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline ROUNDED_RECT_OUTLINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_rect_outline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect_outline"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline ROUNDED_BLUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline ROUNDED_BLUR_OUTLINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/rounded_blur_outline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_blur_outline"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline LIQUID_GLASS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/liquid_glass"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/liquid_glass"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/liquid_glass"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline FONT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/font"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/font"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/font"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline MSDF_FONT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/msdf_font"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/msdf_font"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/msdf_font"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );


    public static final RenderPipeline TEXTURE_BLUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/texture_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/texture_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/texture_blur"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline IMAGE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/image"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/image"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/image"))
                    .withSampler("Sampler0")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static final RenderPipeline SPINNER = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/spinner"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/rounded_rect"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/spinner"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexFormat(ROUNDED_RECT_FORMAT, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static void init() {
    }
}
