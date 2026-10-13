package com.haloclient.client.module.impl.render;

import com.haloclient.client.gui.click.ClickGUI;
import com.haloclient.client.module.Category;
import com.haloclient.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class ClickGUIModule extends Module {
    public ClickGUIModule() {
        super("ClickGUI", "Opens the module menu", Category.RENDER);
        setKey(0);
    }

    @Override
    protected void onEnable() {
        mc.gui.setScreen(new ClickGUI());
        setEnabled(false); // Disable immediately after opening
    }
}
