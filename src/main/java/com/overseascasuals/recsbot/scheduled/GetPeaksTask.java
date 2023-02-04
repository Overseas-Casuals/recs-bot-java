package com.overseascasuals.recsbot.scheduled;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.json.ItemSupply;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.json.TCDay;
import com.overseascasuals.recsbot.mysql.*;
import com.overseascasuals.recsbot.solver.Solver;
import com.overseascasuals.recsbot.twitter.RecsTweet;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
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
    @Value("${tcUrl}")
    private String tcURL;

    @Value("${mienna}")
    private String miennaID;

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;
    @Value("${discord.squawkboxRole}")
    String squawkboxRole;
    @Value("${discord.crimeRole}")
    String crimeTimeRole;

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

    private String cron = "0 10 8 ? * TUE-FRI";

    private GatewayDiscordClient client;

    private static ObjectMapper objectMapper = new ObjectMapper();

    private MessageChannel channel;
    private MessageChannel peakChannel;

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
        for(int recDay= startDay; recDay<= endDay; recDay++)
        {
            boolean validTCPeaks = false;
            List<TCDay> tcDays = null;
            boolean alreadyHavePeaks = false;
            String response = null;

            int peakday = Math.min(recDay, 3);
            List<CraftPeaks> peaksByDay = peakRepository.findPeaksByDay(week, peakday);
            if(peaksByDay != null && peaksByDay.size() > 0)
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
                        tcDays = objectMapper.readValue(response, new TypeReference<>(){});
                    validTCPeaks = tcDays != null && tcDays.size() > recDay && tcDays.get(recDay).getObjects() != null && tcDays.get(recDay).getObjects().size() > 0;
                }
                catch(Exception e)
                {
                    LOG.error("Error parsing data from TC: "+response, e);
                }
            }

            if(validTCPeaks)
            {
                if(recDay > 0)
                    peaksByDay = peakRepository.findPeaksByDay(week, recDay-1);
                else
                    peaksByDay = new ArrayList<>();
                List<CraftPeaks> lastWeeksPeaks = peakRepository.findPeaksByDay(week-1, 3);

                validTCPeaks = validate62Peaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay);
                validTCPeaks = validTCPeaks && validate63Peaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay);
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
                    try{
                        LOG.info("Sending popularity to island.ws: "+restService.postPopularity(week,tcDays.get(0).getPopularity(),  tcDays.get(0).getPredictedPopularity()));
                    }
                    catch(RestClientException e)
                    {
                        LOG.error("Failed to send popularity to peak DB", e);
                        peakChannel.createMessage("<@"+miennaID+"> Couldn't connect to peak database to send popularity").subscribe();
                    }

                }

                for(var singlePeak : peaksByDay)
                {
                    peakRepository.save(singlePeak);
                }

                try {
                    LOG.info("Sending peaks to island.ws: " + restService.postPeaks(week, recDay, peaksByDay));
                }
                catch(RestClientException e)
                {
                    LOG.error("Failed to send peaks to peak DB", e);
                    peakChannel.createMessage("<@"+miennaID+"> Couldn't connect to peak database to send peaks").subscribe();
                }

            }
            else if (!alreadyHavePeaks)
            {
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                LOG.warn("Peaks were invalid. Rescheduling");
                int delay = 15;
                scheduler.schedule(this, delay, TimeUnit.MINUTES);
                scheduler.shutdown();
                return;
            }
            //Also send to Discord
            var peaksArray = peaksByDay.stream().map(CraftPeaks::getPeak).toArray();
            peakChannel.createMessage("peaks: " + Arrays.toString(peaksArray)).subscribe();

            var list = solver.getDailyRecommendations(week, recDay, false, peaksByDay);
            if(list == null)
                peakChannel.createMessage("<@"+miennaID+"> No recs returned").subscribe();
            else
            {
                if(recDay == 3)
                {
                    int numDays = list.size()/3;
                    for(int i=0; i<numDays;i++)
                    {
                        var crimes = solver.crimeTimeRecs.get(i);

                        channel.createMessage(OCUtils.createCombinedC4Post(week, list, squawkboxRole, solver.totalValue)).flatMap(Message::publish).subscribe();
                        channel.createMessage(OCUtils.createCrimeTimePost(week, list, crimes, crimeTimeRole, solver.crimeTimeValue)).flatMap(Message::publish).subscribe();

                        //Pop the first 3 recs and start again
                        //Note: Like, test this before we do multiple ranks again
                        list.remove(0);
                        list.remove(0);
                        list.remove(0);
                    }


                }
                else
                {
                    for(var recs : list)
                    {
                        var message = channel.createMessage(OCUtils.generateRecEmbedMessage(week, recs.withRank(-1), c1PeakRole, squawkboxRole));
                        if(recs.getOldRec() != null)
                        {
                            message.subscribe();
                        }
                        else
                        {
                            message.flatMap(Message::publish).subscribe();
                        }

                        try{
                            RecsTweet.sendRecAsReply(week, recs, !local);
                        }
                        catch(Exception e)
                        {
                            LOG.error("Error tweeting!!",e);
                        }

                    }
                }
            }
        }
    }



    private boolean validate62Peaks(List<CraftPeaks> newPeaks, List<CraftPeaks> oldPeaks, List<TCDay> tcDays, int week, int day)
    {
        day = Math.min(day, 3);
        boolean valid;

        LOG.info("Validating TC peaks from day {} for items 1-50", day+1);

        int num2Weak = 0;
        int num2Strong = 0;
        int num3Strong = 0;
        int num3Weak = 0;
        int num4Weak = 0;
        int num4Strong = 0;
        int num5Weak = 0;
        int num5Strong = 0;
        int num6Weak = 0;
        int num6Strong = 0;
        int num7Weak = 0;
        int num7Strong = 0;
        int num45 = 0;
        int num67 = 0;
        int num5 = 0;

        if(day >= tcDays.size())
        {
            LOG.warn("Could not find today's data in TC data. Only found "+tcDays.size()+" days. Needed day "+(day+1));
            return false;
        }

        //Day 1
        if(day==0)
        {
            int num2Unk = 0;
            for(int i=0; i<50; i++)
            {
                var lastWeekPeak = oldPeaks.get(i);
                ItemSupply supply = tcDays.get(0).getObjects().get(lastWeekPeak.getPeakID().getItemID());
                String peakString;
                if(supply.getSupply() == Surplus)
                    return false;
                else if(supply.getSupply() == Insufficient)
                {
                    if(supply.getDemand() == Skyrocketing && lastWeekPeak.getPeakEnum().isReliable)
                    {
                        num2Strong++;
                        peakString = "2S";
                    }
                    else if (supply.getDemand() == Increasing || supply.getDemand() == Decreasing)
                    {
                        num2Weak++;
                        peakString = "2W";
                    }
                    else if (supply.getDemand() == None) {
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

            if (num2Strong == 4)
                for (int i = 0; i < 50; i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2W");
            if (num2Weak == 4)
                for (int i = 0; i < 50; i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2S");

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()<=50).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            valid = num2Unk+num2Strong+num2Weak==8 && num2Strong<=4 && num2Weak<=4;
            LOG.info(MessageFormatter.format("As of day 1, 1-50 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

            LOG.info("Peaks for 1-50 C1: num2Strong={}/4, num2Weak={}/4, num2Unknown={}/0", num2Strong, num2Weak, num2Unk);


            return valid;
        }


        //Day 2
        if(day==1)
        {
            for (int i = 0; i < 50; i++)
            {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(1).getObjects().get(i+1);
                if (supply.getSupply() == Nonexistent) {
                    num2Strong++;
                    currentPeak.setPeak("2S");
                }
                else if (supply.getSupply() == Insufficient && (currentPeak.getPeak().equals("2U") || currentPeak.getPeak().equals("2W")))
                {
                    num2Weak++;
                    currentPeak.setPeak("2W");
                }
                else if (supply.getSupply() == Insufficient) //Peaks D3 or 6/7
                {
                    if (supply.getDemand() == Skyrocketing)
                    {
                        num3Strong++;
                        currentPeak.setPeak("3S");
                    }
                    else
                    {
                        num67++;
                        currentPeak.setPeak("67");;
                    }
                }
                else
                {
                    num45++;
                    currentPeak.setPeak("45");;
                }
            }
            valid = num2Strong == 4 && num2Weak == 4 && num3Strong == 4 && num45 == 16 && num67 == 22;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()<=50).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            LOG.info(MessageFormatter.format("As of day 2, 1-50 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

                LOG.info("peaks for 1-50 D2 "+", num2Strong = "+num2Strong+"/4"+", num2Weak = "+num2Weak+"/4"+", num3Strong = "+num3Strong+"/4"+", num45 = "+num45+"/16"+", num67 = "+num67+"/22");

            return valid;
        }



        if(day==2) {
            //Day 3
            for (int i = 0; i < 50; i++) {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(2).getObjects().get(i + 1);
                if (currentPeak.getPeak().equals("45")) {
                    if (supply.getSupply() == Insufficient) //potentialPeaks 4
                    {
                        if (supply.getDemand() == Skyrocketing) {
                            num4Strong++;
                            currentPeak.setPeak("4S");
                        } else {
                            num4Weak++;
                            currentPeak.setPeak("4W");
                        }
                    } else //potentialPeaks 5
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
                    else if (supply.getSupply() == Sufficient && supply.getDemand() == Decreasing) {
                        num6Weak++;
                        currentPeak.setPeak("6W");
                    } else {
                        num67++;
                    }
                }
                else if(currentPeak.getPeak().equals("3W"))
                    num3Weak++;
            }
            valid = num3Weak == 4 && num4Weak == 4 && num4Strong == 4 && num5 == 8 && num6Weak == 4 && num67 == 14;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()<=50).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            LOG.info(MessageFormatter.format("As of day 3, 1-50 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

                LOG.info("Peaks for 1-50 D3 "+ ", num3Weak = " + num3Weak + "/4"  + ", num4Weak = " + num4Weak + "/4" + ", num4Strong = " + num4Strong + "/4" + ", num5 = " + num5 + "/8" + ", num6Weak = " + num6Weak + "/4" + ", num67 = " + num67 + "/14");

            return valid;
        }

        //Day 4
        for (int i = 0; i < 50; i++)
        {
            CraftPeaks currentPeak = newPeaks.get(i);
            currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
            ItemSupply supply = tcDays.get(3).getObjects().get(i + 1);

            if (currentPeak.getPeak().equals("67") && supply.getSupply() == Sufficient)
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
            else if (currentPeak.getPeak().equals("5U"))
            {
                if (supply.getSupply() == Insufficient)
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
            }
        }
        valid = num5Weak == 4 && num5Strong == 4 && num6Strong == 4 && num7Weak == 5 && num7Strong == 5;

        String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()<=50).map(CraftPeaks::toString).collect(Collectors.joining(", "));
        LOG.info(MessageFormatter.format("As of day 4, 1-50 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

            LOG.info("peaks for 1-50 D4 "+", num5Weak = "+num5Weak+"/4"+", num5Strong = "+num5Strong+"/4"
                    +", num6Strong = "+num6Strong+"/4"+", num7Weak = "+num7Weak+"/5"
                    +", num7Strong = "+num7Strong+"/5");
        return valid;
    }

    private boolean validate63Peaks(List<CraftPeaks> newPeaks, List<CraftPeaks> oldPeaks, List<TCDay> tcDays, int week, int day)
    {
        day = Math.min(day, 3);
        boolean valid;

        LOG.info("Validating TC peaks from day {} for items 51-60", day+1);

        if(day >= tcDays.size())
        {
            LOG.warn("Could not find today's data in TC data. Only found "+tcDays.size()+" days. Needed day "+(day+1));
            return false;
        }

        //Day 1
        if(day==0)
        {
            int num2Strong = 0;
            int num2Weak = 0;
            int num2Unk = 0;
            for(int i=50; i<60; i++)
            {
                var lastWeekPeak = oldPeaks.get(i);
                ItemSupply supply = tcDays.get(0).getObjects().get(lastWeekPeak.getPeakID().getItemID());
                String peakString;
                if(supply.getSupply() == Surplus)
                    return false;
                else if(supply.getSupply() == Insufficient)
                {
                    if(supply.getDemand() == Skyrocketing && lastWeekPeak.getPeakEnum().isReliable)
                    {
                        num2Strong++;
                        peakString = "2S";
                    }
                    else if (supply.getDemand() == Increasing || supply.getDemand() == Decreasing)
                    {
                        num2Weak++;
                        peakString = "2W";
                    }
                    else if (supply.getDemand() == None) {
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

            if (num2Strong == 1 && num2Unk == 1)
                for (int i = 50; i < newPeaks.size(); i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2W");
            if (num2Weak == 1 && num2Unk == 1)
                for (int i = 50; i < newPeaks.size(); i++)
                    if (newPeaks.get(i).getPeak().equals("2U"))
                        newPeaks.get(i).setPeak("2S");

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>50 && peak.getPeakID().getItemID()<=60).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            valid = num2Unk + num2Strong + num2Weak <=2 && num2Strong <= 1 && num2Weak <= 1 && num2Unk <=2;

            LOG.info(MessageFormatter.format("As of day 1, 51-60 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

            LOG.error("peaks for 51-60 C1: num2Strong={}/1?, num2Weak={}/1?, num2Unknown={}/0", num2Strong, num2Weak, num2Unk);


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
            for (int i = 50; i < 60; i++)
            {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(1).getObjects().get(i+1);
                if (supply.getSupply() == Nonexistent) {
                    num2Strong++;
                    currentPeak.setPeak("2S");
                }
                else if (supply.getSupply() == Insufficient && (currentPeak.getPeak().equals("2U") || currentPeak.getPeak().equals("2W")))
                {
                    num2Weak++;
                    currentPeak.setPeak("2W");
                }
                else if (supply.getSupply() == Insufficient) //Peaks D3 or 6/7
                {
                    if (supply.getDemand() == Skyrocketing)
                    {
                        num3Strong++;
                        currentPeak.setPeak("3S");
                    }
                    else
                    {
                        num67++;
                        currentPeak.setPeak("67");;
                    }
                }
                else
                {
                    num45++;
                    currentPeak.setPeak("45");;
                }
            }
            valid = num2Strong <= 1 && num2Weak <=1 && num3Strong <=1 && num45 <= 4 && num67 <= 5;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>50 && peak.getPeakID().getItemID()<=60).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            LOG.info(MessageFormatter.format("As of day 2, 51-60 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

                LOG.info("Peaks for 51-60 D2 "+", num2Strong = "+num2Strong+"/1?"+", num2Weak = "+num2Weak+"/1?"+", num3Strong = "+num3Strong+"/1?"+", num45 = "+num45+"/4?"+", num67 = "+num67+"/5?");

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
            for (int i = 50; i < 60; i++) {
                CraftPeaks currentPeak = newPeaks.get(i);
                currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
                ItemSupply supply = tcDays.get(2).getObjects().get(i + 1);
                if (currentPeak.getPeak().equals("45")) {
                    if (supply.getSupply() == Insufficient) //potentialPeaks 4
                    {
                        if (supply.getDemand() == Skyrocketing) {
                            num4Strong++;
                            currentPeak.setPeak("4S");
                        } else {
                            num4Weak++;
                            currentPeak.setPeak("4W");
                        }
                    } else //potentialPeaks 5
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
                    else if (supply.getSupply() == Sufficient && supply.getDemand() == Decreasing) {
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
            valid = num3Weak <= 1 && num4Weak <= 1 && num4Strong <= 1 && num5 <= 2 && num6Weak <=1 && num67 <=3 && numEarlier+num3Weak+num4Weak+num4Strong+num5+num6Weak+num67 == 10;

            String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>50 && peak.getPeakID().getItemID()<=60).map(CraftPeaks::toString).collect(Collectors.joining(", "));
            LOG.info(MessageFormatter.format("As of day 3, 51-60 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

                LOG.info("peaks for 51-60 C3 "+ ", num3Weak = " + num3Weak + "/1"  + ", num4Weak = " + num4Weak + "/1" + ", num4Strong = " + num4Strong + "/1" +
                        ", num5 = " + num5 + "/2" + ", num6Weak = " + num6Weak + "/1" + ", num67 = " + num67 + "/3. Peaked earlier: {}",numEarlier);

            return valid;
        }

        int num6Strong = 0;
        int num7Weak = 0;
        int num7Strong = 0;
        int num5Strong = 0;
        int num5Weak = 0;

        int numEarlier = 0;
        //Day 4
        for (int i = 50; i < 60; i++)
        {

            CraftPeaks currentPeak = newPeaks.get(i);
            currentPeak.setPeakID(new PeakID(week, day, currentPeak.getPeakID().getItemID()));
            ItemSupply supply = tcDays.get(3).getObjects().get(i + 1);

            if (currentPeak.getPeak().equals("67"))
            {
                if(supply.getSupply() == Sufficient)
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
                if (supply.getSupply() == Insufficient)
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
            }
            else
            {
                numEarlier++;
            }
        }
        valid = num5Weak <= 1 && num5Strong <=1 && num6Strong <=1 && num7Weak <=1 && num7Strong <= 1 && numEarlier + num5Weak + num5Strong + num6Strong + num7Weak + num7Strong == 10;

        String peaks = newPeaks.stream().filter(peak -> peak.getPeakID().getItemID()>50 && peak.getPeakID().getItemID()<=60).map(CraftPeaks::toString).collect(Collectors.joining(", "));

        LOG.info(MessageFormatter.format("As of day 4, 51-60 safe? {}, Peaks: {}, ", valid, peaks).getMessage());

            LOG.info("peaks for 51-60 C4"+", num5Weak = "+num5Weak+"/1"+", num5Strong = "+num5Strong+"/1"
                    +", num6Strong = "+num6Strong+"/1"+", num7Weak = "+num7Weak+"/1"
                    +", num7Strong = "+num7Strong+"/1 Peaked earlier: {}",numEarlier);
        return valid;
    }
}
