package net.robinfriedli.botify.exceptions.handlers;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private MessageChannel messageChannel;
    private ExecutionContext executionContext;

    public TrackLoadingExceptionHandler(Logger logger) {
        this(logger, null, null);
    }

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel messageChannel, ExecutionContext executionContext) {
        this.logger = logger;
        this.messageChannel = messageChannel;
        this.executionContext = executionContext;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (executionContext == null) {
            executionContext = ExecutionContext.Current.get();
        }

        if (messageChannel == null) {
            messageChannel = executionContext != null ? executionContext.getChannel() : ThreadContext.Current.get(MessageChannel.class);
        }

        ExceptionUtils.handleTrackLoadingException(e, logger, executionContext, messageChannel);
    }

}
