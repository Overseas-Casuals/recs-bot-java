package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.PeakCycle;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

public abstract class MessageListener {
    Logger LOG = LoggerFactory.getLogger(MessageListener.class);

    @Autowired
    Solver solver;

    public Mono<Void> processCommand(Message eventMessage)
    {
        if(eventMessage.getContent().startsWith("!setpeak"))
            return processSetPeakCommand(eventMessage);

        return Mono.just(eventMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().equalsIgnoreCase("!todo"))
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage("Things to do today:\n - write a bot\n - eat lunch\n - play a game"))
                .then();
    }

    private Mono<Void> processSetPeakCommand(Message eventMessage)
    {
        Exception e = null;
        LOG.info("Parsing !setpeak command "+eventMessage.getContent());
        if(eventMessage.getAuthor().map(user -> !user.isBot()).orElse(false))
        {
            String[] commandParts = eventMessage.getContent().split(" ");
            if(commandParts.length == 3)
            {
                try{
                    Item item = Item.valueOf(commandParts[1]);
                    boolean valid = false;
                    if(commandParts[2].toLowerCase().contains("strong"))
                    {
                        solver.updatePeak(item, PeakCycle.Cycle2Strong);
                        valid = true;
                    }
                    else if(commandParts[2].toLowerCase().contains("weak"))
                    {
                        solver.updatePeak(item, PeakCycle.Cycle2Weak);
                        valid = true;
                    }
                    if(!solver.hasTentativeD2())
                    {
                        LOG.info("command is valid, telling the Solver");
                        var recs = solver.getRecommendationsForToday();
                        return Mono.just(eventMessage)
                                .flatMap(Message::getChannel)
                                .flatMap(channel -> channel.createMessage(recs.get(0).toString()))
                                .then();
                    }

                }
                catch (IllegalArgumentException ex)
                {
                    e = ex;
                }
            }
        }

        final String text = "Could not parse !setpeak command "+eventMessage.getContent() + (e==null?"":"\nException: "+e.getMessage());

        return Mono.just(eventMessage)
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(text))
                .then();
    }
}