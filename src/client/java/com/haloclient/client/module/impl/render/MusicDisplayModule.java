package com.haloclient.client.module.impl.render;

import com.haloclient.client.gui.click.MusicDisplayOverlay;
import com.haloclient.client.gui.click.DetachedNextElement;
import com.haloclient.client.module.Category;
import com.haloclient.client.module.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MusicDisplayModule extends Module {

    public MusicDisplayModule() {
        super("MusicDisplay", "Shows music display overlay", Category.RENDER);
        setEnabled(true);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        MusicDisplayOverlay.render(graphics);
        DetachedNextElement.render(graphics);
    }
}
