package com.mrleonardos.codeeconomy.internal.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codecore.api.command.SenderPosition;

final class TestCommandContext implements CommandContext {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<Sent> replies = new ArrayList<>();
    private final CommandSender console = new ConsoleSender();

    TestCommandContext set(String name, Object value) {
        values.put(name, value);
        return this;
    }

    void put(String name, Object value) {
        values.put(name, value);
    }

    @Override
    public CommandSender caller() {
        return console;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        if (!values.containsKey(name)) {
            throw new IllegalArgumentException("Command has no argument named " + name);
        }
        return (T) values.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String name, T fallback) {
        return values.containsKey(name) ? (T) values.get(name) : fallback;
    }

    @Override
    public boolean has(String name) {
        return values.containsKey(name);
    }

    @Override
    public void reply(String translationKey, Object... arguments) {
        replies.add(new Sent(translationKey, arguments, false));
    }

    @Override
    public void replyError(String translationKey, Object... arguments) {
        replies.add(new Sent(translationKey, arguments, true));
    }

    Sent last() {
        return replies.get(replies.size() - 1);
    }

    List<Sent> sent() {
        return replies;
    }

    /** Отправитель без игрока: команды из тестов приходят от консоли, как из настоящего терминала. */
    private final class ConsoleSender implements CommandSender {

        @Override
        public SenderKind kind() {
            return SenderKind.CONSOLE;
        }

        @Override
        public Optional<PlayerRef> player() {
            return Optional.empty();
        }

        @Override
        public String name() {
            return "console";
        }

        @Override
        public Optional<SenderPosition> position() {
            return Optional.empty();
        }

        @Override
        public void reply(String translationKey, Object... arguments) {
            TestCommandContext.this.reply(translationKey, arguments);
        }

        @Override
        public void replyError(String translationKey, Object... arguments) {
            TestCommandContext.this.replyError(translationKey, arguments);
        }
    }

    static final class Sent {

        final String key;
        final List<Object> arguments;
        final boolean error;

        private Sent(String key, Object[] arguments, boolean error) {
            this.key = key;
            this.arguments = Arrays.asList(arguments);
            this.error = error;
        }

        boolean is(String translationKey) {
            return key.equals(translationKey);
        }
    }
}
