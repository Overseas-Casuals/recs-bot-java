package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.PeakCycle;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.NewsChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class CommandListener implements EventListener<ChatInputInteractionEvent>
{
    Logger LOG = LoggerFactory.getLogger(CommandListener.class);

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;

    @Value("${discord.recsChannel}")
    String recsChannelID;

    @Value("${mienna}")
    private String miennaID;

    @Value("${solver.island.rank}")
    private int maxIslandRank;


    @Autowired
    Solver solver;
    @Override
    public Class<ChatInputInteractionEvent> getEventType() {
        return ChatInputInteractionEvent.class;
    }

    @Override
    public Mono<Void> execute(ChatInputInteractionEvent event) {
        String command = event.getCommandName();
        LOG.info("Processsing {} command", command);
        switch (command) {
            case "set_peak" -> {
                processSetPeakCommand(event).block();
                return deferredPeakResponse(event);
            }
            case "set_schedule" -> {
                event.deferReply().withEphemeral(true).block();
                return deferredScheduleResponse(event);
            }
            case "next_week" -> {
                event.deferReply().block();
                return deferredNextWeekCommand(event);
            }
            case "this_week" -> {
                event.deferReply().block();
                return deferredThisWeekCommand(event);
            }
            case "today" -> {
                event.deferReply().block();
                return deferredTodayCommand(event);
            }
            case "rerun" -> {
                event.deferReply().withEphemeral(true).block();
                return deferredRerunCommand(event);
            }
            case "alts" -> {
                event.deferReply().block();
                return deferredAltsCommand(event);
            }
        }
        return event.reply()
                .withEphemeral(true)
                .withContent("Command "+event.getCommandName()+" not recognized");
    }

    private Mono<Void> processSetPeakCommand(ChatInputInteractionEvent event)
    {
        LOG.info("Processing set peak command. Options:");
        var opts = event.getOptions();
        for(var opt : opts)
        {
            if(opt.getValue().isPresent())
                LOG.info(opt.getName()+": "+opt.getValue().get().asString());
            else
                LOG.info(opt.getName()+": absent");
        }

        return event.deferReply(/*InteractionCallbackSpec.builder().ephemeral(true).build()*/);
    }

    private Mono<Void> deferredPeakResponse(ChatInputInteractionEvent event)
    {
        String itemName = event.getOption("item")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .get();

        Item item;
        try{
            item = Item.valueOf(itemName);
        }
        catch(IllegalArgumentException e)
        {
            return event.editReply(itemName+" is not a valid item").then();
        }
        boolean valid = false;
        if(!solver.hasRunRecs)
        {
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
            if(day==0)
            {

                LOG.info("Haven't run recs yet. Doing so now.");
                solver.getDailyRecommendations(week, day, true);
            }
        }

        if(solver.isSolvedD2()) //Really just doesn't have anything
        {
            return event.editReply("Current cycle doesn't need confirmation of any peaks.").then();
        }
        String peakType = event.getOption("peak_type")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .get();

        if(peakType.equals("strong"))
        {
            valid = solver.updatePeak(item, PeakCycle.Cycle2Strong);
        }
        else if(peakType.equals("weak"))
        {
            valid = solver.updatePeak(item, PeakCycle.Cycle2Weak);
        }

        if(valid)
        {
            LOG.info("command is valid");
            if(solver.allTentativeD2Set())
            {
                LOG.info("All troublemakers set. Running solver again");
                var recs = solver.redoDay2Recs();

                if(recs== null || recs.size() == 0)
                {
                    return event.editReply("Set peak for item "+item.getDisplayName()+", but no recs returned. <@"+miennaID+">").then();
                }

                NewsChannel channel = event.getClient().getChannelById(Snowflake.of(recsChannelID))
                        .cast(NewsChannel.class).block();

                recs.forEach(
                        rec -> channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), rec, c1PeakRole))
                                .flatMap(Message::publish).subscribe()
                );

                return event.editReply("Item "+item.getDisplayName()+" set to "+peakType+" peak. Generating recs.").then();
            }
            else
            {
                return event.editReply("Item "+item.getDisplayName()+" set to "+peakType+" peak. Still waiting on more required info.").then();
            }
        }
        else
        {
            LOG.info("command is for item we don't need");
            return event.editReply("No peak info needed for "+item.getDisplayName()).then();
        }
    }

    private Mono<Void> deferredScheduleResponse(ChatInputInteractionEvent event)
    {
        List<Item> items = new ArrayList<>();
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
        }
        for(int i=1; i<=6; i++)
        {
            if(event.getOption("craft_"+i).isPresent())
            {
                String itemName = event.getOption("craft_"+i).flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();
                try
                {
                    items.add(Item.valueOf(itemName));
                }
                catch(IllegalArgumentException e)
                {
                    return event.editReply(itemName+" is not a valid item").then();
                }
            }
            else
            {
                LOG.info("Craft "+i+" isn't present. Stopping converting list");
                break;
            }
        }

        int day = event.getOption("cycle").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .get().intValue();
        solver.setScheduleCommand(day, rank, items);

        return event.editReply("Created schedule of "+(items.size() > 0? items : "Rest")+" for cycle "+(day+1)).then();
    }

    private Mono<Void> deferredNextWeekCommand(ChatInputInteractionEvent event)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
        }
        if(!solver.hasRunRecs)
        {
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getVacationRecs(rank);

        if(recs == null || recs.size() < 5)
        {
          if(rank >=9 && rank <=11)
              return event.editReply("No vacation recs returned. <@"+miennaID+">").then();
          else
              return event.editReply("No vacation recs available for rank "+rank).then();
        }


        var embed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, recs, rank);

        return event.editReply().withEmbeds(embed).then();
    }

    private Mono<Void> deferredThisWeekCommand(ChatInputInteractionEvent event)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
        }



        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
        if(day >= 3)
            return event.editReply("Rest of week already known. See <#"+recsChannelID+">").then();

        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getRestOfWeekRecs(rank);


        if(recs == null || recs.size() == 0)
            return event.editReply("No rest of week recs returned. <@"+miennaID+">").then();

        var embed = OCUtils.generateThisWeekEmbed(solver.getWeek(), recs, rank);

        return event.editReply().withEmbeds(embed).then();
    }

    private Mono<Void> deferredTodayCommand(ChatInputInteractionEvent event)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
        }

        var calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        var hour = calendar.get(Calendar.HOUR_OF_DAY);
        if(hour < 8)
            hour += 24;
        hour = (hour - 8) % 24;
        int hoursLeft = 24 - (((hour / 2) + 1) * 2);

        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        //If we don't have this, it's because we haven't run recs at all
        //So run recs to get things all set up
        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getRestOfDayRecs(day, hoursLeft, rank);

        if((recs == null || recs.size() == 0) && hoursLeft >= 4)
        {
            return event.editReply("No rest of day recs returned. <@"+miennaID+">").then();
        }

        var embed = OCUtils.generateTodayEmbed(week, day, hoursLeft, recs, rank);

        return event.editReply().withEmbeds(embed).then();
    }

    private Mono<Void> deferredRerunCommand(ChatInputInteractionEvent event)
    {
        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        LOG.info("Reruning recs for today");
        var recs = solver.getDailyRecommendations(week, day, true);

        if(recs== null || recs.size() == 0)
        {
            return event.editReply("No recs returned. <@"+miennaID+">").then();
        }
        NewsChannel channel = event.getClient().getChannelById(Snowflake.of(recsChannelID))
                .cast(NewsChannel.class).block();



        recs.forEach(
                rec -> channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), rec, c1PeakRole))
                        .flatMap(Message::publish).subscribe()
        );

        return event.editReply("Re-ran recs successfully. Check <#"+recsChannelID+">").then();
    }

    private Mono<Void> deferredAltsCommand(ChatInputInteractionEvent event)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
        }

        List<Item> items = new ArrayList<>();
        for(int i=1; i<=3; i++)
        {
            if(event.getOption("nocraft"+i).isPresent())
            {
                String itemName = event.getOption("nocraft"+i).flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();
                try
                {
                    items.add(Item.valueOf(itemName));
                }
                catch(IllegalArgumentException e)
                {
                    return event.editReply(itemName+" is not a valid item").then();
                }
            }
            else
            {
                LOG.info("Craft "+i+" isn't present. Stopping converting list");
                break;
            }
        }

        var calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        var hour = calendar.get(Calendar.HOUR_OF_DAY);
        if(hour < 8)
            hour += 24;
        hour = (hour - 8) % 24;

        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        if(day == 6)
            return event.editReply("Rest C1").then();

        if(rank == maxIslandRank && items.size() == 0)
            return event.editReply("See <#"+recsChannelID+">").then();

        //If we don't have this, it's because we haven't run recs at all
        //So run recs to get things all set up
        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        String content = "";
        if(items.size()>0)
            content = "Not using items "+ items.stream().map(Item::getDisplayName).collect(Collectors.joining(", "));


        var dailyRec = solver.getRecForSingleDay(day+1, rank, items);

        if(dailyRec == null)
        {
            return event.editReply("No alt recs returned. <@"+miennaID+">").then();
        }
        else if(dailyRec.isTentative())
        {
            return event.editReply("C2 info not known. Need more peaks").then();
        }

        var embed = OCUtils.getGeneralRecEmbed(week, dailyRec);

        return event.editReply(content).withEmbeds(embed).then();
    }

}
