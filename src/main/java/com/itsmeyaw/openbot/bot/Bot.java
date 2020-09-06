package com.itsmeyaw.openbot.bot;

import com.itsmeyaw.openbot.bot.util.channelAnnotator.AllChannelType;
import com.itsmeyaw.openbot.bot.util.Command;
import com.itsmeyaw.openbot.bot.util.Dictionary;
import com.itsmeyaw.openbot.bot.util.DictionaryEntry;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.gateway.GatewayClient;
import lombok.NonNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
        }
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