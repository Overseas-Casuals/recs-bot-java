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
        if(solver.isSolvedD2())//Might be uninitialized
        {
            LOG.info("Has no D2 info. Maaaaaaybe we needed to reboot the server and now it lost it.");
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
            if(day==0)
            {
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

                event.getClient().getChannelById(Snowflake.of(recsChannelID))
                        .cast(NewsChannel.class)
                        .flatMap(channel -> channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), recs.get(0), c1PeakRole))
                                .flatMap(Message::publish)).subscribe();

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
        solver.setScheduleCommand(day, items);

        return event.editReply("Created schedule of "+(items.size() > 0? items : "Rest")+" for cycle "+(day+1)).then();
    }

    private Mono<Void> deferredNextWeekCommand(ChatInputInteractionEvent event)
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

        if(recs == null || recs.size() < 5)
            return event.editReply("No vacation recs returned. <@"+miennaID+">").then();

        var embed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, recs);

        return event.editReply().withEmbeds(embed).then();
    }

    private Mono<Void> deferredThisWeekCommand(ChatInputInteractionEvent event)
    {
        var recs = solver.getRestOfWeek();
        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
        if(day >= 3)
            return event.editReply("Rest of week already known. See <#"+recsChannelID+">").then();

        if(recs == null)
        {
            LOG.info("Has no rest of week info. Maybe we needed to reboot the server and now it lost it.");

            solver.getDailyRecommendations(week, day, true);
            recs = solver.getRestOfWeek();
        }

        if(recs == null || recs.size() == 0)
            return event.editReply("No rest of week recs returned. <@"+miennaID+">").then();

        var embed = OCUtils.generateThisWeekEmbed(solver.getWeek(), recs);

        return event.editReply().withEmbeds(embed).then();
    }

    private Mono<Void> deferredTodayCommand(ChatInputInteractionEvent event)
    {
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
        if(solver.getVacationRecs() == null)
        {
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getRestOfDayRecs(day, hoursLeft);

        if((recs == null || recs.size() == 0) && hoursLeft >= 4)
        {
            return event.editReply("No rest of day recs returned. <@"+miennaID+">").then();
        }


        var embed = OCUtils.generateTodayEmbed(week, day, hoursLeft, recs);

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
}
