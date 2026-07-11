package cc.kamshi;

import com.google.common.base.Suppliers;
import cc.kamshi.msdf.MsdfFont;
import cc.kamshi.gui.ClickGUI;
import cc.kamshi.gui.SpotifyManager;
import cc.kamshi.gui.SpotifyOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import java.util.function.Supplier;

public final class MinecraftRenderEnhancer implements ModInitializer {

	public static final String MOD_ID = "mre";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Supplier<MsdfFont> BIKO_FONT = Suppliers.memoize(() -> MsdfFont.builder().atlas("biko").data("biko").build());
	
	private static KeyBinding openGuiKey;

	@Override
	public void onInitialize() {
		// Load and start Spotify polling
		SpotifyManager.getInstance().load();
		SpotifyManager.getInstance().startPolling();

		HudRenderCallback.EVENT.register(this::render);

		// Handle overlay interaction inside ChatScreen using Fabric ScreenMouseEvents API
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof net.minecraft.client.gui.screen.ChatScreen) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
					return SpotifyOverlay.onMouseClicked(mouseX, mouseY, button);
				});
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeRender(screen).register((s, context, mouseX, mouseY, tickCounter) -> {
					SpotifyOverlay.onMouseDragged(mouseX, mouseY, 0);
				});
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseX, mouseY, button) -> {
					SpotifyOverlay.onMouseReleased(mouseX, mouseY, button);
					return true;
				});
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
					return SpotifyOverlay.onMouseScrolled(mouseX, mouseY, vertical);
				});
			}
		});

		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.mre.open_gui",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"MusicDisplay"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.wasPressed()) {
				client.setScreen(new ClickGUI());
			}
		});
	}

	private void render(DrawContext context, RenderTickCounter tickCounter) {
		SpotifyOverlay.render(context, tickCounter);
	}

}