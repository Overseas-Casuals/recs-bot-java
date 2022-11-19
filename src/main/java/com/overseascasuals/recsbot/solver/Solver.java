package com.overseascasuals.recsbot.solver;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.data.*;
import com.overseascasuals.recsbot.json.PopularityInstance;
import com.overseascasuals.recsbot.json.PopularityJson;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.messages.MessageListener;
import com.overseascasuals.recsbot.mysql.*;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.overseascasuals.recsbot.data.Item.*;
import static com.overseascasuals.recsbot.data.ItemCategory.*;
import static com.overseascasuals.recsbot.data.PeakCycle.*;
import static com.overseascasuals.recsbot.data.RareMaterial.*;

@Service
public class Solver
{
    Logger LOG = LoggerFactory.getLogger(Solver.class);
    @Autowired
    RestService restService;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    CraftRepository craftRepository;

    final static int WORKSHOP_BONUS = 120;
    final static int GROOVE_MAX = 35;

    final static int NUM_WORKSHOPS = 3;
    
    final static ItemInfo[] items = {
            new ItemInfo(Potion,Concoctions,Invalid,28,4,1,null),
            new ItemInfo(Firesand,Concoctions,UnburiedTreasures,28,4,1,null),
            new ItemInfo(WoodenChair,Furnishings,Woodworks,42,6,1,null),
            new ItemInfo(GrilledClam,Foodstuffs,MarineMerchandise,28,4,1,null),
            new ItemInfo(Necklace,Accessories,Woodworks,28,4,1,null),
            new ItemInfo(CoralRing,Accessories,MarineMerchandise,42,6,1,null),
            new ItemInfo(Barbut,Attire,Metalworks,42,6,1,null),
            new ItemInfo(Macuahuitl,Arms,Woodworks,42,6,1,null),
            new ItemInfo(Sauerkraut,PreservedFood,Invalid,40,4,1,Map.of(Cabbage,1)),
            new ItemInfo(BakedPumpkin,Foodstuffs,Invalid,40,4,1,Map.of(Pumpkin,1)),
            new ItemInfo(Tunic,Attire,Textiles,72,6,1,Map.of(Fleece,2)),
            new ItemInfo(CulinaryKnife,Sundries,CreatureCreations,44,4,1,Map.of(Claw,1)),
            new ItemInfo(Brush,Sundries,Woodworks,44,4,1,Map.of(Fur, 1)),
            new ItemInfo(BoiledEgg,Foodstuffs,CreatureCreations,44,4,1,Map.of(Egg, 1)),
            new ItemInfo(Hora,Arms,CreatureCreations,72,6,1,Map.of(Carapace, 2)),
            new ItemInfo(Earrings,Accessories,CreatureCreations,44,4,1,Map.of(Fang, 1)),
            new ItemInfo(Butter,Ingredients,CreatureCreations,44,4,1,Map.of(Milk, 1)),
            new ItemInfo(BrickCounter,Furnishings,UnburiedTreasures,48,6,5,null),
            new ItemInfo(BronzeSheep,Furnishings,Metalworks,64,8,5,null),
            new ItemInfo(GrowthFormula,Concoctions,Invalid,136,8,5,Map.of(Alyssum, 2)),
            new ItemInfo(GarnetRapier,Arms,UnburiedTreasures,136,8,5,Map.of(Garnet,2)),
            new ItemInfo(SpruceRoundShield,Attire,Woodworks,136,8,5,Map.of(Spruce,2)),
            new ItemInfo(SharkOil,Sundries,MarineMerchandise,136,8,5,Map.of(Shark,2)),
            new ItemInfo(SilverEarCuffs,Accessories,Metalworks,136,8,5,Map.of(Silver,2)),
            new ItemInfo(SweetPopoto,Confections,Invalid,72,6,5,Map.of(Popoto, 2, Milk,1)),
            new ItemInfo(ParsnipSalad,Foodstuffs,Invalid,48,4,5,Map.of(Parsnip,2)),
            new ItemInfo(Caramels,Confections,Invalid,81,6,6,Map.of(Milk,2)),
            new ItemInfo(Ribbon,Accessories,Textiles,54,6,6,null),
            new ItemInfo(Rope,Sundries,Textiles,36,4,6,null),
            new ItemInfo(CavaliersHat,Attire,Textiles,81,6,6,Map.of(Feather,2)),
            new ItemInfo(Item.Horn,Sundries,CreatureCreations,81,6,6,Map.of(RareMaterial.Horn,2)),
            new ItemInfo(SaltCod,PreservedFood,MarineMerchandise,54,6,7,null),
            new ItemInfo(SquidInk,Ingredients,MarineMerchandise,36,4,7,null),
            new ItemInfo(EssentialDraught,Concoctions,MarineMerchandise,54,6,7,null),
            new ItemInfo(Jam,Ingredients,Invalid,78,6,7,Map.of(Isleberry,3)),
            new ItemInfo(TomatoRelish,Ingredients,Invalid,52,4,7,Map.of(Tomato,2)),
            new ItemInfo(OnionSoup,Foodstuffs,Invalid,78,6,7,Map.of(Onion,3)),
            new ItemInfo(Pie,Confections,MarineMerchandise,78,6,7,Map.of(Wheat,3)),
            new ItemInfo(CornFlakes,PreservedFood,Invalid,52,4,7,Map.of(Corn,2)),
            new ItemInfo(PickledRadish,PreservedFood,Invalid,104,8,7,Map.of(Radish,4)),
            new ItemInfo(IronAxe,Arms,Metalworks,72,8,8,null),
            new ItemInfo(QuartzRing,Accessories,UnburiedTreasures,72,8,8,null),
            new ItemInfo(PorcelainVase,Sundries,UnburiedTreasures,72,8,8,null),
            new ItemInfo(VegetableJuice,Concoctions,Invalid,78,6,8,Map.of(Cabbage,3)),
            new ItemInfo(PumpkinPudding,Confections,Invalid,78,6,8,Map.of(Pumpkin, 3, Egg, 1, Milk,1)),
            new ItemInfo(SheepfluffRug,Furnishings,CreatureCreations,90,6,8,Map.of(Fleece,3)),
            new ItemInfo(GardenScythe,Sundries,Metalworks,90,6,9,Map.of(Claw,3)),
            new ItemInfo(Bed,Furnishings,Textiles,120,8,9,Map.of(Fur,4)),
            new ItemInfo(ScaleFingers,Attire,CreatureCreations,120,8,9,Map.of(Carapace,4)),
            new ItemInfo(Crook,Arms,Woodworks,120,8,9,Map.of(Fang,4))};
    
    private int groove = 0;
    private int totalGross = 0;
    private int totalNet = 0;
    private static final int alternatives = 10;

    private final static ObjectMapper objectMapper = new ObjectMapper();
    public static int averageDayValue = 4044;
    public int rested = -1;
    private int islandRank = 10;
    public static double materialWeight = 0.5;
    private boolean guaranteeRestD5 = false;
    private Set<Item> reservedItems = new HashSet<>();
    private static boolean valuePerHour = true;
    private static int itemsToReserve = 15;

    private Set<Item> d2Troublemakers;

    private int week = 0;
    private int day = 0;

    private CSVImporter csvImporter;

    public Solver()
    {
        //objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            csvImporter = new CSVImporter();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Get ItemInfo from api?
        //Get supply value from api?
    }

    public List<DailyRecommendation> getRecommendationsForToday()
    {
        return getRecommendationsForToday(week, day, false);
    }
    public List<DailyRecommendation> getRecommendationsForToday(int week, int day, boolean hardRefresh)
    {
        if(hardRefresh || this.week != week)
        {
            rested = -1;
            groove = 0;
            reservedItems.clear();
            int currentPop = popularityRepository.findByWeek(week).getPopularity();
            String popResponse = restService.getURLResponse("https://xivapi.com/MJICraftworksPopularity/"+currentPop);

            PopularityJson popJson = null;
            try {
                popJson = objectMapper.readValue(popResponse, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            var peaks = peakRepository.findPeaksByDay(week, day);

            for(int i=0;i<items.length;i++)
            {
                items[i].setInitialData(popJson.getPopularities()[i].getRatio(), peaks.get(i).getPeakEnum());
            }

            //Load previous crafts from db
            for(int i=1; i<=day; i++)
            {
                CycleCraft crafts = craftRepository.findCraftsByDay(week, i);
                LOG.info("Found history for day {}: {}", i+1, crafts);
                if(crafts.getCraft1().isEmpty())
                {
                    LOG.info("Found rest day on day {}", i+1);
                    rested = i;
                }
                else
                {
                    for(int c=0; c<crafts.getCrafts().length; c++)
                    {
                        String name = crafts.getCrafts()[c];
                        if(name.isEmpty())
                            break;
                        groove+=NUM_WORKSHOPS;
                        int numToAdd = NUM_WORKSHOPS * 2;
                        if(c==0)
                        {
                            numToAdd = NUM_WORKSHOPS;
                            groove -= NUM_WORKSHOPS;
                        }
                        Item item = Item.valueOf(name);
                        items[item.ordinal()].setCrafted(numToAdd, i);
                    }
                }
                LOG.info("groove after day {}: {}", i+1, groove);
            }

            //sum total gross and total net

            this.day = day;
            this.week = week;
        }
        else if(this.day != day)
        {
            var peaks = peakRepository.findPeaksByDay(week,day);
            for(int i=0;i<items.length;i++)
            {
                items[i].peak = peaks.get(i).getPeakEnum();
            }
            this.day = day;
        }

        if(reservedItems.size() == 0)
            populateReservedItems();

        List<DailyRecommendation> listOfRecs = new ArrayList<>();

        if(day < 3)
        {
            DailyRecommendation rec;
            int dayToSolve = day+1;
            if(rested == dayToSolve)
                rested = -1;
            LOG.info("Solving recs for day {}",(dayToSolve+1));
            var todayRecs =  getBestBruteForceSchedules(dayToSolve, groove, null, dayToSolve, alternatives);
            var bestSchedule = todayRecs.get(0);
            boolean shouldRest = false;
            CycleSchedule schedule = new CycleSchedule(dayToSolve, groove);
            schedule.setForAllWorkshops(bestSchedule.getKey().getItems());
            int dailyValue = schedule.getValue();



            if(rested < 0) //If we haven't already rested, check to see if we should now
            {
                if(day < 2 && isWorseThanAllFollowing(bestSchedule, dayToSolve))
                    shouldRest = true;
                else if(day == 2)
                {
                    guaranteeRestD5 = false;
                    boolean worst = isWorseThanAllFollowing(bestSchedule,  dayToSolve, true);
                    if(worst)
                        shouldRest = true;
                    else if(guaranteeRestD5)
                    {
                        LOG.debug("Guaranteed resting D5 so recalculating D4");
                        todayRecs = getBestBruteForceSchedules(dayToSolve, groove,  null, dayToSolve + 1, alternatives);
                    }
                }
            }

            if(shouldRest)
            {
                LOG.info("Should rest");
                rec = new DailyRecommendation(dayToSolve, todayRecs);
            }
            else
            {
                rec = new DailyRecommendation(dayToSolve, todayRecs, schedule, dailyValue);
            }

            listOfRecs.add(rec);

            if(dayToSolve==1)
                rec.setTroublemakers(getTentativeD2Items(bestSchedule.getValue().getWeighted()));


            LOG.info("{}", rec);
        }
        else
        {
            //Try days 5-7
        }

        for(var dailyRec : listOfRecs)
        {
            if(dailyRec.isRestRecommended())
                rested = dailyRec.getDay();

            addCraftedFromCycle(dailyRec.getBestRec());
        }

        return listOfRecs;
    }

    private void addCraftedFromCycle(CycleSchedule schedule)
    {
        CycleCraft crafts = new CycleCraft();
        crafts.setCraftID(new CraftID(week, schedule.day));

        if(schedule != null)
        {
            if(schedule.numCrafted == null)
                schedule.getValue();
            schedule.numCrafted.forEach((k, v) -> items[k.ordinal()].setCrafted(v, schedule.day));
            var items = schedule.getItems();
            groove+=(items.size()-1) * NUM_WORKSHOPS;
            crafts.setCrafts(items);
        }

        craftRepository.save(crafts);
    }

    public void updatePeak(Item item, PeakCycle peak)
    {
        if(d2Troublemakers.contains(item))
        {
            d2Troublemakers.remove(item);
            items[item.ordinal()].peak = peak;
            CraftPeaks singlePeak = new CraftPeaks();
            singlePeak.setPeakFromEnum(peak);
            singlePeak.setPeakID(new PeakID(week, day, item.ordinal()+1));
            peakRepository.save(singlePeak);
        }
    }

    private void populateReservedItems()
    {
        Map<Item, Integer> itemValues = new HashMap<Item, Integer>();
        for(ItemInfo item : items)
        {
            if(item.time == 4)
                continue;
            int value = item.getValueWithSupply(Supply.Sufficient);
            if(valuePerHour)
                value = value * 8 / item.time;
            itemValues.put(item.item, value);
        }
        LinkedHashMap<Item,Integer> bestItems = itemValues
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> y, LinkedHashMap::new));
        Iterator<Entry<Item, Integer>> itemIterator = bestItems.entrySet().iterator();

        for(int i=0;i<itemsToReserve && itemIterator.hasNext(); i++)
        {
            Item next = itemIterator.next().getKey();
            //System.out.println("Reserving "+next);
            reservedItems.add(next);
        }
    }


    private void printRestDayInfo(List<Item> rec, int day)
    {
        CycleSchedule restedDay = new CycleSchedule(day, 0);
        restedDay.setForAllWorkshops(rec);
        LOG.info("Rest day " + (day + 1)+". Think we'll make more than "
                + restedDay.getValue() + " grooveless with " + rec + ". ");
    }

    /*private void setLateDays()
    {
        Entry<WorkshopSchedule, Integer> cycle5Sched = getBestSchedule(4, groove, null);
        Entry<WorkshopSchedule, Integer> cycle6Sched = getBestSchedule(5, groove, null);
        Entry<WorkshopSchedule, Integer> cycle7Sched = getBestSchedule(6, groove, null);

        // I'm just hardcoding this, This could almost certainly be improved

        List<Entry<WorkshopSchedule, Integer>> endOfWeekSchedules = new ArrayList<>();
        endOfWeekSchedules.add(cycle5Sched);
        endOfWeekSchedules.add(cycle6Sched);
        endOfWeekSchedules.add(cycle7Sched);

        int bestDay = -1;
        int bestDayValue = -1;
        int worstDay = -1;
        int worstDayValue = -1;

        for (int i = 0; i < 3; i++)
        {
            int value = endOfWeekSchedules.get(i).getValue();
            if (bestDay == -1 || value > bestDayValue)
            {
                bestDay = i + 4;
                bestDayValue = value;
            }
            if (worstDay == -1 || value < worstDayValue)
            {
                worstDay = i + 4;
                worstDayValue = value;
            }
        }

        if (bestDay == 4) // Day 5 is best
        {
            // System.out.println("Day 5 is best. Adding as-is");
            addDay(cycle5Sched.getKey().getItems(), 4);

            if (worstDay == 5)
            {
                // System.out.println("Recalcing day 7");
                Entry<WorkshopSchedule, Integer> recalcedCycle7Sched = getBestSchedule(6);

                // Day 6 is worst, so recalculate it according to day 7
                // System.out.println("Recalcing day 6 using only day 7's requirements
                // as verboten and adding");
                Map<Item,Integer> reserved7Set = recalcedCycle7Sched.getKey().getLimitedUses();
                Entry<WorkshopSchedule, Integer> recalced6Sched = getBestSchedule(5, groove,
                        reserved7Set, 6);
                if (rested)
                {
                    addDay(recalced6Sched.getKey().getItems(), 5);
                } 
                else
                {
                    printRestDayInfo(recalced6Sched.getKey().getItems(), 5);
                }
                // System.out.println("Adding day 7");
                addDay(recalcedCycle7Sched.getKey().getItems(), 6);
            } else
            {
                // System.out.println("Day 6 is second best, just recalcing and adding");
                addDay(getBestSchedule(5).getKey().getItems(), 5);

                Entry<WorkshopSchedule, Integer> recalced7Sched = getBestSchedule(6);
                if (rested)
                {
                    // System.out.println("Day 6 is second best, just recalcing and adding
                    // 7 too");
                    addDay(recalced7Sched.getKey().getItems(), 6);
                } else
                {
                    printRestDayInfo(recalced7Sched.getKey().getItems(), 6);
                }
            }
        } else if (bestDay == 6) // Day 7 is best
        {
            //System.out.println("Day 7 is best");
            Map<Item,Integer> reserved7Set = cycle7Sched.getKey().getLimitedUses();
            Entry<WorkshopSchedule, Integer> recalcedCycle6Sched = getBestSchedule(5, reserved7Set, 6);
            //System.out.println("Recalced 6:"+Arrays.toString(recalcedCycle6Sched.getKey().getItems().toArray())+" value:"+recalcedCycle6Sched.getValue());
            
            if(recalcedCycle6Sched.getValue()>cycle5Sched.getValue())
                worstDay = 4;
            else
                worstDay = 5;
            
            //System.out.println("Comparing to original 5: "+cycle5Sched.getValue()+". New worst day: "+worstDay);
            
            if (worstDay == 4 || rested) // Day 6 is second best or we're using all the
                                         // days anyway
            {
                 
                Map<Item,Integer> reserved67Items = recalcedCycle6Sched.getKey().getLimitedUses(reserved7Set);

                if (rested)
                {
                    Entry<WorkshopSchedule, Integer> recalcedCycle5Sched = getBestSchedule(
                            4, reserved67Items, 6);
                    //System.out.println("Recalced 5:"+Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())+" value:"+recalcedCycle5Sched.getValue());
                    //System.out.println("Recalcing 5 based on 6. Is it better?");

                    // Only use the recalculation if it doesn't ruin D5 too badly
                    if (recalcedCycle5Sched.getValue() + recalcedCycle6Sched
                            .getValue() > cycle5Sched.getValue() + cycle6Sched.getValue())
                    {
                        //System.out.println("It is! Using recalced 5");
                        addDay(recalcedCycle5Sched.getKey().getItems(), 4);
                    }

                    else
                    {
                        //This case is buggy. 
                        //System.out.println("It isn't, using original");
                        addDay(cycle5Sched.getKey().getItems(), 4);
                    }
                    //System.out.println("Recalcing 6 AGAIN just in case 5 changed it, still only forbidding things used day 7");
                    addDay(getBestSchedule(5, reserved7Set, 6).getKey().getItems(), 5);
                } else
                {
                    printRestDayInfo(cycle5Sched.getKey().getItems(), 4);
                    addDay(recalcedCycle6Sched.getKey().getItems(), 5);
                }
            }
            else if (worstDay == 5) // Day 5 is second best and we aren't using day 6
            {
                printRestDayInfo(recalcedCycle6Sched.getKey().getItems(), 5);
                //System.out.println("Day 6 isn't being used so just recalc 5 based on day 7");
                addDay(getBestSchedule(4, reserved7Set,6).getKey().getItems(), 4);
            }
             //System.out.println("Adding recalced day 7");
            addDay(getBestSchedule(6).getKey().getItems(), 6);

        } else // Best day is Day 6
        {
            // System.out.println("Day 6 is best");
            Map<Item,Integer> reserved6 = cycle6Sched.getKey().getLimitedUses();
         // System.out.println("Recalcing D5 allowing D6's items");
            Entry<WorkshopSchedule, Integer> recalcedCycle5Sched = getBestSchedule(4,
                    reserved6, 5);
            
            if(recalcedCycle5Sched.getValue() > cycle7Sched.getValue())
                worstDay = 6;
            else
                worstDay = 4;
            
            
            if (rested || worstDay != 4)
            {
                addDay(recalcedCycle5Sched.getKey().getItems(), 4);
            } else
                printRestDayInfo(cycle5Sched.getKey().getItems(), 4);
            // System.out.println("Recalcing day 6 and adding");
            addDay(getBestSchedule(5).getKey().getItems(), 5);

            Entry<WorkshopSchedule, Integer> recalcedCycle7Sched = getBestSchedule(6);
            if (rested || worstDay != 6)
            {
                // System.out.println("Recalcing day 7 and adding");
                addDay(recalcedCycle7Sched.getKey().getItems(), 6);
            } else
                printRestDayInfo(recalcedCycle7Sched.getKey().getItems(), 6);
        }
    }*/
    
    private boolean isWorseThanAllFollowing(Entry<WorkshopSchedule, WorkshopValue> rec,
            int day)
    {
        return isWorseThanAllFollowing(rec, day, false);
    }

    private boolean isWorseThanAllFollowing(Entry<WorkshopSchedule, WorkshopValue> rec,
            int day, boolean checkD5)
    {
        int worstInFuture = 99999;
        boolean bestD5IsWorst = true;
        int bestD5 = 0;
        LOG.debug("Comparing d" + (day + 1) + " (" + rec.getValue()+ ") to worst-case future days");
        
        Map<Item,Integer> reservedSet = new HashMap<Item,Integer>();
        for(Item item : rec.getKey().getItems())
            reservedSet.put(item, 0);
        for (int d = day + 1; d < 7; d++)
        {
            Entry<WorkshopSchedule, WorkshopValue> solution;
            if (day == 3 && d == 4) // We have a lot of info about this specific pair so
                                    // we might as well use it
            {
                solution = getD5EV();   
                bestD5 = solution.getValue().getWeighted();
            }
            else
                solution = getBestSchedule(d, groove, reservedSet);
            LOG.debug("Day " + (d + 1) + ", crafts: "
                        + Arrays.toString(solution.getKey().getItems().toArray())
                        + " value: " + solution.getValue());
            worstInFuture = Math.min(worstInFuture, solution.getValue().getWeighted());
            
            for(Item item : solution.getKey().getItems())
                reservedSet.put(item, 0);
            
            if (bestD5 > 0 && d > 4 && solution.getValue().getWeighted() < bestD5) //If we're checking a later day and it's worse than our best D5
                bestD5IsWorst = false;
        }
        //System.out.println("Best D5 "+bestD5+". Worst? "+bestD5IsWorst);
        if (checkD5 && day == 3 && bestD5IsWorst && rec.getValue().getWeighted() >= bestD5)
        {
            guaranteeRestD5 = true;
            LOG.debug("Best D5 "+bestD5+" is the worst value I can find, so recalcing D4 with its crafts too.");
        }
        
           // System.out.println("Worst future day: " + worstInFuture);

        return rec.getValue().getWeighted() <= worstInFuture;
    }

    // Specifically for comparing D4 to D5
    public Entry<WorkshopSchedule, WorkshopValue> getD5EV()
    {
        var solution = getBestSchedule(4, groove,null );
        LOG.trace("Testing against D5 solution " + solution.getKey().getItems());
        List<ItemInfo> c5Peaks = new ArrayList<>();
        for (Item item : solution.getKey().getItems())
            if (items[item.ordinal()].peak == Cycle5
                    && !c5Peaks.contains(items[item.ordinal()]))
                c5Peaks.add(items[item.ordinal()]);
        int sum = solution.getValue().getWeighted();
        int permutations = (int) Math.pow(2, c5Peaks.size());
        LOG.trace("C5 peaks: " + c5Peaks.size() + ", permutations: " + permutations);

        for (int p = 1; p < permutations; p++)
        {
            for (int i = 0; i < c5Peaks.size(); i++)
            {
                boolean strong = ((p) & (1 << i)) != 0; // I can't believe I'm using a
                                                        // bitwise and
                LOG.trace("Checking permutation " + p + " for item "+ c5Peaks.get(i).item + " " + (strong ? "strong" : "weak"));
                if (strong)
                    c5Peaks.get(i).peak = Cycle5Strong;
                else
                    c5Peaks.get(i).peak = Cycle5Weak;
            }

            int toAdd = solution.getKey().getValueWithGrooveEstimate(4, groove, rested >= 0).getWeighted();
            LOG.trace("Permutation " + p + " has value " + toAdd);
            sum += toAdd;
        }

        LOG.trace("Sum: " + sum + " average: " + sum / permutations);
        sum /= permutations;
        WorkshopValue value = new WorkshopValue(sum, 0, 0);
        solution.setValue(value);

        for (ItemInfo item : c5Peaks)
        {
            item.peak = Cycle5; // Set back to normal
        }

        return solution;
    }

    public boolean hasTentativeD2()
    {
        return d2Troublemakers != null && d2Troublemakers.size() > 0;
    }

    public Set<Item> getTentativeD2Items(int valueToCompare)
    {
        List<ItemInfo> c2Unknowns = new ArrayList<>();
        for (ItemInfo item : items)
            if (item.peak == Cycle2Unknown)
                c2Unknowns.add(item);

        LOG.info("Checking {} unknown D2 peaks.", c2Unknowns.size());

        d2Troublemakers = new HashSet<>();
        for (int i = 0; i < c2Unknowns.size(); i++)
        {
            c2Unknowns.get(i).peak = Cycle2Strong;
            LOG.info("Setting {} to strong peak", c2Unknowns.get(i).item);
            var schedule = getBestSchedule(1,0,null);
            if(schedule.getValue().getWeighted()>valueToCompare)
                d2Troublemakers.add(c2Unknowns.get(i).item);
            c2Unknowns.get(i).peak = Cycle2Unknown;
        }

        return d2Troublemakers;
    }

    private Map.Entry<WorkshopSchedule, WorkshopValue> getBestSchedule(int day, int groove, Map<Item,Integer> limitedUse)
    {
        var bestSchedule = getBestBruteForceSchedules(day, groove, limitedUse, day, 1).get(0);
        LOG.info("Best schedule for day {} with {} groove and {} limited items: {}", day, groove, limitedUse==null?0:limitedUse.size(), Arrays.toString(bestSchedule.getKey().getItems().toArray()));
        return bestSchedule;
    }

    private List<Map.Entry<WorkshopSchedule, WorkshopValue>> getBestBruteForceSchedules(int day, int groove,
            Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn)
    {

        HashMap<WorkshopSchedule, WorkshopValue> safeSchedules = new HashMap<>();
        List<List<Item>> filteredItemLists;


        filteredItemLists = csvImporter.allEfficientChains.stream()
                .filter(list -> list.stream().allMatch(
                        item -> items[item.ordinal()].rankUnlocked <= islandRank))
                .filter(list -> list.stream().allMatch(
                        item -> items[item.ordinal()].peaksOnOrBeforeDay(allowUpToDay, reservedItems)))
                .collect(Collectors.toList());



        for (List<Item> list : filteredItemLists)
        {
            addToScheduleMap(list, day, groove, limitedUse, safeSchedules);
        }

        var sortedSchedules = safeSchedules
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(numToReturn)
                .collect(Collectors.toList());


        return sortedSchedules;

    }
    private void addToScheduleMap(List<Item> list, int day, int groove, Map<Item,Integer> limitedUse,
            HashMap<WorkshopSchedule, WorkshopValue> safeSchedules)
    {
        WorkshopSchedule workshop = new WorkshopSchedule(list);
        if(workshop.usesTooMany(limitedUse))
            return;
        
        WorkshopValue value = workshop.getValueWithGrooveEstimate(day, groove, rested >= 0);
        // Only add if we don't already have one with this schedule or ours is better
        int oldValue = -1;
        if(safeSchedules.containsKey(workshop))
            oldValue = safeSchedules.get(workshop).getWeighted();

        if (oldValue < value.getWeighted())
        {
//            if (verboseSolverLogging && oldValue > 0)
//                System.out.println("Replacing schedule with mats "
//                        + workshop.rareMaterialsRequired + " with " + list + " because "
//                        + value + " is higher than " + oldValue);
            safeSchedules.remove(workshop); // It doesn't seem to update the key when
                                            // updating the value, so we delete the key
                                            // first
            safeSchedules.put(workshop, value);
        } else
        {
//            if (verboseSolverLogging)
//                System.out.println("Not replacing schedule with mats "
//                        + workshop.rareMaterialsRequired + " with " + list + " because "
//                        + value + " is lower than " + oldValue);

        }
    }



    public static Supply getSupplyBucket(int supply)
    {
        if (supply < -8)
            return Supply.Nonexistent;
        if (supply < 0)
            return Supply.Insufficient;
        if (supply < 8)
            return Supply.Sufficient;
        if (supply < 16)
            return Supply.Surplus;
        return Supply.Overflowing;
    }

    public static DemandShift getDemandShift(int prevSupply, int newSupply)
    {
        int diff = newSupply - prevSupply;
        if (diff < -5)
            return DemandShift.Skyrocketing;
        if (diff < -1)
            return DemandShift.Increasing;
        if (diff < 2)
            return DemandShift.None;
        if (diff < 6)
            return DemandShift.Decreasing;
        return DemandShift.Plummeting;
    }
}
