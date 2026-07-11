package cc.kamshi.renderers.impl;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import cc.kamshi.msdf.MsdfFont;
import cc.kamshi.providers.ColorProvider;
import cc.kamshi.providers.ResourceProvider;
import cc.kamshi.renderers.IRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public record BuiltText(
        MsdfFont font,
        String text,
    	float size,
        float thickness,
        int color,
		float smoothness,
        float spacing,
		int outlineColor,
		float outlineThickness
    ) implements IRenderer {

	private static final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("msdf_font"), 
		VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
	
	@Override
    public void render(Matrix4f matrix, float x, float y, float z) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();

		RenderSystem.setShaderTexture(0, this.font.getTextureId());
		
		boolean outlineEnabled = (this.outlineThickness > 0.0f);
		ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
		if (shader.getUniform("Range") != null) {
			shader.getUniform("Range").set(this.font.getAtlas().range());
		}
		if (shader.getUniform("Thickness") != null) {
			shader.getUniform("Thickness").set(this.thickness);
		}
		if (shader.getUniform("Smoothness") != null) {
			shader.getUniform("Smoothness").set(this.smoothness);
		}
		if (shader.getUniform("Outline") != null) {
			shader.getUniform("Outline").set(outlineEnabled ? 1 : 0);
		}

		if (outlineEnabled) {
			if (shader.getUniform("OutlineThickness") != null) {
				shader.getUniform("OutlineThickness").set(this.outlineThickness);
			}
			if (shader.getUniform("OutlineColor") != null) {
				float[] outlineComponents = ColorProvider.normalize(this.outlineColor);
				shader.getUniform("OutlineColor").set(outlineComponents[0], outlineComponents[1], 
					outlineComponents[2], outlineComponents[3]);
			}
		}
		
		boolean hasRenderableGlyph = false;
		if (this.text != null) {
			for (int i = 0; i < this.text.length(); ) {
				int cp = this.text.codePointAt(i);
				if (this.font.hasGlyph(cp)) {
					hasRenderableGlyph = true;
					break;
				}
				i += Character.charCount(cp);
			}
		}
		if (!hasRenderableGlyph) {
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
			return;
		}

		BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		this.font.applyGlyphs(matrix, builder, this.text, this.size,
			(this.thickness + this.outlineThickness * 0.5f) * 0.5f * this.size, this.spacing,
				x, y + this.font.getMetrics().baselineHeight() * this.size, z, this.color);
		
		BufferRenderer.drawWithGlobalProgram(builder.end());

		RenderSystem.setShaderTexture(0, 0);

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

}