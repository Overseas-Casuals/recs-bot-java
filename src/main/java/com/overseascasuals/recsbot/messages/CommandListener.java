package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.mysql.PeakRepository;
import com.overseascasuals.recsbot.mysql.PopularityRepository;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.NewsChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionReplyEditMono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class CommandListener implements EventListener<ChatInputInteractionEvent,Message>
{
    Logger LOG = LoggerFactory.getLogger(CommandListener.class);

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;
    @Value("${discord.squawkboxRole}")
    String squawkboxRole;
    @Value("${discord.recsChannel}")
    String recsChannelID;

    @Value("${mienna}")
    private String miennaID;

    private final int maxIslandRank = Solver.maxIslandRank;


    @Autowired
    Solver solver;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    RestService restService;

    public enum Material {Tinsand, Laver, Sap, Copper, RockSalt, Sugarcane, Cotton}

    //Crafting around really annoying materials exclusion list
    private final Map<Material, List<Item>> caramelMap = Map.of(
            Material.Laver, List.of(Item.SharkOil, Item.EssentialDraught, Item.VegetableJuice, Item.CawlCennin, Item.Dressing, Item.BoiledEgg, Item.PickledZucchini),
            Material.Sap, List.of(Item.SharkOil, Item.SweetPopoto, Item.ParsnipSalad, Item.Jam, Item.Honey, Item.DriedFlowers, Item.Dressing),
            Material.Copper, List.of(Item.Barbut, Item.BronzeSheep, Item.GarnetRapier, Item.SpruceRoundShield, Item.Ribbon, Item.Lantern, Item.Spectacles),
            Material.RockSalt, List.of(Item.Sauerkraut, Item.Butter, Item.SaltCod, Item.SquidInk, Item.OnionSoup, Item.Isloaf, Item.RunnerBeanSaute, Item.Natron),
            Material.Sugarcane, List.of(Item.Caramels, Item.Jam, Item.TomatoRelish, Item.Pie, Item.CornFlakes, Item.PickledRadish, Item.CoconutJuice),
            Material.Cotton, List.of(Item.Ribbon, Item.CavaliersHat, Item.SheepfluffRug, Item.Bed, Item.ScaleFingers, Item.FossilDisplay),
            Material.Tinsand, List.of(Item.BronzeSheep, Item.GarnetRapier, Item.SilverEarCuffs)
    );

    @Override
    public Class<ChatInputInteractionEvent> getEventType() {
        return ChatInputInteractionEvent.class;
    }

    @Override
    public Mono<Message> execute(ChatInputInteractionEvent event) {
        String command = event.getCommandName();
        LOG.info("Processing {} command", command);
        try {
            if (solver.isRunningRecs) {
                LOG.info("Telling them to chill for a sec");
                return event.deferReply().withEphemeral(true).then(event.editReply("Recs bot is running recs right now. Please try again in a minute or so!"));
            }

            switch (command) {
                case "set_schedule" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredScheduleResponse(event)));
                }
                case "next_week" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredNextWeekCommand(event)));
                }
                case "this_week" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredThisWeekCommand(event)));
                }
                case "today" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredTodayCommand(event)));
                }
                case "rerun" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredRerunCommand(event)));
                }
                case "alts" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredAltsCommand(event)));
                }
                case "push_peaks" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredPushPeaks(event)));
                }
                case "clear_cache" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredClearCache(event)));
                }
            }

            LOG.info("Unknown command???");
            return event.deferReply().withEphemeral(true)
                    .then(event.editReply("Command " + event.getCommandName() + " not recognized"));
        }
        catch(Exception e)
        {
            LOG.error("Exception handling /"+command, e);
        }
        try
        {
            return event.editReply("Error handling event./"+command+"<@"+miennaID+">");
        }
        catch(Exception ex)
        {
            LOG.error("Exception editing reply to /"+command, ex);
        }
        return event.deferReply()
                .then(event.editReply("Error handling event./"+command+"<@"+miennaID+">"));
    }

    private InteractionReplyEditMono deferredClearCache(ChatInputInteractionEvent event)
    {
        String cacheKey = event.getOption("key")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .get();

        solver.clearCache(cacheKey);
        return event.editReply("Cleared");
    }

    private InteractionReplyEditMono deferredScheduleResponse(ChatInputInteractionEvent event)
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
                        .get().replace(" ","");
                try
                {
                    items.add(Item.valueOf(itemName));
                }
                catch(IllegalArgumentException e)
                {
                    return event.editReply(itemName+" is not a valid item");
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

        return event.editReply("Created schedule of "+(items.size() > 0? items : "Rest")+" for cycle "+(day+1));
    }

    private InteractionReplyEditMono deferredNextWeekCommand(ChatInputInteractionEvent event)
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

        var recs = solver.getVacationRecs(Math.min(maxIslandRank,rank));

        if(recs == null || recs.size() < 5)
        {
          if(rank >=9 && rank <= maxIslandRank)
              return event.editReply("No vacation recs returned. <@"+miennaID+">");
          else
              return event.editReply("No vacation recs available for rank "+rank);
        }

        LOG.info("Returning next week from cache");

        var embed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, recs, rank);

        return event.editReply().withEmbeds(embed);
    }

    private InteractionReplyEditMono deferredThisWeekCommand(ChatInputInteractionEvent event)
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
        if(day==6)
            return event.editReply("It's Cycle 7 so this week is over.");
        else if(day >= 3)
            return deferredAltsCommand(event);

        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getRestOfWeekRecs(rank, false);


        if(recs == null || recs.getRecs() == null || recs.getRecs().size() == 0)
            return event.editReply("No rest of week recs returned. <@"+miennaID+">");

        var embed = OCUtils.generateThisWeekEmbed(solver.getWeek(), recs, rank);

        return event.editReply().withEmbeds(embed);
    }

    private InteractionReplyEditMono deferredTodayCommand(ChatInputInteractionEvent event)
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

        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        var recs = solver.getRestOfDayRecs(day, hoursLeft, rank,null);

        if((recs == null || recs.size() == 0) && hoursLeft >= 4)
        {
            return event.editReply("No rest of day recs returned. <@"+miennaID+">");
        }

        var embed = OCUtils.generateTodayEmbed(week, day, hoursLeft, recs, rank);

        return event.editReply().withEmbeds(embed);
    }

    private InteractionReplyEditMono deferredRerunCommand(ChatInputInteractionEvent event)
    {
        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        LOG.info("Rerunning recs for today");
        var recs = solver.getDailyRecommendations(week, day, true);

        if(recs== null || recs.size() == 0)
        {
            return event.editReply("No recs returned. <@"+miennaID+">");
        }
        NewsChannel channel = event.getClient().getChannelById(Snowflake.of(recsChannelID))
                .cast(NewsChannel.class).block();


        for (var rec: recs)
        {
            if(rec.getOldRec()==null)
                channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), rec.withRank(-1), c1PeakRole, squawkboxRole))
                    .flatMap(Message::publish).subscribe();
            else
                channel.createMessage(OCUtils.generateRecEmbedMessage(solver.getWeek(), rec.withRank(-1), c1PeakRole, squawkboxRole))
                        .subscribe();
        }

        return event.editReply("Re-ran recs successfully. Check <#"+recsChannelID+">");
    }

    private InteractionReplyEditMono deferredAltsCommand(ChatInputInteractionEvent event)
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
                        .get().replace(" ","");
                try
                {
                    items.add(Item.valueOf(itemName));
                }
                catch(IllegalArgumentException e)
                {
                    return event.editReply(itemName+" is not a valid item");
                }
            }
            else
            {
                break;
            }
        }
        for(int i=1; i<=3; i++)
        {
            if(event.getOption("nomat"+i).isPresent())
            {
                Material mat = Material.values()[event.getOption("nomat"+i).flatMap(ApplicationCommandInteractionOption::getValue).map(ApplicationCommandInteractionOptionValue::asLong).get().intValue()];
                LOG.info("Running alts excluding mat {}", mat);
                items.addAll(caramelMap.get(mat));
            }
            else
            {
                break;
            }
        }

        var calendar = Calendar.getInstance();
        calendar.setTime(new Date());

        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;



        if(day == 6)
            return event.editReply("It's Cycle 7! Set Cycle 1 of next season to rest, like always.");

        //If we don't have this, it's because we haven't run recs at all
        //So run recs to get things all set up
        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        if(Math.min(3,day) > solver.getDay() || week != solver.getWeek())
            return event.editReply("Don't have peak info for the current day. Wait until recs get run!");

        String content = "";
        if(items.size()>0)
            content = "Not using "+ items.stream().map(Item::getDisplayName).collect(Collectors.joining(", "));


        List<DailyRecommendation> recs = solver.getRecForSingleDay(day+1, rank, items, false);
        if(recs == null || recs.size() == 0 || recs.stream().anyMatch(Objects::isNull))
        {
            LOG.warn("Null/no recs in cache? Trying again");
            recs = solver.getRecForSingleDay(day+1, rank, items, true);
        }

        if(recs == null || recs.size() == 0 || recs.stream().anyMatch(Objects::isNull))
        {
            return event.editReply("No alt recs returned. <@"+miennaID+">");
        }
        else if(day == 4 && recs.size() == 3)
        {
            recs.remove(0);
        }

        List<EmbedCreateSpec> embeds = new ArrayList<>();
        for(var rec : recs)
        {
            embeds.add(OCUtils.getGeneralRecEmbed(week, rec.withRank(rank)));
        }

        return event.editReply(content).withEmbedsOrNull(embeds);
    }

    public InteractionReplyEditMono deferredPushPeaks(ChatInputInteractionEvent event)
    {
        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        var popularity = popularityRepository.findByWeek(week);
        String popresponse = restService.postPopularity(week, popularity.getPopularity(), popularity.getNextPopularity());

        var peaks = peakRepository.findPeaksByDay(week, day);
        String peakresponse = restService.postPeaks(week, day, peaks);

        return event.editReply("Posted popularity and peaks: "+popresponse+", "+peakresponse);
    }

}
