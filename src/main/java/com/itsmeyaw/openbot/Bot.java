package com.itsmeyaw.openbot;

import com.itsmeyaw.openbot.util.messageAnnotator.AllChannelType;
import com.itsmeyaw.openbot.util.Command;
import com.itsmeyaw.openbot.util.Dictionary;
import com.itsmeyaw.openbot.util.DictionaryEntry;
import com.itsmeyaw.openbot.util.messageAnnotator.GuildTextType;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.gateway.GatewayClient;
import lombok.NonNull;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
public class Bot {
    private static volatile Bot botInstance;
    private final GatewayDiscordClient gateway;

    private final String defaultPrefix = "!";

    private final Map<Long, String> guildsPrefix = new ConcurrentHashMap<>();

    @Dictionary
    private final Map<String, Command> dictionary = new HashMap<>();

    @DictionaryEntry(key = "ping")
    Command ping =
            new Command() {
                @Override
                public Message execute(@NonNull @AllChannelType Message message) {
                    message.getChannel()
                            .flatMap(messageChannel -> messageChannel.createMessage("\uD83C\uDFD3 Pong!\nMy latency is " + Objects.requireNonNullElse(messageChannel.getClient().getGatewayClient(0).map(GatewayClient::getResponseTime).get(), Duration.ZERO).toMillis() + " ms"))
                            .subscribe();
                    return message;
                }

                @Override
                public String getDescription() {
                    return "Get the latency of Bot.";
                }
            };

    @DictionaryEntry(key = "mute-all")
    Command muteALl =
            new Command() {
                @Override
                public Message execute(@NonNull @GuildTextType Message message) {
                    if (message.getAuthorAsMember().flatMap(Member::getVoiceState).flatMap(VoiceState::getChannel).block() != null) {
                        Snowflake authorChannel = message.getAuthorAsMember().flatMap(Member::getVoiceState)
                                .flatMap(VoiceState::getChannel)
                                .map(voiceChannel -> voiceChannel.getId())
                                .block();

                        message.getGuild().block().getMembers()
                                .filter(member -> member.getVoiceState().map(VoiceState::getChannel).block() != null && member.getVoiceState().flatMap(VoiceState::getChannel).map(VoiceChannel::getId).block().equals(authorChannel))
                                .flatMap(member -> member.edit(guildMemberEditSpec -> guildMemberEditSpec.setMute(true)))
                                .onErrorContinue((Throwable t, Object o) -> {
                                })
                                .subscribe();

                        message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("I have just muted all people in your channel!")).subscribe();
                    } else {
                        message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("This command only can be used if you on a voice channel!")).subscribe();
                    }

                    return message;
                }

                @Override
                public String getDescription() {
                    return "Mute all people in a voice channel you are joining.";
                }
            };

    @DictionaryEntry(key = "unmute-all")
    Command unmuteALl =
            new Command() {
                @Override
                public Message execute(@NonNull Message message) {
                    if (message.getAuthorAsMember().flatMap(Member::getVoiceState).flatMap(VoiceState::getChannel).block() != null) {
                        Snowflake authorChannel = message.getAuthorAsMember().flatMap(Member::getVoiceState)
                                .flatMap(VoiceState::getChannel)
                                .map(voiceChannel -> voiceChannel.getId())
                                .block();

                        message.getGuild().block().getMembers()
                                .filter(member -> member.getVoiceState().map(VoiceState::getChannel).block() != null && member.getVoiceState().flatMap(VoiceState::getChannel).map(VoiceChannel::getId).block().equals(authorChannel))
                                .flatMap(member -> member.edit(guildMemberEditSpec -> guildMemberEditSpec.setMute(false)))
                                .onErrorContinue((Throwable t, Object o) -> {
                                })
                                .subscribe();

                        message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("I have just unmuted all people in your channel!")).subscribe();
                    } else {
                        message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("This command only can be used if you on a voice channel!")).subscribe();
                    }

                    return message;
                }

                @Override
                public String getDescription() {
                    return "Unmute all people in a voice channel you are joining.";
                }
            };

    @DictionaryEntry(key = "help")
    Command help = new Command() {
        @Override
        public Message execute(@NonNull @AllChannelType Message message) {
            StringBuilder sb = new StringBuilder();

            message.getChannel().flatMap(
                    messageChannel -> {
                        Snowflake guildId = message.getGuildId().orElse(Snowflake.of(-1));
                        dictionary.entrySet().stream().forEach(
                                stringCommandEntry -> sb.append(getPrefix(guildId.asLong())).append(stringCommandEntry.getKey()).append("\n").append(stringCommandEntry.getValue().getDescription()).append("\n\n")
                        );
                        return messageChannel.createMessage(sb.toString());
                    }
            ).subscribe();

            return message;
        }

        @Override
        public String getDescription() {
            return "Show this message.";
        }
    };

    public Bot() {
        if (botInstance != null) {
            throw new IllegalStateException("Singleton is broken!");
        } else {
            this.gateway = DiscordClient.create(System.getenv("DISCORD_API_KEY")).login().block();
            Arrays.stream(Bot.class.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(DictionaryEntry.class))
                    .forEach(field -> {
                        try {
                            dictionary.put(field.getAnnotation(DictionaryEntry.class).key(), (Command) field.get(this));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
            this.start();
        }
    }

    private void start() {
        gateway.getEventDispatcher()
                .on(ReadyEvent.class)
                .subscribe(readyEvent -> {
                    User self = readyEvent.getSelf();
                    System.out.printf("Logged in as %s#%s%n", self.getUsername(), self.getDiscriminator());
                });

        gateway.getEventDispatcher()
                .on(GuildCreateEvent.class)
                .subscribe(guildCreateEvent -> {
                    System.out.println("Joined new Guild: " + guildCreateEvent.getGuild().getName());
                    if (!guildsPrefix.containsKey(guildCreateEvent.getGuild().getId().asLong()))
                        guildsPrefix.put(guildCreateEvent.getGuild().getId().asLong(), defaultPrefix);
                });

        gateway.getEventDispatcher()
                .on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .flatMap(message -> {
                    if (message.getAuthor().map(author -> !author.isBot()).orElse(false)) {
                        if (Objects.requireNonNullElse(message.getChannel().map(messageChannel -> messageChannel.getType() == Channel.Type.GUILD_TEXT).block(), false)) {
                            String prefix = guildsPrefix.get(message.getGuild().block().getId().asLong());
                            if (message.getContent().startsWith(prefix)) {
                                String[] input = message.getContent().replaceFirst(prefix, "").split(" ");
                                if (input.length > 0) {
                                    dictionary.entrySet().stream()
                                            .filter(entry -> entry.getKey().equalsIgnoreCase(input[0]))
                                            .findAny()
                                            .ifPresent(entry -> entry.getValue().execute(message));
                                }
                            }
                        } else if (Objects.requireNonNullElse(message.getChannel().map(messageChannel -> messageChannel.getType() != Channel.Type.GUILD_TEXT).block(), false) &&
                                message.getContent().startsWith(defaultPrefix)) {
                            String[] input = message.getContent().replaceFirst(defaultPrefix, "").split(" ");
                            if (input.length > 0) {
                                dictionary.entrySet().stream()
                                        .filter(entry -> entry.getKey().equalsIgnoreCase(input[0]))
                                        .findAny()
                                        .ifPresent(entry -> entry.getValue().execute(message));
                            }
                        }
                    }
                    return Flux.just(message);
                })
                .subscribe();

        gateway.onDisconnect().block();
    }

    public String getPrefix(long GuildId) {
        String prefix = guildsPrefix.get(GuildId);
        return Objects.requireNonNullElse(prefix, defaultPrefix);
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }
}