package cn.qihuang02.graaljs.command;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.core.ScriptType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 注册 {@code /graaljs} 游戏内命令。
 * <ul>
 *   <li>{@code /graaljs reload [startup|server|client]} — 重载指定类型的脚本上下文</li>
 * </ul>
 */
public final class GraaljsCommand {

    private GraaljsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graaljs")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (ScriptType type : ScriptType.values()) {
                                                builder.suggest(type.name().toLowerCase(Locale.ROOT));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(GraaljsCommand::reloadType))
                                .executes(GraaljsCommand::reloadAll))
        );
    }

    private static int reloadType(CommandContext<CommandSourceStack> context) {
        String typeName = StringArgumentType.getString(context, "type");
        ScriptType scriptType;
        try {
            scriptType = ScriptType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Unknown script type: " + typeName));
            return 0;
        }

        try {
            Graaljs.getFactory().reload(scriptType);
            context.getSource().sendSuccess(
                    () -> Component.literal("Reloaded " + scriptType.name().toLowerCase(Locale.ROOT) + " scripts"),
                    true);
            return 1;
        } catch (Exception e) {
            Graaljs.LOGGER.error("Failed to reload {} scripts", scriptType.directory, e);
            context.getSource().sendFailure(Component.literal("Failed to reload: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadAll(CommandContext<CommandSourceStack> context) {
        int count = 0;
        for (ScriptType type : ScriptType.values()) {
            try {
                if (Graaljs.getFactory().getContext(type) != null) {
                    Graaljs.getFactory().reload(type);
                    count++;
                }
            } catch (Exception e) {
                Graaljs.LOGGER.error("Failed to reload {} scripts", type.directory, e);
                context.getSource().sendFailure(
                        Component.literal("Failed to reload " + type.name().toLowerCase(Locale.ROOT) + ": " + e.getMessage()));
            }
        }
        int finalCount = count;
        context.getSource().sendSuccess(
                () -> Component.literal("Reloaded " + finalCount + " script context(s)"),
                true);
        return count;
    }
}
