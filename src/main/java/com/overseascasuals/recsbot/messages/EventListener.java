package com.overseascasuals.recsbot.messages;

import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Entity;
import discord4j.core.object.entity.Message;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public interface EventListener<T extends Event, R> {

    Logger LOG = LoggerFactory.getLogger(EventListener.class);

    Class<T> getEventType();
    Mono<R> execute(T event);

    default Mono<R> handleError(Throwable error) {
        LOG.error("[M] Unable to process " + getEventType().getSimpleName(), error);
        return Mono.empty();
    }
}
