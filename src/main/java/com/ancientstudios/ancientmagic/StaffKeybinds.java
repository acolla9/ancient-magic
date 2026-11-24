package com.ancientstudios.ancientmagic;

import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable staff controls and direct numbered ability casts. */
public final class StaffKeybinds {
    private static final String CATEGORY = "key.categories.ancientmagic";
    private static final List<KeyMapping> QUICK = java.util.stream.IntStream.range(0, StaffType.COUNT)
            .mapToObj(i -> new KeyMapping("key.ancientmagic.quick_cast." + (i == 9 ? 0 : i + 1),
                    i == 9 ? GLFW.GLFW_KEY_0 : GLFW.GLFW_KEY_1 + i, CATEGORY)).toList();
    private static KeyMapping openWheel;
    private static KeyMapping confirm;

    private StaffKeybinds() {}

    public static String confirmKeyLabel() { return confirm == null ? "Enter" : confirm.getTranslatedKeyMessage().getString(); }

    @EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            openWheel = new KeyMapping("key.ancientmagic.open_wheel", GLFW.GLFW_KEY_G, CATEGORY);
            confirm = new KeyMapping("key.ancientmagic.confirm_cast", GLFW.GLFW_KEY_ENTER, CATEGORY);
            event.register(openWheel); event.register(confirm);
            QUICK.forEach(event::register);
        }
    }

    @EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private static InputConstants.Key[] savedHotbarKeys;

        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            updateHotbarBindings(mc, player != null && offhandStaff(player) != null);
            if (player == null || mc.screen != null) return;
            if (openWheel != null && openWheel.consumeClick()) {
                AdminStaffItem staff = heldStaff(player);
                if (staff != null) StaffCastClient.openWheel(player, staff.type());
            }
            if (confirm != null && confirm.consumeClick() && StaffCastClient.isWheelOpen()) StaffCastClient.confirmWheel();
        }

        @SubscribeEvent public static void key(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || StaffCastClient.isWheelOpen()) return;
            // Number keys remain free for hotbar selection unless the staff is in the offhand.
            AdminStaffItem staff = offhandStaff(mc.player);
            if (staff == null) return;
            for (int i = 0; i < QUICK.size(); i++) {
                if (QUICK.get(i).matches(event.getKey(), event.getScanCode())) {
                    StaffCastClient.quickCast(staff.type(), i);
                    return;
                }
            }
        }

        private static AdminStaffItem heldStaff(LocalPlayer player) {
            for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem()))
                if (stack.getItem() instanceof AdminStaffItem staff) return staff;
            return null;
        }

        private static AdminStaffItem offhandStaff(LocalPlayer player) {
            return player.getOffhandItem().getItem() instanceof AdminStaffItem staff ? staff : null;
        }

        private static void updateHotbarBindings(Minecraft mc, boolean blockHotbar) {
            var hotbar = mc.options.keyHotbarSlots;
            if (blockHotbar && savedHotbarKeys == null) {
                savedHotbarKeys = new InputConstants.Key[hotbar.length];
                for (int i = 0; i < hotbar.length; i++) {
                    savedHotbarKeys[i] = hotbar[i].getKey();
                    hotbar[i].setKey(InputConstants.UNKNOWN);
                }
                KeyMapping.resetMapping();
            } else if (!blockHotbar && savedHotbarKeys != null) {
                for (int i = 0; i < hotbar.length; i++) hotbar[i].setKey(savedHotbarKeys[i]);
                savedHotbarKeys = null;
                KeyMapping.resetMapping();
            }
        }
    }
}
