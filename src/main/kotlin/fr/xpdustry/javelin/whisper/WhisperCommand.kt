package fr.xpdustry.javelin.whisper

import arc.Events
import arc.util.Strings
import cloud.commandframework.annotations.Argument
import cloud.commandframework.annotations.CommandDescription
import cloud.commandframework.annotations.CommandMethod
import cloud.commandframework.annotations.specifier.Greedy
import cloud.commandframework.services.State
import fr.xpdustry.distributor.command.sender.ArcCommandSender
import fr.xpdustry.distributor.message.MessageIntent
import fr.xpdustry.javelin.util.clientMessageFormatter
import fr.xpdustry.javelin.util.format
import fr.xpdustry.javelin.util.servicePipeline
import fr.xpdustry.javelin.util.typeToken
import mindustry.game.EventType.PlayerLeave
import mindustry.gen.Player

@Suppress("UNUSED")
class WhisperCommand {
    private val replies = mutableMapOf<Player, String>()

    init {
        Events.on(PlayerLeave::class.java) { replies.remove(it.player) }
    }

    @CommandMethod("whisper|w <receiver> <message>")
    @CommandDescription("Whisper a message to another player.")
    fun sendMessage(
        sender: ArcCommandSender,
        @Argument("receiver") receiver: String,
        @Argument("message") @Greedy message: String
    ) {
        if (Strings.stripColors(sender.player.name()) == Strings.stripColors(receiver)) {
            sender.sendMessage(clientMessageFormatter.format("You can't message yourself.", MessageIntent.ERROR))
        } else {
            sender.whisper(receiver, message)
        }
    }

    @CommandMethod("reply|r <message>")
    @CommandDescription("Whisper a message to another player.")
    fun sendMessage(
        sender: ArcCommandSender,
        @Argument("message") @Greedy message: String
    ) {
        val receiver = replies[sender.player]
        if (receiver == null) {
            sender.sendMessage(clientMessageFormatter.format("Error, no recent messages.", MessageIntent.ERROR))
        } else {
            sender.whisper(receiver, message)
        }
    }

    private fun ArcCommandSender.whisper(receiver: String, message: String) {
        val context = WhisperContext(player.name(), receiver, message)
        replies[player] = receiver

        servicePipeline
            .pump(context)
            .through(typeToken<WhisperService>())
            .getResult { result, _ ->
                if (result == State.ACCEPTED) {
                    player.sendMessage(WhisperFormatter.instance.format(context))
                } else {
                    sendMessage(clientMessageFormatter.format("The player $receiver is not online.", MessageIntent.ERROR))
                }
            }
    }
}
