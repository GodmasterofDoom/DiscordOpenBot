package com.itsmeyaw.openbot.bot.util;

import discord4j.core.object.entity.Message;
import lombok.NonNull;

public interface Command {
    public void execute(@NonNull Message message);
}
