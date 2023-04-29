package net.xolt.sbutils.features;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.xolt.sbutils.config.ModConfig;
import net.xolt.sbutils.util.Messenger;
import net.xolt.sbutils.util.RegexFilters;

import static net.xolt.sbutils.SbUtils.MC;

public class AutoRaffle {

    private static boolean enabled;
    private static boolean waitingToBuy;
    private static boolean shouldSendErrorMessage;
    private static long checkedForGrassAt;

    public static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> autoRaffleNode = dispatcher.register(ClientCommandManager.literal("autoraffle")
                .executes(context -> {
                    ModConfig.INSTANCE.getConfig().autoRaffle = !ModConfig.INSTANCE.getConfig().autoRaffle;
                    ModConfig.INSTANCE.save();
                    Messenger.printChangedSetting("text.sbutils.config.category.autoraffle", ModConfig.INSTANCE.getConfig().autoRaffle);
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.literal("tickets")
                        .executes(context -> {
                            Messenger.printSetting("text.sbutils.config.option.raffleTickets", ModConfig.INSTANCE.getConfig().raffleTickets);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer())
                                .executes(context -> {
                                    ModConfig.INSTANCE.getConfig().raffleTickets = IntegerArgumentType.getInteger(context, "amount");
                                    ModConfig.INSTANCE.save();
                                    Messenger.printChangedSetting("text.sbutils.config.option.raffleTickets", ModConfig.INSTANCE.getConfig().raffleTickets);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(ClientCommandManager.literal("checkDelay")
                        .executes(context -> {
                            Messenger.printSetting("text.sbutils.config.option.grassCheckDelay", ModConfig.INSTANCE.getConfig().grassCheckDelay);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommandManager.argument("seconds", DoubleArgumentType.doubleArg())
                                .executes(context -> {
                                    ModConfig.INSTANCE.getConfig().grassCheckDelay = DoubleArgumentType.getDouble(context, "seconds");
                                    ModConfig.INSTANCE.save();
                                    Messenger.printChangedSetting("text.sbutils.config.option.grassCheckDelay", ModConfig.INSTANCE.getConfig().grassCheckDelay);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        dispatcher.register(ClientCommandManager.literal("autoraf")
                .executes(context ->
                        dispatcher.execute("autoraffle", context.getSource())
                )
                .redirect(autoRaffleNode));
    }

    public static void tick() {
        if (enabled != ModConfig.INSTANCE.getConfig().autoRaffle) {
            enabled = ModConfig.INSTANCE.getConfig().autoRaffle;
            reset();
        }

        if (!ModConfig.INSTANCE.getConfig().autoRaffle || MC.getNetworkHandler() == null || MC.currentScreen instanceof ProgressScreen) {
            return;
        }

        if (waitingToBuy && System.currentTimeMillis() - checkedForGrassAt > ModConfig.INSTANCE.getConfig().grassCheckDelay * 1000.0) {
            buyTickets();
        }
    }

    public static void processMessage(Text message) {
        if (ModConfig.INSTANCE.getConfig().autoRaffle && RegexFilters.raffleEndFilter.matcher(message.getString()).matches()) {
            reset();
        }
    }

    public static void onJoinGame() {
        if (ModConfig.INSTANCE.getConfig().autoRaffle) {
            reset();
        }
    }

    private static void buyTickets() {
        if (MC.player == null) {
            return;
        }

        int numTickets = Math.min(Math.max(ModConfig.INSTANCE.getConfig().raffleTickets, 1), 2);
        int grassCount = getGrassCount();
        if (grassCount < 1) {
            waitingToBuy = true;
            checkedForGrassAt = System.currentTimeMillis();
            if (shouldSendErrorMessage) {
                Messenger.printMessage("message.sbutils.autoRaffle.notEnoughGrass");
                shouldSendErrorMessage = false;
            }
            return;
        }

        int buyAmount = Math.min(numTickets, grassCount);
        MC.player.sendCommand("raffle buy " + buyAmount);
        waitingToBuy = false;
        Messenger.printWithPlaceholders("message.sbutils.autoRaffle.buying", buyAmount);
    }

    private static int getGrassCount() {
        if (MC.player == null) {
            return -1;
        }

        int counter = 0;
        for (int i = 0; i < MC.player.getInventory().size(); i++) {
            ItemStack itemStack = MC.player.getInventory().getStack(i);
            if (!(itemStack.getItem().equals(Items.GRASS_BLOCK))) {
                continue;
            }

            counter += itemStack.getCount();
        }
        return counter;
    }

    private static void reset() {
        waitingToBuy = true;
        shouldSendErrorMessage = true;
    }
}
