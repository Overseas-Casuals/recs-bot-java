package com.overseascasuals.recsbot.scheduled;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.json.ItemSupply;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.json.TCDay;
import com.overseascasuals.recsbot.mysql.*;
import com.overseascasuals.recsbot.solver.Solver;
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

    @Value("${testing.startDay}")
    int startDayOverride;

    @Value("${testing.endDay}")
    int endDayOverride;

    @Value("${testing.week}")
    int weekOverride;

    @Value("${spring.profiles.active}")
    private String activeProfile;

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
    public void initialize(GatewayDiscordClient client) {
        this.client = client;
        channel = client.getChannelById(Snowflake.of(recsChannel))
                .cast(MessageChannel.class).block();
        peakChannel = client.getChannelById(Snowflake.of(peaksChannel))
                .cast(MessageChannel.class).block();
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

        if("local".equals(activeProfile))
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
            var peaksByDay = peakRepository.findPeaksByDay(week, peakday);
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
                peaksByDay = new ArrayList<>();
                List<CraftPeaks> lastWeeksPeaks = peakRepository.findPeaksByDay(week-1, 3);
                validTCPeaks = validatePeaks(peaksByDay, lastWeeksPeaks, tcDays, week, recDay);
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
                for(var recs : list)
                {
                    var message = channel.createMessage(OCUtils.generateRecEmbedMessage(week, recs, c1PeakRole));
                    if(recs.isTentative())
                        message.subscribe();
                    else
                        message.flatMap(Message::publish).subscribe();
                }

                if(recDay == 3)
                {
                    var crimes = solver.getCrimeTimeRecs();
                    for(var crime : crimes)
                        channel.createMessage(OCUtils.generateCrimeTimeEmbed(week, crime)).flatMap(Message::publish).subscribe();
                }
            }

        }
    }



    private boolean validatePeaks(List<CraftPeaks> newPeaks, List<CraftPeaks> oldPeaks, List<TCDay> tcDays, int week, int day)
    {
        day = Math.min(day, 3);
        boolean valid;

        LOG.info("Validating TC peaks from day {}", day+1);

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

        if(tcDays.get(day).getObjects().size()<=oldPeaks.size())
        {
            LOG.warn("Could not find enough crafts TC data. Only found "+(tcDays.get(day).getObjects().size() -1 )+" crafts. Needed "+oldPeaks.size());
            return false;
        }

        //Day 1
        for(var lastWeekPeak : oldPeaks)
        {
            ItemSupply supply = tcDays.get(0).getObjects().get(lastWeekPeak.getPeakID().getItemID());
            String peakString;
            if(supply.getSupply() == Surplus)
                break;
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
                    peakString = "2U";
            }
            else
                peakString = "U1";

            CraftPeaks newCraft = new CraftPeaks();
            newCraft.setPeak(peakString);
            newCraft.setPeakID(new PeakID(week, day, supply.getId()));
            newPeaks.add(newCraft);
        }

        if (num2Strong == 5)
            for (int i = 0; i < newPeaks.size(); i++)
                if (newPeaks.get(i).getPeak().equals("2U"))
                    newPeaks.get(i).setPeak("2W");
        if (num2Weak == 5)
            for (int i = 0; i < newPeaks.size(); i++)
                if (newPeaks.get(i).getPeak().equals("2U"))
                    newPeaks.get(i).setPeak("2S");

        String peaks = Arrays.toString(newPeaks.toArray());
        LOG.info(MessageFormatter.format("As of day 1, Peaks: {}, safe? {}", peaks, true ).getMessage());
        if (day == 0)
            return true;

        //Day 2
        num2Strong = 0;
        num2Weak = 0;

        for (int i = 0; i < newPeaks.size(); i++) 
        {
            CraftPeaks currentPeak = newPeaks.get(i);
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
                /*else if (supply.getDemand() == Increasing)
                {
                    num3Weak++;
                    currentPeak.setPeak("3W");;
                }*/
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

        valid = num2Strong == 5 && num2Weak == 5 && num3Strong == 5 && num3Weak == 0 && num45 == 20 && num67 == 25;

        peaks = Arrays.toString(newPeaks.toArray());
        LOG.info(MessageFormatter.format("As of day 2, Peaks: {}, safe? {}", peaks, valid ).getMessage());
        if(!valid)
            LOG.error("Invalid peaks for D2 "+", num2Strong = "+num2Strong+"/5"+", num2Weak = "+num2Weak+"/5"+", num3Strong = "+num3Strong+"/5"+", num3Weak = "+num3Weak+"/5"+", num45 = "+num45+"/20"+", num67 = "+num67+"/20");
        if (day == 1)
            return valid;

        //Day 3
        num67 = 0;
        num3Weak = 0;
        for (int i = 0; i < newPeaks.size(); i++)
        {
            CraftPeaks currentPeak = newPeaks.get(i);
            ItemSupply supply = tcDays.get(2).getObjects().get(i+1);
            if (currentPeak.getPeak().equals("45"))
            {
                if (supply.getSupply() == Insufficient) //potentialPeaks 4
                {
                    if (supply.getDemand() == Skyrocketing) {
                        num4Strong++;
                        currentPeak.setPeak("4S");;
                    }
                    else
                    {
                        num4Weak++;
                        currentPeak.setPeak("4W");;
                    }
                }
                else //potentialPeaks 5
                {
                    num5++;
                    currentPeak.setPeak("5U");;
                }
            }
            else if (Objects.equals(currentPeak.getPeak(), "67"))
            {
                if (supply.getSupply() == Sufficient && supply.getDemand() == Decreasing) {
                    num6Weak++;
                    currentPeak.setPeak("6W");;
                }
                else if(supply.getSupply() == Insufficient)
                {
                    currentPeak.setPeak(("3W"));
                    num3Weak++;
                }
                else {
                    num67++;
                }
            }
        }
        valid = num3Weak == 5 && num4Weak == 5 && num4Strong == 5 && num5 == 10 && ((num6Weak == 5 && num67 == 15) || (num6Weak == 4 && num67 == 16));

        peaks = Arrays.toString(newPeaks.toArray());
        LOG.info(MessageFormatter.format("As of day 3, Peaks: {}, safe? {}", peaks, valid ).getMessage());

        if(!valid)
            LOG.error("Invalid peaks for D3 "+", num3Weak = "+num3Weak+"/5"+", num4Weak = "+num4Weak+"/5"+", num4Strong = "+num4Strong+"/5"+", num5 = "+num5+"/10"+", num6Weak = "+num6Weak+"/5?"+", num67 = "+num67+"/15?");
        if (day == 2)
            return valid;


        //Day 4
        for (int i = 0; i < newPeaks.size(); i++)
        {
            CraftPeaks currentPeak = newPeaks.get(i);
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
        valid = num5Weak == 5 && num5Strong == 5;// && num6Weak == 5 && num6Strong == 5 && num7Weak == 5 && num7Strong == 5;
        LOG.info("Peaks for D4 "+", num5Weak = "+num5Weak+"/5"+", num5Strong = "+num5Strong+"/5"
                +", num6Weak = "+num6Weak+"/5?"+", num6Strong = "+num6Strong+"/5?"+", num7Weak = "+num7Weak+"/5?"
                +", num7Strong = "+num7Strong+"/5?");

        peaks = Arrays.toString(newPeaks.toArray());
        LOG.info(MessageFormatter.format("As of day 4, Peaks: {}, safe? {}", peaks, valid ).getMessage());

        if(!valid)
            LOG.error("Invalid peaks for D4 "+", num5Weak = "+num5Weak+"/5"+", num5Strong = "+num5Strong+"/5"
                    +", num6Weak = "+num6Weak+"/5"+", num6Strong = "+num6Strong+"/5"+", num7Weak = "+num7Weak+"/5"
                    +", num7Strong = "+num7Strong+"/5");
        return valid;
    }
}
