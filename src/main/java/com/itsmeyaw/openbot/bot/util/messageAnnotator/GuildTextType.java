package com.itsmeyaw.openbot.bot.util.messageAnnotator;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.*;
import java.util.Objects;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
@Constraint(validatedBy = GuildTextType.GuildTextTypeValidator.class)
public @interface GuildTextType {
    String message() default "Channel type is not a guild text channel!";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class GuildTextTypeValidator implements ConstraintValidator<GuildTextType, Message> {
        @Override
        public boolean isValid(Message message, ConstraintValidatorContext constraintValidatorContext) {
            return Objects.requireNonNull(message.getChannel().block()).getType() == Channel.Type.GUILD_TEXT;
        }
    }
}
