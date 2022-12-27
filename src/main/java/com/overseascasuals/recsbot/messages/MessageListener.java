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

import java.util.Date;

public abstract class MessageListener {
    Logger LOG = LoggerFactory.getLogger(MessageListener.class);

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;

    @Value("${mienna}")
    private String miennaID;

    @Autowired
    Solver solver;

    public Mono<Void> processCommand(Message eventMessage)
    {
        if(eventMessage.getAuthor().map(user -> !user.isBot()).orElse(false) && eventMessage.getContent().startsWith("!setpeak"))
            return processSetPeakCommand(eventMessage);

        if(eventMessage.getAuthor().map(user -> !user.isBot()).orElse(false) && eventMessage.getContent().equalsIgnoreCase("!nextweek"))
            return processNextWeekCommand(eventMessage);

        return Mono.empty();
        /*return Mono.just(eventMessage)
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().equalsIgnoreCase("!todo"))
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage("Things to do today:\n - write a bot\n - eat lunch\n - play a game"))
                .then();*/
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
                    boolean strong = false;
                    if(solver.isSolvedD2())//Might be uninitialized
                    {
                        LOG.info("Has no D2 info. Maaaaaaybe we needed to reboot the server and now it lost it.");
                        var d1 = new Date(1661241600000l);
                        var d2 = new Date();

                        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
                        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
                        if(day==0)
                        {
                            solver.getDailyRecommendations(week, 0, true);
                        }
                    }

                    if(solver.isSolvedD2()) //Really just doesn't have anything
                    {
                        return Mono.just(eventMessage)
                                .flatMap(Message::getChannel)
                                .flatMap(channel -> channel.createMessage("Current cycle doesn't need confirmation of any peaks."))
                                .then();
                    }
                    if(commandParts[2].toLowerCase().contains("strong"))
                    {
                        valid = solver.updatePeak(item, PeakCycle.Cycle2Strong);
                        strong = true;
                    }
                    else if(commandParts[2].toLowerCase().contains("weak"))
                    {
                        valid = solver.updatePeak(item, PeakCycle.Cycle2Weak);
                    }

                    if(valid)
                    {
                        LOG.info("command is valid, telling the Solver");
                        if(solver.allTentativeD2Set())
                        {
                            var recs = solver.redoDay2Recs();

                            if(recs== null || recs.size() == 0)
                            {
                                return Mono.just(eventMessage)
                                        .flatMap(Message::getChannel)
                                        .flatMap(channel -> channel.createMessage("<@"+miennaID+"> No recs returned"))
                                        .then();
                            }


                            return Mono.just(eventMessage)
                                    .flatMap(Message::getChannel)
                                    .flatMap(channel -> channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), recs.get(0), c1PeakRole)).flatMap(Message::publish))
                                    .then();
                        }
                        else
                        {
                            final String text = "Item "+item.getDisplayName()+" set to "+(strong?"strong":"weak"+" peak. Still waiting on more required info.");
                            return Mono.just(eventMessage)
                                    .flatMap(Message::getChannel)
                                    .flatMap(channel -> channel.createMessage(text))
                                    .then();
                        }
                    }
                    else
                    {
                        LOG.info("command is for item we don't need");
                        return Mono.just(eventMessage)
                                .flatMap(Message::getChannel)
                                .flatMap(channel -> channel.createMessage("No peak info needed for "+item.getDisplayName()))
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

    private Mono<Void> processNextWeekCommand(Message eventMessage)
    {
        var recs = solver.getVacationRecs();
        if(recs == null)
        {
            LOG.info("Has no next week info. Maybe we needed to reboot the server and now it lost it.");
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
            solver.getDailyRecommendations(week, day, true);
            recs = solver.getVacationRecs();
        }

        if(recs == null)
            return Mono.just(eventMessage)
                    .flatMap(Message::getChannel)
                    .flatMap(channel -> channel.createMessage("<@"+miennaID+"> No vacation recs returned"))
                    .then();

        var embed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, recs);
        return Mono.just(eventMessage)
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(embed))
                .then();
    }


}