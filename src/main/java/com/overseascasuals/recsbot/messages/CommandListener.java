package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.*;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.mysql.PeakRepository;
import com.overseascasuals.recsbot.mysql.PopularityRepository;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
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
            Material.Laver, List.of(Item.SharkOil, Item.EssentialDraught, Item.VegetableJuice, Item.CawlCennin, Item.Dressing, Item.BoiledEgg, Item.PickledZucchini, Item.GrilledClam),
            Material.Sap, List.of(Item.BakedPumpkin, Item.SharkOil, Item.SweetPopoto, Item.ParsnipSalad, Item.IsleberryJam, Item.Honey, Item.DriedFlowers, Item.Dressing),
            Material.Copper, List.of(Item.Barbut, Item.BronzeSheep, Item.GarnetRapier, Item.SpruceRoundShield, Item.Ribbon, Item.Lantern, Item.Spectacles, Item.BrassServingDish),
            Material.RockSalt, List.of(Item.Sauerkraut, Item.Butter, Item.SaltCod, Item.SquidInk, Item.OnionSoup, Item.Isloaf, Item.RunnerBeanSaute, Item.Natron, Item.Peperoncino),
            Material.Sugarcane, List.of(Item.Caramels, Item.IsleberryJam, Item.TomatoRelish, Item.IslefishPie, Item.CornFlakes, Item.PickledRadish, Item.CoconutJuice, Item.SweetPopotoPie, Item.PickledZucchini),
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
                return event.deferReply().withEphemeral(true).then(event.editReply("Squawkbot is running recs right now. Please try again in a minute or so!"));
            }

            switch (command) {
                case "next_week" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredThisWeekCommand(event, true)));
                }
                case "this_week" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredThisWeekCommand(event, false)));
                }
                case "today" -> {
                    return event.deferReply().then(Mono.defer(() -> deferredTodayCommand(event)));
                }
                case "alts" -> {
                    LOG.info("Alts");
                    return event.deferReply().then(Mono.defer(() -> deferredThisWeekCommand(event, false)));
                }
                case "clear_cache" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredClearCache(event)));
                }
                case "favors" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredFavors(event)));
                }
                case "peak" -> {
                    return event.deferReply().withEphemeral(true).then(Mono.defer(() -> deferredPeaks(event)));
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
        finally
        {
            LOG.info("Initial defer for "+command+" command");
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

    private InteractionReplyEditMono deferredPeaks(ChatInputInteractionEvent event)
    {
        Set<Item> crafts;
        try{
            crafts = getItemsFromEvent(event, "craft");
        }
        catch(IllegalArgumentException e)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply(e.getMessage());
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

        if(crafts.size()>0)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply().withEmbeds(OCUtils.getPeaksEmbed(Solver.canonContext, new ArrayList<>(crafts), solver.getWeek()));
        }
        else
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("Please enter at least one craft you want to know the peak of!");
        }
    }
    private InteractionReplyEditMono deferredFavors(ChatInputInteractionEvent event)
    {
        Set<Item> favorsRaw = null;
        try{
            favorsRaw = getItemsFromEvent(event, "favor");
        }
        catch(IllegalArgumentException e)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply(e.getMessage());
        }

        boolean link48 = false;
        boolean link68 = false;
        boolean link46 = false;

        Item[] favors = new Item[3]; //4hr, 6hr, and 8hr favor, in order

        for(Item favor : favorsRaw)
        {
            int time = Solver.items[favor.ordinal()].time;
            if(favors[(time/2)-2] != null)
            {
                LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
                return event.editReply("Invalid favors specified. Multiple "+time+"-hour crafts listed.");
            }
            favors[(time/2)-2] = favor;
        }

        link46 = favors[0] != null && favors[1] != null && Solver.items[favors[0].ordinal()].getsEfficiencyBonus(Solver.items[favors[1].ordinal()]);
        link48 = favors[0] != null && favors[2] != null && Solver.items[favors[0].ordinal()].getsEfficiencyBonus(Solver.items[favors[2].ordinal()]);
        link68 = favors[1] != null && favors[2] != null && Solver.items[favors[1].ordinal()].getsEfficiencyBonus(Solver.items[favors[2].ordinal()]);

        List<List<Item>> favorSchedules = new ArrayList<>();

        if(!solver.hasRunRecs)
        {
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        //The work
        CraftContext context = new CraftContext(Solver.canonContext,0);
        if(favors[0] != null && favors[1] != null && favors[2] != null)//All 3 favors given
        {
            //All 3 link
            if(link46 && link48 && link68)
            {
                favorSchedules.add(List.of(favors[0], favors[2], favors[0], favors[2]));
                favorSchedules.add(List.of(favors[0], favors[2], favors[0], favors[2]));
                favorSchedules.add(List.of(favors[1], favors[0], favors[1], favors[2]));
                favorSchedules.add(List.of(favors[1], favors[0], favors[1], favors[2]));
            }
            else if(link46 && Solver.getBestLink(context, 4, favors[2]) != null)
            {
                Item eightLink = Solver.getBestLink(context, 4, favors[2]);
                Item fourLink = Solver.getBestLink(context, 4, favors[0]);

                favorSchedules.add(List.of(fourLink, favors[0], favors[1], favors[0], favors[1]));
                favorSchedules.add(List.of(fourLink, favors[0], favors[1], favors[0], favors[1]));
                favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
                favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
            }
            else if(link48)
            {
                favorSchedules.add(List.of(favors[0], favors[2], favors[0], favors[2]));
                favorSchedules.add(List.of(favors[0], favors[2], favors[0], favors[2]));
                Item sixLink= Solver.getBestLink(context, 8, favors[1]);
                if(sixLink == null)
                {
                    sixLink = Solver.getBestLink(context, 4, favors[1]);
                    favorSchedules.add(List.of(favors[0], sixLink, favors[1], sixLink, favors[1]));
                    favorSchedules.add(List.of(favors[0], sixLink, favors[1], sixLink, favors[1]));
                }
                else
                {
                    favorSchedules.add(List.of(favors[0], favors[1], sixLink, favors[1]));
                    favorSchedules.add(List.of(favors[0], favors[1], sixLink, favors[1]));
                }



            }
            else if(link68)
            {
                Item eightLink = Solver.getBestLink(context, 4, favors[2] ,favors[0]);
                if(eightLink == null)
                    eightLink = Solver.getBestLink(context, 4, favors[2]);

                Item fourLink = Solver.getBestLink(context, 4, favors[0]);

                if(eightLink != null)
                {
                    favorSchedules.add(List.of(favors[0], fourLink, favors[0], eightLink, favors[2]));
                    favorSchedules.add(List.of(favors[0], fourLink, favors[0], eightLink, favors[2]));
                    favorSchedules.add(List.of(favors[0], favors[1], favors[2], favors[1]));
                    favorSchedules.add(List.of(favors[0], favors[1], favors[2], favors[1]));
                }
                else
                {
                    eightLink = Solver.getBestLink(context, 6, favors[2]);
                    Item fourEightLink = Solver.getBestLink(context, 4, eightLink);
                    favorSchedules.add(List.of(fourEightLink, eightLink, favors[2], favors[1]));
                    favorSchedules.add(List.of(fourEightLink, eightLink, favors[2], favors[1]));
                    favorSchedules.add(List.of(fourEightLink, eightLink, favors[2], favors[1]));
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));

                }


            }
            else if(Solver.getBestLink(context, 4, favors[2]) == null) //Mammettttttt
            {
                Item fourLink = Solver.getBestLink(context, 4, favors[0]);
                Item sixLink = Solver.getBestLink(context, 4, favors[1]);
                Item eightLink = Solver.getBestLink(context, 6, favors[2]);
                favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));
                favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));
                favorSchedules.add(List.of(sixLink, favors[1], eightLink, favors[2]));
                favorSchedules.add(List.of(sixLink, favors[1], eightLink, favors[2]));
                favorSchedules.add(List.of(sixLink, favors[1], eightLink, favors[2]));
            }
            else
            {
                Item fourLink = Solver.getBestLink(context, 4, favors[0]);
                Item sixLink = Solver.getBestLink(context, 4, favors[1]);
                Item fourSixLink = Solver.getBestLink(context, 4, sixLink);

                favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourLink, favors[0]));
                favorSchedules.add(List.of(fourSixLink, sixLink, favors[1], sixLink, favors[1]));

                var possibleItems = solver.getItemsBetween(10, favors[0], favors[1]);
                if(possibleItems!=null)
                {
                    favorSchedules.add(List.of(fourLink, favors[0], possibleItems.get(0), possibleItems.get(1), favors[1]));
                }
                else
                {
                    Item sixFourLink = Solver.getBestLink(context, 6, favors[0]);

                    favorSchedules.add(List.of(fourLink, favors[0], sixFourLink, sixLink, favors[1]));
                }

                Item eightLink = Solver.getBestLink(context, 4, favors[2]);
                favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
                favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));

            }


        }
        else if(favors[0] != null && favors[1] != null)//4 and 6 hour given
        {
            if(link46)
            {
                favorSchedules.add(List.of(favors[0], favors[1], favors[0], favors[1], favors[0]));
                favorSchedules.add(List.of(favors[0], favors[1], favors[0], favors[1], favors[0]));
            }
            else
            {

                Item fourLink = Solver.getBestLink(context, 4, favors[0]);
                Item sixLink = Solver.getBestLink(context, 4, favors[1]);
                Item fourSixLink = Solver.getBestLink(context, 4, sixLink);

                favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourLink, favors[0]));
                favorSchedules.add(List.of(fourSixLink, sixLink, favors[1], sixLink, favors[1]));

                var possibleItems = solver.getItemsBetween(10, favors[0], favors[1]);
                if(possibleItems!=null)
                {
                    favorSchedules.add(List.of(fourLink, favors[0], possibleItems.get(0), possibleItems.get(1), favors[1]));
                }
                else
                {
                    Item sixFourLink = Solver.getBestLink(context, 6, favors[0]);

                    favorSchedules.add(List.of(fourLink, favors[0], sixFourLink, sixLink, favors[1]));
                }



            }
        }
        else if(link48) //4 and 8 hour given and link
        {
            Item eightLink = Solver.getBestLink(context, 4, favors[2]);
            favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
            Item fourLink = Solver.getBestLink(context, 4, favors[0]);
            favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));
            favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], favors[2]));
        }
        else if(link68) //6 and 8 hours given and link
        {
                Item sixLink = Solver.getBestLink(context, 4, favors[1]);
                Item eightLink = Solver.getBestLink(context, 4, favors[2]);
                if(eightLink != null)
                {
                    favorSchedules.add(List.of(sixLink, favors[1], favors[2], favors[1]));
                    favorSchedules.add(List.of(sixLink, favors[1], favors[2], favors[1]));
                    favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
                }
                else
                {
                    favorSchedules.add(List.of(favors[2], favors[1], favors[2]));
                    favorSchedules.add(List.of(favors[2], favors[1], favors[2]));
                    favorSchedules.add(List.of(favors[2], favors[1], favors[2]));
                }

        }
        else
        {
            if(favors[1] != null)
            {
                Item sixLink = Solver.getBestLink(context, 4, favors[1]);
                favorSchedules.add(List.of(sixLink, favors[1], sixLink, favors[1], sixLink));
                favorSchedules.add(List.of(sixLink, favors[1], sixLink, favors[1], sixLink));
            }
            if(favors[0] != null)
            {
                Item fourLink = Solver.getBestLink(context, 4, favors[0]);

                Item fourEightLink = Solver.getBestLink(context, 8, favors[0]);
                if(fourEightLink != null)
                {
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourEightLink));
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourEightLink));
                }
                else
                {
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourLink, favors[0]));
                    favorSchedules.add(List.of(fourLink, favors[0], fourLink, favors[0], fourLink, favors[0]));
                }

            }
            if(favors[2] != null)
            {
                Item eightLink = Solver.getBestLink(context, 4, favors[2]);
                if(eightLink == null)
                {
                    eightLink = Solver.getBestLink(context, 6, favors[2]);
                    favorSchedules.add(List.of(Solver.getBestLink(context, 4, eightLink), eightLink, favors[2], eightLink));
                    favorSchedules.add(List.of(favors[2], eightLink, favors[2]));
                    favorSchedules.add(List.of(favors[2], eightLink, favors[2]));
                }
                else
                {
                    favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
                    favorSchedules.add(List.of(eightLink, favors[2], eightLink, favors[2]));
                }
            }
        }
        if(favorSchedules.size()>0)
        {
            var embed = OCUtils.favorsEmbed(solver.getWeek(), favorSchedules);
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("Fit the following schedules anywhere in the given week. You need to use each schedule in one workshop each time it appears.\n\nThese schedules will frequently lose the efficiency bonus. That's normal.").withEmbeds(embed);
        }
        else
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("Please enter at least one favor you want to make");
        }
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

    private InteractionReplyEditMono deferredThisWeekCommand(ChatInputInteractionEvent event, boolean nextWeek)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            try
            {
                rank = Math.toIntExact(event.getOption("rank")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asLong).get());
            }
            catch (Exception e)
            {
                return event.editReply("Try a rank between 1 and 20");
            }
        }

        var d1 = new Date(1661241600000L);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        if(nextWeek)
            day = 0;
        if(day==6)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("It's Cycle 7 so this week is over.");
        }

        if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(week, day, true);
        }

        Set<Item> items;
        try
        {
            items = getItemsFromEvent(event);
        }
        catch(IllegalArgumentException e)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply(e.getMessage());
        }

        List<DailyRecommendation> recs = null;

        CraftContext context = null;
        if(nextWeek)
            context = new CraftContext(Solver.nextWeekContext, day);
        else
            context = new CraftContext(Solver.canonContext, day);

        try
        {
            recs = solver.getRecForDayOn(context, day+1, rank, items, false);
        }
        catch(NullPointerException e)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("Not enough crafts to work with. Try ?stockpile.");
        }

        String content = "";
        if(items.size()>0 && items != Solver.rareMatItems)
            content = "Not using "+ items.stream().map(Item::getDisplayName).collect(Collectors.joining(", "));
        else if(items.size()>0)
            content = "Not using any rare materials.";

        if(recs == null || recs.size() == 0)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("No rest of week recs returned. <@"+miennaID+">");
        }

        if(rank == maxIslandRank)
            rank++;
        int total = -1;

        if(day == 0)
        {
            total = 0;
            for(DailyRecommendation rec : recs)
            {
                if(!rec.isRestRecommended())
                    total+=rec.getDailyValue();
            }
        }

        var embed = OCUtils.generateThisWeekEmbed(context.getWeek(), recs, rank, total);
        LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());

        return event.editReply(content).withEmbedsOrNull(embed);
    }

    private InteractionReplyEditMono deferredTodayCommand(ChatInputInteractionEvent event)
    {
        int rank = maxIslandRank;
        if(event.getOption("rank").isPresent())
        {
            try
            {
                rank = Math.toIntExact(event.getOption("rank")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong).get());
            }
            catch (Exception e)
            {
                return event.editReply("Try a rank between 1 and 20");
            }
        }

        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(timeZone);
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

        //Need to copy whole context because today assumes you'll follow recs moving forward
        //It's probably safe to just pass in canon context but that assumes I never modify it at all and that sounds exhausting
        var recs = solver.getRestOfDayRecs(new CraftContext(Solver.canonContext, 6), day, hoursLeft, rank,null);

        if((recs == null || recs.size() == 0) && hoursLeft >= 4)
        {
            LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());
            return event.editReply("No rest of day recs returned. <@"+miennaID+">");
        }

        if(rank == maxIslandRank)
            rank++;
        var embed = OCUtils.generateTodayEmbed(week, day, hoursLeft, recs, rank);

        LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());

        return event.editReply().withEmbeds(embed);
    }

    private Set<Item> getItemsFromEvent(ChatInputInteractionEvent event) throws IllegalArgumentException
    {
        if(event.getOption("no_rare_mats").isPresent() && event.getOption("no_rare_mats").flatMap(ApplicationCommandInteractionOption::getValue).map(ApplicationCommandInteractionOptionValue::asBoolean).get())
            return Solver.rareMatItems;
        return getItemsFromEvent(event, "nocraft");
    }
    private Set<Item> getItemsFromEvent(ChatInputInteractionEvent event, String prefix) throws IllegalArgumentException
    {
        Set<Item> items = new TreeSet<>(); //TreeSet so list is consistently ordered
        for(int i=1; i<=6; i++)
        {
            if(event.getOption(prefix+i).isPresent())
            {
                String itemName = event.getOption(prefix+i).flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get().replace(" ","");
                try
                {
                    items.add(Item.getEnum(itemName));
                }
                catch(IllegalArgumentException e)
                {
                    throw new IllegalArgumentException(itemName+" is not a valid item");
                }
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
        }
        return items;
    }
}
