package com.itsmeyaw.openbot.bot;

import com.itsmeyaw.openbot.bot.util.channelAnnotator.AllChannelType;
import com.itsmeyaw.openbot.bot.util.Command;
import com.itsmeyaw.openbot.bot.util.Dictionary;
import com.itsmeyaw.openbot.bot.util.DictionaryEntry;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.gateway.GatewayClient;
import lombok.NonNull;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Bot {
    private static volatile Bot botInstance;
    private final GatewayDiscordClient gateway;

    private final String defaultPrefix = "!";

    private final Map<Long, String> guildsPrefix = new ConcurrentHashMap<>();

    @Dictionary
    private final Map<String, Command> dictionary = new HashMap<>();

    @DictionaryEntry(key = "ping")
    Command ping = (@NonNull @AllChannelType Message message) -> {
        message.getChannel()
                .flatMap(messageChannel -> messageChannel.createMessage("U+1F3D3 Pong!\nMy latency is " + getLatency().toMillis() + " ms"))
                .subscribe();
        return message;
    };

    private Duration getLatency() {
        return gateway.getGatewayClient(0).map(GatewayClient::getResponseTime).orElse(Duration.ZERO);
    }

    private Bot() {
        if (botInstance != null) {
            throw new IllegalStateException("Singleton is broken!");
        } else {
            this.gateway = DiscordClient.create(System.getenv("DISCORD_API_KEY")).login().block();
            Arrays.stream(Bot.class.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(DictionaryEntry.class))
                    .forEach(field -> {
                        try {
                            dictionary.put(field.getAnnotation(DictionaryEntry.class).key(), (Command) field.get(null));
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
                    System.out.println(String.format("Logged in %s#%s", self.getUsername(), self.getDiscriminator()));
                });

        gateway.getEventDispatcher()
                .on(GuildCreateEvent.class)
                .subscribe(guildCreateEvent -> guildsPrefix.put(guildCreateEvent.getGuild().getId().asLong(), defaultPrefix));

        gateway.getEventDispatcher()
                .on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .map(message -> {
                    if (message.getAuthor().map(author -> !author.isBot()).orElse(false)) {
                        if (message.getChannel().map(messageChannel -> messageChannel.getType() == Channel.Type.GUILD_TEXT).block()) {
                            String prefix = guildsPrefix.get(message.getGuild().block().getId().asLong());
                            if (message.getContent().startsWith(prefix)) {
                                String[] input = message.getContent().replaceFirst(prefix, "").split(" ");
                                if (input.length > 0) {
                                    dictionary.entrySet().stream()
                                            .filter(entry -> entry.getKey().equalsIgnoreCase(input[0]))
                                            .findAny()
                                            .ifPresentOrElse(entry -> entry.getValue().execute(message),
                                                    () -> message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("Cannot understand command!")).subscribe());
                                }
                            }
                        } else if (message.getChannel().map(messageChannel -> messageChannel.getType() != Channel.Type.GUILD_TEXT).block() &&
                                message.getContent().startsWith(defaultPrefix)) {
                            String[] input = message.getContent().replaceFirst(defaultPrefix, "").split(" ");
                            if (input.length > 0) {
                                dictionary.entrySet().stream()
                                        .filter(entry -> entry.getKey().equalsIgnoreCase(input[0]))
                                        .findAny()
                                        .ifPresentOrElse(entry -> entry.getValue().execute(message),
                                                () -> message.getChannel().flatMap(messageChannel -> messageChannel.createMessage("Cannot understand command!")).subscribe());
                            }
                        }
                    }
                    return message;
                })
                .subscribe();

        gateway.onDisconnect().block();
    }

    public static Bot getBotInstance() {
        if (botInstance == null) {
            synchronized (Bot.class) {
                if (botInstance == null) {
                    botInstance = new Bot();
                }
            }
        }
        return botInstance;
    }

    public String getPrefix(long GuildId) {
        String prefix = guildsPrefix.get(GuildId);
        return Objects.requireNonNullElse(prefix, defaultPrefix);
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }
}