package com.haloclient.client.render;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.haloclient.client.render.Constants.mc;

public final class MathUtility {

    private MathUtility() {
    }

    public static Number roundAndClamp(final Number value, final Number minValue, final Number maxValue,
                                       final Number increment) {
        switch (value) {
            case Double casted -> {
                casted = Math.round(casted / increment.doubleValue()) * increment.doubleValue();
                casted = Mth.clamp(casted, minValue.doubleValue(), maxValue.doubleValue());
                return casted;
            }
            case Float casted -> {
                casted = Math.round(casted / increment.floatValue()) * increment.floatValue();
                casted = Mth.clamp(casted, minValue.floatValue(), maxValue.floatValue());
                return casted;
            }
            case Long casted -> {
                casted = Math.round((float) casted / increment.longValue()) * increment.longValue();
                casted = Mth.clamp(casted, minValue.longValue(), maxValue.longValue());
                return casted;
            }
            default -> {
                int casted = value.intValue();
                casted = Math.round((float) casted / increment.intValue()) * increment.intValue();
                casted = Mth.clamp(casted, minValue.intValue(), maxValue.intValue());
                return casted;
            }
        }
    }

    public static Vec3 getInterpolatedPosition(final LivingEntity entity, final float tickDelta) {
        return new Vec3(
                Mth.lerp(tickDelta, entity.xOld, entity.getX()),
                Mth.lerp(tickDelta, entity.yOld, entity.getY()),
                Mth.lerp(tickDelta, entity.zOld, entity.getZ()));
    }

    public static Vec3 getInterpolatedPositionRelative(final LivingEntity entity, final float tickDelta) {
        final Camera camera = mc.gameRenderer.mainCamera();
        return getInterpolatedPosition(entity, tickDelta).subtract(camera.position());
    }

    public static Vec3 interpolate(final LivingEntity entity, final float tickDelta) {
        return getInterpolatedPositionRelative(entity, tickDelta);
    }

    public static double interpolate(final double a, final double b, final double v) {
        return (a + (b - a) * v);
    }

    public static float interpolate(final float a, final float b, final float v) {
        return (a + (b - a) * v);
    }

    public static long interpolate(final long a, final long b, final long v) {
        return (a + (b - a) * v);
    }

    public static int interpolate(final int a, final int b, final int v) {
        return (a + (b - a) * v);
    }
}
