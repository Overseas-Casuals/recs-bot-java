package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.PeakCycle;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.NewsChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

public abstract class MessageListener {
    Logger LOG = LoggerFactory.getLogger(MessageListener.class);

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;

    @Autowired
    Solver solver;

    public Mono<Void> processCommand(Message eventMessage)
    {
        if(eventMessage.getAuthor().map(user -> !user.isBot()).orElse(false) && eventMessage.getContent().startsWith("!setpeak"))
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
        var guildID = eventMessage.getChannel().cast(NewsChannel.class).map(textChannel -> textChannel.getGuildId()).block();
        LOG.info("Parsing !setpeak command "+eventMessage.getContent());
        var hasRole = eventMessage.getAuthor().map(user -> user.asMember(guildID).map(member -> member.getRoleIds().contains(Snowflake.of(c1PeakRole))).block()).orElse(false);
        if(hasRole)
        {
            String[] commandParts = eventMessage.getContent().split(" ");
            if(commandParts.length == 3)
            {
                try{
                    Item item = Item.valueOf(commandParts[1]);
                    boolean valid = false;
                    if(!solver.hasTentativeD2())
                    {
                        return Mono.just(eventMessage)
                                .flatMap(Message::getChannel)
                                .flatMap(channel -> channel.createMessage("Current cycle doesn't need confirmation of any peaks."))
                                .then();
                    }
                    if(commandParts[2].toLowerCase().contains("strong"))
                    {
                        valid = solver.updatePeak(item, PeakCycle.Cycle2Strong);
                    }
                    else if(commandParts[2].toLowerCase().contains("weak"))
                    {
                        valid = solver.updatePeak(item, PeakCycle.Cycle2Weak);
                    }
                    if(valid && solver.allTentativeD2Set())
                    {
                        LOG.info("command is valid, telling the Solver");
                        var recs = solver.redoDay2Recs();

                        return Mono.just(eventMessage)
                                .flatMap(Message::getChannel)
                                .flatMap(channel -> channel.createMessage(OCUtils.generateRecEmbedMessage(recs.get(0), c1PeakRole)).flatMap(Message::publish))
                                .then();
                    }
                }
                catch (IllegalArgumentException ex)
                {
                    e = ex;
                }
            }
        }
        else
        {
            return Mono.just(eventMessage)
                    .flatMap(Message::getChannel)
                    .flatMap(channel -> channel.createMessage("Error: not authorized to set peaks."))
                    .then();
        }

        final String text = "Could not parse !setpeak command "+eventMessage.getContent() + (e==null?"":"\nException: "+e.getMessage());

        return Mono.just(eventMessage)
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(text))
                .then();
    }
}