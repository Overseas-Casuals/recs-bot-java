package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.PeakCycle;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.NewsChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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
        switch(event.getCommandName())
        {
            case "set_peak":
                return processSetPeakCommand(event);
            case "set_schedule":
                return processSetScheduleCommand(event);
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
            return event.reply().withContent(itemName+" is not a valid item").withEphemeral(true);
        }

        boolean valid = false;
        if(!solver.hasTentativeD2())//Might be uninitialized
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

        if(!solver.hasTentativeD2()) //Really just doesn't have anything
        {
            return event.reply().withContent("Current cycle doesn't need confirmation of any peaks.").withEphemeral(true);
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
            LOG.info("command is valid, telling the Solver");
            if(solver.allTentativeD2Set())
            {
                var recs = solver.redoDay2Recs();

                if(recs== null || recs.size() == 0)
                {
                    event.getClient().getChannelById(Snowflake.of(recsChannelID))
                            .cast(MessageChannel.class)
                            .flatMap(channel -> channel.createMessage("<@"+miennaID+"> No recs returned")).subscribe();
                }

                event.getClient().getChannelById(Snowflake.of(recsChannelID))
                        .cast(NewsChannel.class)
                        .flatMap(channel -> channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), recs.get(0), c1PeakRole))
                        .flatMap(Message::publish)).subscribe();

                return event.reply().withContent("Item "+item.getDisplayName()+" set to "+peakType+" peak. Generating recs.");
            }
            else
            {
                return event.reply().withContent("Item "+item.getDisplayName()+" set to "+peakType+" peak. Still waiting on more required info.");
            }
        }
        else
        {
            LOG.info("command is for item we don't need");
            return event.reply().withContent("No peak info needed for "+item.getDisplayName()).withEphemeral(true);
        }
    }

    private Mono<Void> processSetScheduleCommand(ChatInputInteractionEvent event)
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
                    return event.reply().withContent(itemName+" is not a valid item").withEphemeral(true);
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

        return event.reply().withContent("Created schedule of "+items+" for cycle "+(day+1)).withEphemeral(true);
    }
}
