package cc.kamshi.builders.impl;

import cc.kamshi.builders.AbstractBuilder;
import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.renderers.impl.BuiltBlur;

public final class BlurBuilder extends AbstractBuilder<BuiltBlur> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private java.awt.Color color2;
    private float smoothness;
    private float blurRadius;
    private float gradientAngle;
    private float bloom;
    private float cornerMask;

    public BlurBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public BlurBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public BlurBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public BlurBuilder color2(java.awt.Color color2) {
        this.color2 = color2;
        return this;
    }

    public BlurBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public BlurBuilder blurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
        return this;
    }

    public BlurBuilder gradientAngle(float gradientAngle) {
        this.gradientAngle = gradientAngle;
        return this;
    }

    public BlurBuilder bloom(float bloom) {
        this.bloom = bloom;
        return this;
    }

    public BlurBuilder cornerMask(float cornerMask) {
        this.cornerMask = cornerMask;
        return this;
    }

    @Override
    protected BuiltBlur _build() {
        return new BuiltBlur(
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