package com.itsmeyaw.openbot.bot.util;

import discord4j.core.object.entity.Message;
import lombok.NonNull;

public interface Command {
    public Message execute(@NonNull Message message);
    public String getDescription();
}
