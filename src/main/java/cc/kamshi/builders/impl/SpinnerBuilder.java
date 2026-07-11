package cc.kamshi.builders.impl;

import cc.kamshi.builders.AbstractBuilder;
import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.renderers.impl.BuiltSpinner;

public final class SpinnerBuilder extends AbstractBuilder<BuiltSpinner> {

    private SizeState size;
    private QuadColorState color;
    private float startAngle;
    private float sweepAngle;
    private float radius;
    private float thickness;

    public SpinnerBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public SpinnerBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public SpinnerBuilder startAngle(float startAngle) {
        this.startAngle = startAngle;
        return this;
    }

    public SpinnerBuilder sweepAngle(float sweepAngle) {
        this.sweepAngle = sweepAngle;
        return this;
    }

    public SpinnerBuilder radius(float radius) {
        this.radius = radius;
        return this;
    }

    public SpinnerBuilder thickness(float thickness) {
        this.thickness = thickness;
        return this;
    }

    @Override
    protected BuiltSpinner _build() {
        return new BuiltSpinner(
            this.size,
            this.color,
            this.startAngle,
            this.sweepAngle,
            this.radius,
            this.thickness
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.color = QuadColorState.WHITE;
        this.startAngle = 0.0f;
        this.sweepAngle = 3.14159265f;
        this.radius = 10.0f;
        this.thickness = 2.0f;
    }

}
