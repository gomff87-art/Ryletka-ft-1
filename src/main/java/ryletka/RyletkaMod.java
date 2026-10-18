package ryletka;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class RyletkaMod implements ClientModInitializer {

    static final String UPGRADE_COMMAND = "upgrade";
    static final int INPUT_SLOT  = 13;
    static final int SPIN_SLOT   = 49;
    static final int RESULT_SLOT = 13;
    static final int GUI_DELAY   = 10;
    static final int SPIN_WAIT   = 100;
    static final int CLICK_DELAY = 4;

    enum State { IDLE, OPEN_CHEST, TAKE_BOOTS, CLOSE_CHEST, OPEN_UPGRADE, PUT_BOOTS, SPIN, WAIT_SPIN, TAKE_RESULT }

    static State state = State.IDLE;
    static int timer = 0;
    static BlockPos chestPos = null;

    @Override
    public void onInitializeClient() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String c = command.trim();
            if (!c.equals("ryletka") && !c.startsWith("ryletka ")) return true;
            MinecraftClient mc = MinecraftClient.getInstance();
            String arg = c.length() > 8 ? c.substring(8).trim() : "";
            switch (arg) {
                case "chest" -> {
                    if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                        chestPos = hit.getBlockPos();
                        msg("Сундук сохранён: " + chestPos.toShortString());
                    } else msg("Посмотри на сундук!");
                }
                case "start" -> {
                    if (chestPos == null) { msg("Сначала /ryletka chest"); break; }
                    state = hasBoots() ? State.OPEN_UPGRADE : State.OPEN_CHEST;
                    timer = 0;
                    msg("Старт");
                }
                case "stop" -> { state = State.IDLE; msg("Стоп"); }
                default -> msg("/ryletka chest | start | stop");
            }
            return false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(RyletkaMod::tick);
    }

    static void tick(MinecraftClient mc) {
        if (state == State.IDLE || mc.player == null || mc.interactionManager == null) return;
        if (timer > 0) { timer--; return; }

        ScreenHandler h = mc.player.currentScreenHandler;
        boolean guiOpen = h instanceof GenericContainerScreenHandler;

        switch (state) {
            case OPEN_CHEST -> {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(chestPos), Direction.UP, chestPos, false));
                state = State.TAKE_BOOTS;
                timer = GUI_DELAY;
            }
            case TAKE_BOOTS -> {
                if (!guiOpen) { state = State.OPEN_CHEST; timer = GUI_DELAY; return; }
                int size = ((GenericContainerScreenHandler) h).getRows() * 9;
                for (int i = 0; i < size; i++) {
                    if (isBoots(h.getSlot(i).getStack())) {
                        click(mc, h, i, SlotActionType.QUICK_MOVE);
                        state = State.CLOSE_CHEST;
                        timer = CLICK_DELAY;
                        return;
                    }
                }
                msg("В сундуке нет ботинок — остановка");
                mc.player.closeHandledScreen();
                state = State.IDLE;
            }
            case CLOSE_CHEST -> {
                mc.player.closeHandledScreen();
                state = State.OPEN_UPGRADE;
                timer = CLICK_DELAY;
            }
            case OPEN_UPGRADE -> {
                mc.player.networkHandler.sendChatCommand(UPGRADE_COMMAND);
                state = State.PUT_BOOTS;
                timer = GUI_DELAY;
            }
            case PUT_BOOTS -> {
                if (!guiOpen) { state = State.OPEN_UPGRADE; timer = GUI_DELAY; return; }
                int size = ((GenericContainerScreenHandler) h).getRows() * 9;
                for (int i = size; i < h.slots.size(); i++) {
                    if (isBoots(h.getSlot(i).getStack())) {
                        click(mc, h, i, SlotActionType.PICKUP);
                        click(mc, h, INPUT_SLOT, SlotActionType.PICKUP);
                        state = State.SPIN;
                        timer = CLICK_DELAY;
                        return;
                    }
                }
                msg("Нет ботинок в инвентаре");
                mc.player.closeHandledScreen();
                state = State.OPEN_CHEST;
                timer = CLICK_DELAY;
            }
            case SPIN -> {
                if (!guiOpen) { state = State.OPEN_UPGRADE; return; }
                click(mc, h, SPIN_SLOT, SlotActionType.PICKUP);
                state = State.WAIT_SPIN;
                timer = SPIN_WAIT;
            }
            case WAIT_SPIN -> {
                state = State.TAKE_RESULT;
                timer = CLICK_DELAY;
            }
            case TAKE_RESULT -> {
                if (guiOpen) {
                    click(mc, h, RESULT_SLOT, SlotActionType.QUICK_MOVE);
                    mc.player.closeHandledScreen();
                }
                state = hasBoots() ? State.OPEN_UPGRADE : State.OPEN_CHEST;
                timer = CLICK_DELAY * 2;
            }
            default -> {}
        }
    }

    static void click(MinecraftClient mc, ScreenHandler h, int slot, SlotActionType type) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, type, mc.player);
    }

    static boolean isBoots(ItemStack s) {
        if (s.isEmpty()) return false;
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.FEET;
    }

    static boolean hasBoots() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (isBoots(inv.getStack(i))) return true;
        return false;
    }

    static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Ryletka] " + s), false);
    }
                        }
