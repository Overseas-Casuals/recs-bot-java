package com.overseascasuals.recsbot.scheduled;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.json.ItemSupply;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.json.TCDay;
import com.overseascasuals.recsbot.mysql.*;
import com.overseascasuals.recsbot.solver.Solver;
import com.overseascasuals.recsbot.twitter.RecsTweet;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ChannelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.overseascasuals.recsbot.data.Supply.*;
import static com.overseascasuals.recsbot.data.DemandShift.*;

@Service
public class GetPeaksTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(GetPeaksTask.class);
    @Value("${discord.recsChannel}")
    private String recsChannel;

    @Value("${discord.peaksChannel}")
    private String peaksChannel;

    @Value("${discord.archiveChannel}")
    private String archiveChannelID;

    @Value("${discord.fortunetellerChannelID}")
    private String fortuneChannelID;
    @Value("${tcUrl}")
    private String tcURL;

    @Value("${chinaTcUrl}")
    private String tcChinaURL;

    @Value("${mienna}")
    private String miennaID;

    @Value("${discord.clairvoyantRole}")
    private String clairvoyantRole;

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;
    @Value("${discord.squawkboxRole}")
    String squawkboxRole;

    @Value("${testing.startDay}")
    int startDayOverride;

    @Value("${testing.endDay}")
    int endDayOverride;

    @Value("${testing.week}")
    int weekOverride;

    private boolean local;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    RestService restService;

    @Autowired
    Solver solver;

    private String cron = "0 10 8 ? * *";

    private GatewayDiscordClient client;

    private static ObjectMapper objectMapper = new ObjectMapper();

    private MessageChannel channel;
    private MessageChannel peakChannel;
    private Channel archiveChannel;
    private MessageChannel fortuneChannel;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void initialize(GatewayDiscordClient client, boolean local) {
        this.client = client;
        channel = client.getChannelById(Snowflake.of(recsChannel))
                .cast(MessageChannel.class).block();
        peakChannel = client.getChannelById(Snowflake.of(peaksChannel))
                .cast(MessageChannel.class).block();
        archiveChannel = client.getChannelById(Snowflake.of(archiveChannelID))
                /*.cast(MessageChannel.class)*/.block();
        fortuneChannel = client.getChannelById(Snowflake.of(fortuneChannelID)).cast(MessageChannel.class).block();
        this.local = local;
    }

    @Override
    public void run()
    {
        var d1 = new Date(1661241600000l);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int actualDay = (int)((d2.getTime()-d1.getTime())/86400000) % 7;
        int startDay = actualDay;
        int endDay = actualDay;

        if(local)
        {
            if(startDayOverride != -1)
                startDay = startDayOverride;
            if(endDayOverride != -1)
                endDay = endDayOverride;
            if(weekOverride != -1)
                week = weekOverride;
        }

        LOG.info("Getting info on day {} with start day {}, end day {}, and week {}\nOverrides: start {} end {} week {}", actualDay, startDay, endDay, week, startDayOverride, endDayOverride, weekOverride);
        for(int recDay = startDay; recDay<= endDay; recDay++)
        {
            boolean validTCPeaks = false;
            List<TCDay> tcDays = null;
            boolean alreadyHavePeaks = false;
            String response = null;

            int peakday = Math.min(recDay, 3);
            List<CraftPeaks> peaksByDay = peakRepository.findPeaksByDay(week, peakday);
            if(peaksByDay != null && peaksByDay.size() >= Solver.getNumItems(week))
            {
                LOG.info("Peaks for day {} already found. Skipping grabbing from TC.", peakday+1);
                alreadyHavePeaks = true;
            }
            else
            {
                LOG.info("Nothing found in DB, getting info from TC");
                try
                {
                    response = restService.getURLResponse(tcURL);
                    //Parse data from JSON
                    if(response != null)
                    {
                        response = response.replaceAll("(?<=demand|supply)\":([5-9]\\d*|\\d\\d+)", "\":5");
                        tcDays = objectMapper.readValue(response, new TypeReference<>() {});
                    }

                    validTCPeaks = tcDays != null && tcDays.size() > recDay && tcDays.get(recDay) != null && tcDays.get(recDay).getObjects() != null && tcDays.get(recDay).getObjects().size() > 0;
                }
                catch(Exception e)
                {
                    LOG.error("Error parsing data from TC: "+response, e);
                    validTCPeaks = false;
                }
            }

            if(validTCPeaks)
            {
                if(recDay > 0)
                    peaksByDay = peakRepository.findPeaksByDay(week, recDay-1);
                else
                    peaksByDay = new ArrayList<>();
                List<CraftPeaks> lastWeeksPeaks = peakRepository.findPeaksByDay(week-1, 3);


                validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay, 1,50);

                if(validTCPeaks)
                    validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay,51,62);
                if(validTCPeaks)
                    validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay,63,74);
                if(validTCPeaks)
                    validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay,75,86);

                if(!validTCPeaks)
                {
                    LOG.info("Invalid peaks found. Checking CN/KR info??");
                    List<TCDay> chinaDays = null;
                    //try china?
                    try
                    {
                        response = restService.getURLResponse(tcChinaURL);
                        //Parse data from JSON
                        if(response != null)
                        {
                            response = response.replaceAll("(?<=demand|supply)\":([5-9]\\d*|\\d\\d+)", "\":5");
                            chinaDays = objectMapper.readValue(response, new TypeReference<>() {});
                        }

                        validTCPeaks = chinaDays != null && chinaDays.size() > recDay && chinaDays.get(recDay) != null && chinaDays.get(recDay).getObjects() != null && chinaDays.get(recDay).getObjects().size() > 0;
                    }
                    catch(Exception e)
                    {
                        LOG.error("Error parsing data from KRCN TC: "+response, e);
                        validTCPeaks = false;
                    }

                    if(validTCPeaks)
                    {
                        if(recDay > 0)
                            peaksByDay = peakRepository.findPeaksByDay(week, recDay-1);
                        else
                            peaksByDay = new ArrayList<>();

                        validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, chinaDays, week, recDay, 1,50);

                        if(validTCPeaks)
                            validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, chinaDays, week, recDay,51,62);
                        if(validTCPeaks)
                            validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay,63,74);
                        if(validTCPeaks)
                            validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay,75,86);
                    }
                }

            }
            else if (!alreadyHavePeaks)
            {
                LOG.error("Invalid info gotten from TC: {}", response);
                if(tcDays!=null)
                {
                    LOG.error("TC days size: {}", tcDays.size());
                    if(tcDays.size() > recDay)
                        LOG.error("Current TC data for day {}: {}", recDay, tcDays.get(recDay));
                    else
                        LOG.error("TC doesn't have enough data for day {}", recDay);
                }
                else
                    LOG.error("TC days is null");
            }


            if(validTCPeaks)
            {
                //Send to DB
                if(recDay==0)
                {
                    //write popularity data
                    Popularity pop = new Popularity();
                    pop.setWeek(week);
                    pop.setPopularity(tcDays.get(0).getPopularity());
                    pop.setNextPopularity(tcDays.get(0).getPredictedPopularity());
                    popularityRepository.save(pop);
                    /*try{
                        LOG.info("Sending popularity to island.ws: "+restService.postPopularity(week,tcDays.get(0).getPopularity(),  tcDays.get(0).getPredictedPopularity()));
                    }
                    catch(RestClientException e)
                    {
                        LOG.error("Failed to send popularity to peak DB", e);
                        peakChannel.createMessage("<@"+miennaID+"> Couldn't connect to peak database to send popularity").subscribe();
                    }*/

                }

                for(var singlePeak : peaksByDay)
                {
                    peakRepository.save(singlePeak);
                }

                /*try {
                    LOG.info("Sending peaks to island.ws: " + restService.postPeaks(week, recDay, peaksByDay));
                }
                catch(RestClientException e)
                {
                    LOG.error("Failed to send peaks to peak DB", e);
                    peakChannel.createMessage("<@"+miennaID+"> Couldn't connect to peak database to send peaks").subscribe();
                }*/

            }
            else if (!alreadyHavePeaks)
            {
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                LOG.warn("Peaks were invalid. Rescheduling");
                int delay = 15;
                long timestamp = System.currentTimeMillis() + delay * 1000 * 60;
                peakChannel.createMessage("Peaks were invalid. Rescheduling for <t:"+timestamp/1000+":t>").subscribe();
                scheduler.schedule(this, delay, TimeUnit.MINUTES);
                scheduler.shutdown();
                return;
            }

            List<DailyRecommendation> list;

            try
            {
                list = solver.getDailyRecommendations(week, recDay, false, peaksByDay);
            }
            catch(Exception e)
            {
                LOG.error("Error running recs. Rescheduling. ", e);

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                int delay = 15;
                long timestamp = System.currentTimeMillis() + delay * 1000 * 60;
                peakChannel.createMessage("Error running recs. Rescheduling for <t:"+timestamp/1000+":t>").subscribe();
                scheduler.schedule(this, delay, TimeUnit.MINUTES);
                scheduler.shutdown();
                return;
            }

            var peaksArray = peaksByDay.stream().map(CraftPeaks::getPeak).limit(Solver.getNumItems(week)).toArray();
            peakChannel.createMessage("peaks: " + Arrays.toString(peaksArray)).subscribe();

            if(list == null || list.size() == 0)
            {
                if(list == null)
                    peakChannel.createMessage("<@" + miennaID + "> No recs returned").subscribe();
                else
                {
                    if(recDay < 3)
                    {
                        for(var recs : list) {
                            channel.createMessage(OCUtils.generateRecEmbedMessage(week, recs, miennaID, miennaID)).flatMap(Message::publish)
                                    .subscribe(message -> LOG.info("Successfully posted recs: {}", message.getEmbeds()),
                                            error -> LOG.error("Error posting recs:", error));
                        }

                    }
                    else if(recDay == 3)
                    {
                        var combinedC4Post = MessageCreateSpec.builder();

                        var c4Message = OCUtils.createCombinedC4Post(week, list, solver.totalValue);

                        combinedC4Post.addEmbed(c4Message);

                        peakChannel.createMessage(combinedC4Post.build()).flatMap(Message::publish).subscribe(message -> {LOG.info("Successfully posted C4 recs: {}", message.getEmbeds());}, error -> { LOG.error("Error posting C4 combined post:",error); });

                    }
                }

            }
            else
            {
                var lastArchiveMessageID = archiveChannel.getRestChannel().getData().map(ChannelData::lastMessageId).block().get().orElseThrow();

                if(list.get(0).getOldRec() != null)
                {
                    var recs = list.get(0);
                    if(recs.getOldRec().getItems().equals(recs.getBestRec().getItems()))
                    {
                        //archiveChannel.createMessage("<@&"+archiveRole+"> Final value for Cycle "+(recs.getDay()+1)+"!\n"+recs.getBestRec().getItems()+" = "+recs.getDailyValue()+" ("+recs.getGroovelessValue()+" grooveless)").subscribe(message -> {LOG.info("Successfully posted final value: {}", message.getContent());}, error -> { LOG.error("Error posting updated cycle value:",error); });
                    }
                    else
                    {
                        channel.createMessage(OCUtils.generateRecEmbedMessage(week, recs, c1PeakRole, squawkboxRole)).subscribe(message -> {LOG.info("Successfully posted day-of update: {}", message.getEmbeds());}, error -> { LOG.error("Error posting updated cycle schedule:",error); });
                    }

                    list.remove(0);

                    if(recs.getMaxRank()==Solver.maxIslandRank)
                    {
                        //Add to archive
                        var archive = client.getMessageById(Snowflake.of(archiveChannelID), Snowflake.of(lastArchiveMessageID)).block();
                        archive.edit(OCUtils.addCurrentDay(recDay, recs, archive)).subscribe(message -> {LOG.info("Successfully posted new day to archive: {}", message.getContent());}, error -> { LOG.error("Error posting new archive day:",error);});
                    }
                }
                if(recDay == 3)
                {
                    int numRanks = list.size()/3;

                    for(int i=0; i<numRanks;i++)
                    {
                        var combinedC4Post = MessageCreateSpec.builder().content("<@&"+squawkboxRole+">" + OCUtils.getFlavorText(list));


                        var c4Message = OCUtils.createCombinedC4Post(week, list, numRanks-1==i?solver.totalValue:-1);

                        combinedC4Post.addEmbed(c4Message);

                        channel.createMessage(combinedC4Post.build()).flatMap(Message::publish).subscribe(message -> {LOG.info("Successfully posted C4 recs: {}", message.getEmbeds());}, error -> { LOG.error("Error posting C4 combined post:",error); });

                        //Pop the first 3 recs and start again
                        //Note: Like, test this before we do multiple ranks again
                        if(i==numRanks-1)
                        {
                            for(int d=0;d<3;d++)
                            {
                                trySendTweet(week, list.get(d));
                            }

                            var archive = client.getMessageById(Snowflake.of(archiveChannelID), Snowflake.of(lastArchiveMessageID)).block();
                            archive.edit(OCUtils.addFinalTotal(list, week, solver.totalValue, archive)).subscribe(message -> {LOG.info("Successfully posted final total to archive: {}", message.getContent());}, error -> { LOG.error("Error posting final total to archive:",error);});
                        }

                        list.remove(0);
                        list.remove(0);
                        list.remove(0);
                    }
                    if(solver.fortuneValue > 0)
                    {
                        fortuneChannel.createMessage("Season total: "+String.format("%,d", solver.fortuneValue)+OCUtils.cowriesEmoji).subscribe(message -> {LOG.info("Successfully posted fortune teller total: {}", message.getContent());}, error -> { LOG.error("Error posting fortune-teller total:",error);});
                    }
                    archiveChannel.getRestChannel().createMessage(OCUtils.newArchiveContent(week+1)).subscribe(message -> {LOG.info("Successfully posted new archive post: {}", message.content());}, error -> { LOG.error("Error posting new archive post:",error);});
                }
                else
                {
                    if(recDay == 1)
                    {
                        var embed = OCUtils.generateThisWeekEmbed(week, solver.fortuneTellerRecs, -1);

                        fortuneChannel.createMessage(MessageCreateSpec.builder().content("<@&"+clairvoyantRole+">" + OCUtils.getFlavorText(solver.fortuneTellerRecs)).addEmbed(embed).build()).flatMap(Message::publish).subscribe(message -> {LOG.info("Successfully posted fortune teller recs: {}", message.getEmbeds());}, error -> { LOG.error("Error posting fortune-teller recs:",error);});
                    }
                    for(var recs : list)
                    {
                        channel.createMessage(OCUtils.generateRecEmbedMessage(week, recs, c1PeakRole, squawkboxRole)).flatMap(Message::publish).subscribe(message -> {LOG.info("Successfully posted recs: {}", message.getEmbeds());}, error -> { LOG.error("Error posting recs:",error); });
                        trySendTweet(week, recs);
                        if(recs.isRestRecommended() && recs.getMaxRank()==Solver.maxIslandRank)
                        {
                            var archive = client.getMessageById(Snowflake.of(archiveChannelID), Snowflake.of(lastArchiveMessageID)).block();
                            archive.edit(OCUtils.addCurrentDay(recDay+1, recs, archive)).subscribe(message -> {LOG.info("Successfully posted new rest day to archive: {}", message.getContent());}, error -> { LOG.error("Error posting new rest day to archive:",error);});
                        }
                    }

                }
            }
        }
    }

    private void trySendTweet(int week, DailyRecommendation rec)
    {
        if(rec.getMaxRank() != Solver.maxIslandRank)
            return;
        try{
            RecsTweet.sendRec(week, rec, !local);
        }
        catch(Exception e)
        {
            LOG.error("Error tweeting!!",e);
        }
    }

    private boolean validatePeaks(List<CraftPeaks> newPeaks, List<CraftPeaks> oldPeaks, List<TCDay> tcDays, int week, int day, int firstItem, int lastItem)
    {
        boolean firstGroup = firstItem==1;

        int expectedSinglePeaks = firstGroup?4:1;

        day = Math.min(day, 3);
        boolean valid;

        LOG.info("Validating TC peaks from day {} for items {}-{}", day+1, firstItem, lastItem);

        if(day >= tcDays.size())
        {
            LOG.warn("Could not find today's data in TC data. Only found "+tcDays.size()+" days. Needed day "+(day+1));
            return false;
        }
        for(int i=firstItem-1; i<lastItem; i++)
        {
            int itemID = oldPeaks.get(i).getPeakID().getItemID();
            var observed = tcDays.get(day).getObjects().get(itemID);
            if(observed.getSupply() == InvalidSupply || observed.getDemand() == InvalidDemand)
            {
                LOG.warn("Invalid supply/demand found for item {}: {} {}", itemID, observed.getSupply(), observed.getDemand());
                return false;
            }
        }

        //Day 1
        if(day==0)
        {
            int num2Strong = 0;
            int num2Weak = 0;
            int num2Unk = 0;
            for(int i=firstItem-1; i<lastItem; i++)
            {
                var lastWeekPeak = oldPeaks.get(i);
                ItemSupply supply = tcDays.get(0).getObjects().get(lastWeekPeak.getPeakID().getItemID());
                String peakString;
                if(supply.getSupply() == Insufficient)
                {
                    if(supply.getDemand() == Skyrocketing && lastWeekPeak.getPeakEnum().isReliable)
                    {
                        num2Strong++;
                        peakString = "2S";
                    }
                    else if (supply.getDemand() == Increasing)
                    {
                        num2Weak++;
                        peakString = "2W";
                    }
                    else if (supply.getDemand() == None) { //This will never happen. RIP.
                        num2Strong++;
                        peakString = "2S";
                    }
                    else
                    {
                        num2Unk++;
                        peakString = "2U";
                    }

                }
                else
                    peakString = "U1";

                CraftPeaks newCraft = new CraftPeaks();
                newCraft.setPeak(peakString);
                newCraft.setPeakID(new PeakID(week, day, supply.getId()));
                newPeaks.add(newCraft);
            }

            if (num2Strong == expectedSinglePeaks && num2Unk == expectedSinglePeaks)
                for (int i = firstItem-1; i < newPeaks.size() && i<lastItem; i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2W");
            if (num2Weak == expectedSinglePeaks && num2Unk == expectedSinglePeaks)
                for (int i = firstItem-1; i < newPeaks.size() && i<lastItem; i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2S");

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>=firstItem && peak.getPeakID().getItemID()<=lastItem).map(CraftPeaks::getPeak).collect(Collectors.joining(", "));
            valid = num2Unk + num2Strong + num2Weak == expectedSinglePeaks*2 && num2Strong <= expectedSinglePeaks && num2Weak <= expectedSinglePeaks && num2Unk <=expectedSinglePeaks*2;

            LOG.info("As of day 1, {}-{} safe? {}, Peaks: {}, ", firstItem, lastItem,valid, peaks);

            LOG.error("peaks for {}-{} C1: num2Strong={}/{}?, num2Weak={}/{}?, num2Unknown={}/0", firstItem, lastItem, num2Strong, expectedSinglePeaks, num2Weak, expectedSinglePeaks, num2Unk);


            return valid;
        }


        //Day 2
        if(day==1)
        {
            int num2Strong = 0;
            int num2Weak = 0;
            int num3Strong = 0;
            int num67 = 0;
            int num45 = 0;
            for(int i=firstItem-1; i<lastItem; i++)
            {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(1).getObjects().get(i+1);
                if (supply.getSupply() == Nonexistent) {
                    num2Strong++;
                    currentPeak.setPeak("2S");
                }
                else if (supply.getSupply() == Insufficient && (currentPeak.getPeak().equals("2U") || currentPeak.getPeak().equals("2W")|| currentPeak.getPeak().equals("2S")))
                {
                    num2Weak++;
                    currentPeak.setPeak("2W");
                }
                else if (supply.getDemand() == Skyrocketing)
                {
                    num3Strong++;
                    currentPeak.setPeak("3S");
                }
                else if(supply.getDemand() == Increasing)
                {
                    num67++;
                    currentPeak.setPeak("67");;
                }
                else
                {
                    num45++;
                    currentPeak.setPeak("45");;
                }
            }
            int expected67 = firstGroup?22:5;
            valid = num2Strong == expectedSinglePeaks && num2Weak == expectedSinglePeaks && num3Strong == expectedSinglePeaks && num45 == expectedSinglePeaks*4 && num67 == expected67;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>=firstItem && peak.getPeakID().getItemID()<=lastItem).map(CraftPeaks::getPeak).collect(Collectors.joining(", "));
            LOG.info("As of day 2, {}-{} safe? {}, Peaks: {}, ", firstItem, lastItem, valid, peaks);

            LOG.info("Peaks for {}-{} D2, num2Strong = "+num2Strong+"/{}"+", num2Weak = "+num2Weak+"/{}"+", num3Strong = "+num3Strong+"/{}"+", num45 = "+num45+"/{}"+", num67 = "+num67+"/{}", firstItem, lastItem, expectedSinglePeaks, expectedSinglePeaks, expectedSinglePeaks, expectedSinglePeaks*4, expected67);

            return valid;
        }



        if(day==2) {
            int num4Strong = 0;
            int num5 = 0;
            int num4Weak = 0;
            int num3Weak = 0;
            int num67 = 0;
            int num6Weak = 0;
            int numEarlier = 0;
            //Day 3
            for(int i=firstItem-1; i<lastItem; i++)
            {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(2).getObjects().get(i + 1);
                if (currentPeak.getPeak().equals("45"))
                {
                    if (supply.getDemand() == Skyrocketing)
                    {
                        num4Strong++;
                        currentPeak.setPeak("4S");
                    }
                    else if(supply.getDemand() == Increasing)
                    {
                        num4Weak++;
                        currentPeak.setPeak("4W");
                    }
                    else //potentialPeaks 5
                    {
                        num5++;
                        currentPeak.setPeak("5U");
                    }
                } else if (currentPeak.getPeak().equals("67")) {
                    if(supply.getSupply() == Insufficient)
                    {
                        num3Weak++;
                        currentPeak.setPeak("3W");
                    }
                    else if (supply.getDemand() == Decreasing) {
                        num6Weak++;
                        currentPeak.setPeak("6W");
                    } else {
                        num67++;
                    }
                }
                else if(currentPeak.getPeak().equals("3W"))
                    num3Weak++;
                else
                {
                    numEarlier++;
                }

            }
            int expected67 = firstGroup?14:3;
            valid = num3Weak == expectedSinglePeaks && num4Weak == expectedSinglePeaks && num4Strong == expectedSinglePeaks && num5 == expectedSinglePeaks*2 && num6Weak ==expectedSinglePeaks && num67 ==expected67 && numEarlier == expectedSinglePeaks*3;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>=firstItem && peak.getPeakID().getItemID()<=lastItem).map(CraftPeaks::getPeak).collect(Collectors.joining(", "));
            LOG.info("As of day 3, {}-{} safe? {}, Peaks: {}, ", firstItem, lastItem, valid, peaks);

            LOG.info("peaks for {}-{} C3, num3Weak = " + num3Weak + "/{}"  + ", num4Weak = " + num4Weak + "/{}" + ", num4Strong = " + num4Strong + "/{}" +
                    ", num5 = " + num5 + "/{}" + ", num6Weak = " + num6Weak + "/{}" + ", num67 = " + num67 + "/{}. Peaked earlier: {}",firstItem, lastItem, expectedSinglePeaks, expectedSinglePeaks, expectedSinglePeaks, expectedSinglePeaks*2, expectedSinglePeaks, expected67, numEarlier);

            return valid;
        }

        int num6Strong = 0;
        int num7Weak = 0;
        int num7Strong = 0;
        int num5Strong = 0;
        int num5Weak = 0;

        int numEarlier = 0;
        //Day 4
        for(int i=firstItem-1; i<lastItem; i++)
        {

            CraftPeaks currentPeak = newPeaks.get(i);
            currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
            ItemSupply supply = tcDays.get(3).getObjects().get(i + 1);

            if (currentPeak.getPeak().equals("67"))
            {
                if(supply.getSupply() == Sufficient || supply.getSupply() == Surplus)
                {
                    if (supply.getDemand() == Skyrocketing)
                    {
                        num6Strong++;
                        currentPeak.setPeak("6S");
                    }
                    else if (supply.getDemand() == Increasing)
                    {
                        num7Weak++;
                        currentPeak.setPeak("7W");
                    }
                    else
                    {
                        num7Strong++;
                        currentPeak.setPeak("7S");
                    }
                }
            }
            else if (currentPeak.getPeak().equals("5U"))
            {
                if (supply.getDemand() == Skyrocketing) {
                    num5Strong++;
                    currentPeak.setPeak("5S");
                }
                else
                {
                    num5Weak++;
                    currentPeak.setPeak("5W");
                }
            }
            else
            {
                numEarlier++;
            }
        }
        int expected7 = firstGroup?5:1;
        valid = num5Weak == expectedSinglePeaks && num5Strong == expectedSinglePeaks && num6Strong == expectedSinglePeaks && num7Weak == expected7 && num7Strong == expected7 && numEarlier == expectedSinglePeaks*7;

        String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>=firstItem && peak.getPeakID().getItemID()<=lastItem).map(CraftPeaks::getPeak).collect(Collectors.joining(", "));

        LOG.info("As of day 4, {}-{} safe? {}, Peaks: {}, ", firstItem, lastItem, valid, peaks);

        LOG.info("peaks for {}-{} C4"+", num5Weak = "+num5Weak+"/{}"+", num5Strong = "+num5Strong+"/{}"
                +", num6Strong = "+num6Strong+"/{}"+", num7Weak = "+num7Weak+"/{}"
                +", num7Strong = "+num7Strong+"/{} Peaked earlier: {}",firstItem, lastItem, expectedSinglePeaks, expectedSinglePeaks, expectedSinglePeaks, expected7, expected7, numEarlier);
        return valid;
    }
}
