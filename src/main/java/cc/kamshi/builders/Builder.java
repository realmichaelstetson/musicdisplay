package cc.kamshi.builders;

import cc.kamshi.builders.impl.BlurBuilder;
import cc.kamshi.builders.impl.BorderBuilder;
import cc.kamshi.builders.impl.RectangleBuilder;
import cc.kamshi.builders.impl.TextureBuilder;
import cc.kamshi.builders.impl.TextBuilder;
import cc.kamshi.builders.impl.LiquidGlassBuilder;
import cc.kamshi.builders.impl.SpinnerBuilder;

public final class Builder {

    private static final RectangleBuilder RECTANGLE_BUILDER = new RectangleBuilder();
    private static final BorderBuilder BORDER_BUILDER = new BorderBuilder();
    private static final TextureBuilder TEXTURE_BUILDER = new TextureBuilder();
    private static final TextBuilder TEXT_BUILDER = new TextBuilder();
    private static final BlurBuilder BLUR_BUILDER = new BlurBuilder();
    private static final LiquidGlassBuilder LIQUID_GLASS_BUILDER = new LiquidGlassBuilder();
    private static final SpinnerBuilder SPINNER_BUILDER = new SpinnerBuilder();

    public static RectangleBuilder rectangle() {
        return RECTANGLE_BUILDER;
    }

    public static BorderBuilder border() {
        return BORDER_BUILDER;
    }

    public static TextureBuilder texture() {
        return TEXTURE_BUILDER;
    }

    public static TextBuilder text() {
        return TEXT_BUILDER;
    }

    public static BlurBuilder blur() {
        return BLUR_BUILDER;
    }

    public static LiquidGlassBuilder liquidGlass() {
        return LIQUID_GLASS_BUILDER;
    }

    public static SpinnerBuilder spinner() {
        return SPINNER_BUILDER;
    }

}