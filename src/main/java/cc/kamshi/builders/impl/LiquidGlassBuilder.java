package cc.kamshi.builders.impl;

import cc.kamshi.builders.AbstractBuilder;
import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.renderers.impl.BuiltLiquidGlass;

public final class LiquidGlassBuilder extends AbstractBuilder<BuiltLiquidGlass> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private java.awt.Color color2;
    private float smoothness;
    private float blurRadius;
    private float gradientAngle;
    private float bloom;
    private float cornerMask;

    public LiquidGlassBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public LiquidGlassBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public LiquidGlassBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public LiquidGlassBuilder color2(java.awt.Color color2) {
        this.color2 = color2;
        return this;
    }

    public LiquidGlassBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public LiquidGlassBuilder blurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
        return this;
    }

    public LiquidGlassBuilder gradientAngle(float gradientAngle) {
        this.gradientAngle = gradientAngle;
        return this;
    }

    public LiquidGlassBuilder bloom(float bloom) {
        this.bloom = bloom;
        return this;
    }

    public LiquidGlassBuilder cornerMask(float cornerMask) {
        this.cornerMask = cornerMask;
        return this;
    }

    @Override
    protected BuiltLiquidGlass _build() {
        return new BuiltLiquidGlass(
            this.size,
            this.radius,
            this.color,
            this.color2,
            this.smoothness,
            this.blurRadius,
            this.gradientAngle,
            this.bloom,
            this.cornerMask
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.color2 = null;
        this.smoothness = 1.0f;
        this.blurRadius = 0.0f;
        this.gradientAngle = 0.0f;
        this.bloom = 0.0f;
        this.cornerMask = 0.0f;
    }

}
