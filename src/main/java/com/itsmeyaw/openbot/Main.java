package com.itsmeyaw.openbot;

import com.itsmeyaw.openbot.bot.Bot;
import org.springframework.boot.SpringApplication;
import com.itsmeyaw.openbot.web.Website;

public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Website.class, args);
        Bot.getBotInstance();
    }
}
