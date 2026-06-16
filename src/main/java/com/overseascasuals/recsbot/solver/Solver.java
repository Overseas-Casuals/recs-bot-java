package com.overseascasuals.recsbot.solver;

import com.github.benmanes.caffeine.cache.*;
import com.overseascasuals.recsbot.data.*;
import com.overseascasuals.recsbot.mysql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.overseascasuals.recsbot.data.Item.*;
import static com.overseascasuals.recsbot.data.ItemCategory.*;
import static com.overseascasuals.recsbot.data.PeakCycle.*;
import static com.overseascasuals.recsbot.data.RareMaterial.*;

@Service
public class Solver
{
    Logger LOG = LoggerFactory.getLogger(Solver.class);
    LoadingCache<String, List<DailyRecommendation>> altCache = Caffeine.newBuilder().build(this::populateCacheForRecs);

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    CraftRepository craftRepository;
    static int getMaxGroove(int rank)
    {
        if(rank >= 15)
            return 45;
        if (rank >=9)
            return 35;
        if(rank >=7)
            return 25;
        if(rank >=5)
            return 20;
        return 15;
    }
    static int getWorkshopBonus(int rank)
    {
        if(rank>=19)
            return 140;
        if(rank>=14)
            return 130;
        if (rank>=8)
            return 120;
        if(rank>=6)
            return 110;
        return 100;
    }
    public static int getNumWorkshops(int rank)
    {
        if(rank >= 15)
            return 4;
        if (rank >= 5)
            return 3;
        return 2;
    }
    static int NUM_WORKSHOPS = 4;

    private static final int averageWorkshopValue = 1123;
    public static int getAverageDayValue(int rank)
    {
        return averageWorkshopValue * getWorkshopBonus(rank) * getNumWorkshops(rank) / 100;
    }
    public static int maxIslandRank = 19;
    public static double materialWeight = 0.5;
    private static final int alternatives = 5;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    static int prepeakBonus = 5;
    
    public final static ItemInfo[] items = {
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
            new ItemInfo(Item.SweetPopoto,Confections,Invalid,72,6,5,Map.of(Popoto, 2, Milk,1)),
            new ItemInfo(ParsnipSalad,Foodstuffs,Invalid,48,4,5,Map.of(Parsnip,2)),
            new ItemInfo(Caramels,Confections,Invalid,81,6,6,Map.of(Milk,2)),
            new ItemInfo(Ribbon,Accessories,Textiles,54,6,6,null),
            new ItemInfo(Rope,Sundries,Textiles,36,4,6,null),
            new ItemInfo(CavaliersHat,Attire,Textiles,81,6,6,Map.of(Feather,2)),
            new ItemInfo(Item.Horn,Sundries,CreatureCreations,81,6,6,Map.of(RareMaterial.Horn,2)),
            new ItemInfo(SaltCod,PreservedFood,MarineMerchandise,54,6,7,null),
            new ItemInfo(SquidInk,Ingredients,MarineMerchandise,36,4,7,null),
            new ItemInfo(EssentialDraught,Concoctions,MarineMerchandise,54,6,7,null),
            new ItemInfo(IsleberryJam,Ingredients,Invalid,78,6,7,Map.of(Isleberry,3)),
            new ItemInfo(TomatoRelish,Ingredients,Invalid,52,4,7,Map.of(Tomato,2)),
            new ItemInfo(OnionSoup,Foodstuffs,Invalid,78,6,7,Map.of(Onion,3)),
            new ItemInfo(IslefishPie,Confections,MarineMerchandise,78,6,7,Map.of(Wheat,3)),
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
            new ItemInfo(Crook,Arms,Woodworks,120,8,9,Map.of(Fang,4)),
            new ItemInfo(CoralSword,Arms,MarineMerchandise,72,8,10,null),
            new ItemInfo(CoconutJuice,Confections,Concoctions,36,4,10,null),
            new ItemInfo(Honey,Confections,Ingredients,36,4,10,null),
            new ItemInfo(SeashineOpal,UnburiedTreasures,Invalid,80,8,10,null),
            new ItemInfo(DriedFlowers,Sundries,Furnishings,54,6,10,null),
            new ItemInfo(PowderedPaprika,Ingredients,Concoctions,52,4,11,Map.of(Paprika,2)),
            new ItemInfo(CawlCennin,Concoctions,CreatureCreations,90,6,11,Map.of(Leek,3,Milk,1)),
            new ItemInfo(Isloaf,Foodstuffs,Concoctions,52,4,11,Map.of(Wheat,2)),
            new ItemInfo(PopotoSalad,Foodstuffs,Invalid,52,4,11,Map.of(Popoto,2)),
            new ItemInfo(Dressing,Ingredients,Invalid,52,4,11,Map.of(Onion,2)),
            new ItemInfo(Stove, Furnishings, Metalworks, 54, 6, 12, null),
            new ItemInfo(Lantern, Sundries, Invalid, 80, 8, 12, null),
            new ItemInfo(Natron, Sundries, Concoctions, 36, 4,12,null),
            new ItemInfo(Bouillabaisse, Foodstuffs, MarineMerchandise, 136, 8,12,Map.of(CaveShrimp, 2, Tomato, 2)),
            new ItemInfo(FossilDisplay, CreatureCreations, UnburiedTreasures, 54,6,13,null),
            new ItemInfo(Bathtub, Furnishings, UnburiedTreasures, 72, 8,13,null),
            new ItemInfo(Spectacles, Attire, Sundries, 54, 6,13,null),
            new ItemInfo(CoolingGlass, UnburiedTreasures, Invalid, 80, 8,13,null),
            new ItemInfo(RunnerBeanSaute, Foodstuffs, Invalid, 52, 4, 14,Map.of(RunnerBean, 2)),
            new ItemInfo(BeetSoup, Foodstuffs, Invalid, 78, 6, 14,Map.of(Beet, 3, Popoto, 1, Milk, 1)),
            new ItemInfo(ImamBayildi, Foodstuffs, Invalid, 90, 6, 14,Map.of(Eggplant, 2, Onion, 2, Tomato, 2)),
            new ItemInfo(PickledZucchini, PreservedFood, Invalid, 104, 8, 14,Map.of(Zucchini, 4)),
            new ItemInfo(BrassServingDish, Sundries, Metalworks, 36, 4, 16, null),
            new ItemInfo(GrindingWheel, Sundries, Invalid, 60, 6, 16, null),
            new ItemInfo(DuriumTathlums, Arms, Metalworks, 54, 6,17, null),
            new ItemInfo(GoldHairpin, Accessories, Metalworks, 72, 8,17, null),
            new ItemInfo(MammetAward, Furnishings, Invalid, 80, 8, 17, null),
            new ItemInfo(FruitPunch, Confections, Invalid, 52, 4, 18, Map.of(Watermelon, 1, Isleberry, 1)),
            new ItemInfo(SweetPopotoPie, Foodstuffs, Confections, 120, 8, 18, Map.of(RareMaterial.SweetPopoto, 3, Wheat, 1, Egg, 1)),
            new ItemInfo(Peperoncino, Foodstuffs, Invalid, 75, 6, 18, Map.of(Broccoli, 2, Wheat, 1)),
            new ItemInfo(BuffaloBeanSalad, Foodstuffs, CreatureCreations, 52, 4, 18, Map.of(BuffaloBeans, 2, Milk, 2)),
    };

    public final static Set<Item> rareMatItems = Arrays.stream(items).filter(i -> i.materialsRequired != null).map(i -> i.item).collect(Collectors.toUnmodifiableSet());

    public static int getNumItems(int week)
    {
        if(week < 20)
            return 50;
        return items.length;
    }

    public static int getHoursForItem(Item item)
    {
        return items[item.ordinal()].time;
    }

    public List<ArchiveSchedule> archiveRecs = null;
    public static CraftContext canonContext = null;
    public static CraftContext nextWeekContext = null;
    private final Map<Integer, BruteForceSchedules> restOfDay = new HashMap<>();
    private final Map<Integer, Integer> hoursLeftInDay = new HashMap<>();
    public int totalValue = 0;
    public static double strongRatio62 = 0;
    public static final double strongRatio63 = 0.5;

    private int week = 0;
    public int getWeek() {return week;}
    private int day = 0;
    public int getDay() {return day;}

    private CSVImporter csvImporter;

    public boolean hasRunRecs = false;
    public boolean isRunningRecs = false;
    public Solver()
    {
        try {
            csvImporter = new CSVImporter();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDailyRecommendations(int week, int day, boolean hardRefresh)
    {
        getDailyRecommendations(week, day, hardRefresh, null);
    }
    public List<ArchiveSchedule> getDailyRecommendations(int week, int day, boolean hardRefresh, List<CraftPeaks> peaks)
    {
        isRunningRecs = true;
        LOG.info("Getting recommendations for week {} day {}, hardrefresh? {}.", week, day, hardRefresh);
        int peakWeek = (week - 59) % 100 + 59; //159 should be 59. 201 should be 101. 378 should be 78

        if(peaks == null)
        {
            LOG.info("No peaks passed in, grabbing from DB");
            peaks = peakRepository.findPeaksByDay(peakWeek, 3);
            if(peaks.size() < items.length)
            {
                LOG.error("No peaks found in db for week {}.", peakWeek);
                return null;
            }
        }

        if(hardRefresh || this.week != week)
        {
            totalValue = 0;
            archiveRecs = null;
            canonContext = new CraftContext(week);
            this.day = 0; //Have it 0 while we generate vacation recs, then figure out what day it actually is

            int currentPop = generateVacationRecs(peakWeek, week);

            Integer[] popularities = csvImporter.popularityRatios[currentPop];
            for(int i=0;i<items.length&&i<peaks.size();i++)
            {
                canonContext.addInitialData(popularities[i], peaks.get(i).getPeakEnum());

                LOG.info("Setting item {} to ratio {} and peak {}", items[i].item, popularities[i], peaks.get(i).getPeakEnum());
            }

            if("live".equals(activeProfile) || day != 0)
            {
                //Load previous crafts from db
                for(int i=1; i<=6; i++)
                {
                    CycleCraft crafts = craftRepository.findCraftsByDay(week, i, maxIslandRank);
                    if(crafts == null)
                    {
                        LOG.info("No history found for day {}, assuming we need to run recs", i+1);
                        continue;
                    }

                    LOG.info("Found history for day {}: {}", i+1, crafts);
                    if(crafts.getCraft1() == null || crafts.getCraft1().isEmpty())
                    {
                        LOG.info("Found rest day on day {}", i+1);
                        canonContext.setRested(i);
                        canonContext.dailySchedules.put(i, new ScheduleSet());
                    }
                    else
                    {
                        List<ItemInfo> todaysItems = new ArrayList<>();
                        var craftsAsItems = crafts.getCrafts();
                        var subcraftsAsItems = crafts.getSubcrafts();
                        for(int c=0; c<craftsAsItems.size(); c++)
                        {
                            Item item = craftsAsItems.get(c);
                            ItemInfo itemInfo = items[item.ordinal()];
                            todaysItems.add(itemInfo);
                            int numToAdd = 3;
                            if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                            {
                                numToAdd = 6;
                                canonContext.addGroove(3);
                            }
                            canonContext.setCrafted(itemInfo.item, canonContext.getCraftedOnDay(itemInfo.item, i) + numToAdd, i);
                        }
                        todaysItems.clear();
                        for(int c=0; c<subcraftsAsItems.size(); c++)
                        {
                            Item item = subcraftsAsItems.get(c);
                            ItemInfo itemInfo = items[item.ordinal()];
                            todaysItems.add(itemInfo);
                            int numToAdd = 1;
                            if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                            {
                                numToAdd = 2;
                                canonContext.addGroove(1);
                            }

                            canonContext.setCrafted(itemInfo.item, canonContext.getCraftedOnDay(itemInfo.item, i) + numToAdd, i);
                        }
                        canonContext.setGroove(Math.min(canonContext.getGroove(), getMaxGroove(maxIslandRank)));
                        canonContext.dailySchedules.put(i, new ScheduleSet(craftsAsItems, subcraftsAsItems));
                    }
                    LOG.info("groove after day {}: {}", i+1, canonContext.getGroove());
                    canonContext.setStartingGroove(i+1, canonContext.getGroove());
                }
            }

            this.week = week;
        }
        this.day = day;

        List<ScheduleSet> listOfRecs = new ArrayList<>();

        restOfDay.clear();
        hoursLeftInDay.clear();
        altCache.invalidateAll();

        populateReservedItems(canonContext, day+1);

        //If we're on live and we already have a schedule for this day, make sure our state is accurate and move on
        if("live".equals(activeProfile) && canonContext.dailySchedules.containsKey(Math.min(day + 1,6)))
        {
            hasRunRecs = true;
            isRunningRecs = false;
            return null;
        }

        if(day == 0)
        {
            int rank = maxIslandRank;
            canonContext.setGroove(0);

            //Check 100 weeks' ago's recs
            List<ScheduleSet> previousRecs = new ArrayList<>();
            for(int i=1; i<=6; i++)
            {
                CycleCraft crafts = craftRepository.findCraftsByDay(week-100, i, maxIslandRank);
                if (crafts == null)
                {
                    LOG.info("No history found for week {} day {}, assuming we need to run recs", week-100, i + 1);
                    break;
                }

                LOG.info("Found previous history for week {} day {}: {}", week-100, i + 1, crafts);
                if (crafts.getCraft1() == null || crafts.getCraft1().isEmpty())
                {
                    LOG.info("Found previous rest day on day {}", i + 1);
                    previousRecs.add(new ScheduleSet());
                }
                else
                {
                    var craftsAsItems = crafts.getCrafts();
                    var subcraftsAsItems = crafts.getSubcrafts();
                    previousRecs.add(new ScheduleSet(craftsAsItems, subcraftsAsItems));
                }
            }
            LOG.info("Getting value for previous recs");
            int previousTotal = getValueForWeek(canonContext, previousRecs, maxIslandRank);

            LOG.info("Solving current week");
            var recs = getRecForDayOn(canonContext, 1, rank, null);

            if(recs == null || recs.size() == 0 || recs.get(0) == null)
            {
                LOG.error("Null rec returned for full week and rank {}", rank);
                return null;
            }

            for(var rec : recs)
            {
                if (rec.isRestRecommended())
                {
                    listOfRecs.add(new ScheduleSet());
                }
                else
                {
                    listOfRecs.add(new ScheduleSet(rec.getBestRec().getItems(), rec.getBestRec().getSubItems()));
                }

                LOG.info("C{}: {}", rec.getDay()+1, rec);
            }

            LOG.info("Getting value for current week's recs");
            int todayValue = getValueForWeek(canonContext, listOfRecs, maxIslandRank);

            if(previousTotal > todayValue)
            {
                LOG.info("We had a better schedule last time, just run that");
                listOfRecs = previousRecs;
            }
            totalValue = setArchiveValues(canonContext, listOfRecs, maxIslandRank);
        }

        LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());

        hasRunRecs = true;
        isRunningRecs = false;
        if(day==0)
            return archiveRecs;

        return null;
    }
    public static Item getBestLink(CraftContext context, int hour, Item item)
    {
        return getBestLink(context, hour, item, null);
    }

    public static Item getBestLink(CraftContext context, int hour, Item item1, Item item2)
    {
        Item bestLink = null;
        int bestValue = -1;
        for(var item : items)
        {
            if(item.time == hour && item.getsEfficiencyBonus(items[item1.ordinal()])
                    && (item2==null || item.getsEfficiencyBonus(items[item2.ordinal()]))
                    && item.getSufficientValue(context)>bestValue)
            {
                bestLink = item.item;
                bestValue = item.getSufficientValue(context);
            }
        }
        return bestLink;
    }

    private String getKeyForAltRequest(int week, int dayToSolve, int rank, String dayToRest, Set<Item> items)
    {
        if(rank < 5)
            rank = 1;
        if(rank > maxIslandRank)
            rank = maxIslandRank;
        String key = week+"-"+dayToSolve+"-"+rank+"-"+dayToRest;

        if(items == rareMatItems)
            key+="-all";
        else if(items != null && items.size() > 0)
            key+="-"+items.stream().map(Item::toString).collect(Collectors.joining("-"));

        return key;
    }

    public List<DailyRecommendation> getCachedRec(int week, int dayToSolve, int rank, int dayToRest, Set<Item> forbiddenItems)
    {
        String restStr = String.valueOf(dayToRest);
        if(dayToRest == -1)
            restStr = "X";
        String cacheKey = getKeyForAltRequest(week, dayToSolve, rank, restStr, forbiddenItems);
        
        return altCache.get(cacheKey);
    }

    private List<DailyRecommendation> populateCacheForRecs(String cacheKey)
    {
        LOG.info("Solving recs for key {}", cacheKey);

        var split = cacheKey.split("-");
        //First 4 are guaranteed
        int weekToSolve = Integer.parseInt(split[0]);
        int dayToSolve = Integer.parseInt(split[1]);
        int rank = Integer.parseInt(split[2]);
        String dayToRestStr = split[3];
        Set<Item> forbiddenItems = null;
        if(split.length>4)
        {
            forbiddenItems = new TreeSet<>();
            if(split[4].equals("all"))
                forbiddenItems = rareMatItems;
            else
            {
                for(int i=4; i< split.length; i++)
                {
                    forbiddenItems.add(Item.getEnum(split[i]));
                }
            }
        }
        CraftContext context;
        if(weekToSolve == week)
            context = new CraftContext(canonContext, dayToSolve-1);
        else
            context = new CraftContext(nextWeekContext, 0);

        if(!"X".equals(dayToRestStr))
        {
            context.setRested(Integer.parseInt(dayToRestStr));
        }

        LOG.info("Estimated size of cache: "+altCache.estimatedSize());
        return getRecForDayOn(context, dayToSolve, rank, forbiddenItems);
    }

    private List<DailyRecommendation> getRecForDayOn(CraftContext context, int dayToSolve, int rank, Set<Item> forbiddenItems)
    {
        Map<Integer, List<DailyRecommendation>> recsByRestDay = new HashMap<>();
        int bestDayToRest = -1;
        int bestValueWhenResting = -1;
        List<Integer> restDaysToCheck = new ArrayList<>();


        int rested = context.getRested();
        if(rested > 0)
        {
            LOG.info("We know our rest day. Don't worry too hard.");
            restDaysToCheck.add(rested);
        }
        else
        {
            for(int i=dayToSolve; i<7; i++)
                restDaysToCheck.add(i);
        }
        int initialDayToSolve = dayToSolve;

        for(int restDay : restDaysToCheck)
        {
            context.setRested(restDay);
            //LOG.info("Checking best weekly value if resting on C"+(restDay+1));
            dayToSolve = initialDayToSolve;
            List<DailyRecommendation> recs = new ArrayList<>();

            context.setGroove(context.getStartingGroove(dayToSolve, rank));

            while(dayToSolve < 7)
            {
                if (dayToSolve == 4)
                {
                    recs.addAll(getLateDays(context, rank, forbiddenItems, context.getGroove(), context.getRested()));
                    break;
                }
                if(dayToSolve == 5)
                {
                    recs.addAll(getLastTwoDays(context, rank, forbiddenItems, context.getGroove(), context.getRested()));
                    break;
                }

                DailyRecommendation rec;
                var todayRecs =  getBestBruteForceSchedules(context, dayToSolve, context.getGroove(), forbiddenItems,
                        null, dayToSolve, alternatives, rank);
                if(todayRecs == null || todayRecs.size() == 0)
                    return null;
                var bestSchedule = todayRecs.getBestRec();
                boolean shouldRest = dayToSolve == context.getRested();

                rec = new DailyRecommendation(dayToSolve, rank, todayRecs, bestSchedule, shouldRest);
                recs.add(rec);
                addCraftedFromCycle(context, rec.getDay(), rec.isRestRecommended()?null:rec.getBestRec());

                dayToSolve++;
            }

            recsByRestDay.put(restDay, recs);
            int value = getTotalForRecs(recs, false);
            LOG.info("Value when resting C"+(restDay+1)+": "+value);
            if(value > bestValueWhenResting)
            {
                //LOG.info("Value better than previous rest day, saving as best");
                bestValueWhenResting = value;
                bestDayToRest = restDay;
            }
        }

        context.setRested(bestDayToRest);

        LOG.info("Best day to rest is "+bestDayToRest);
        return recsByRestDay.get(bestDayToRest);
    }
    public void clearCache(String key)
    {
        altCache.invalidate(key);
    }

    public int getValueForWeek(CraftContext context, List<ScheduleSet> scheduleSets, int rank)
    {
        context.setGroove(0);
        int total = 0;
        for(int day = 0; day < 6; day++)
        {
            CycleSchedule sched = new CycleSchedule(context, day+1, context.getGroove(), rank);
            sched.setForFirstThreeWorkshops(scheduleSets.get(day).items);
            sched.setFourthWorkshop(scheduleSets.get(day).subItems);
            addCraftedFromCycle(context,day+1, sched);
            int value = sched.getValue();
            LOG.info("Value for day {}: {}", day+2, value);
            total += value;
        }
        LOG.info("Total value for week: {}", total);
        return total;
    }
    public int setArchiveValues(CraftContext context, List<ScheduleSet> thisWeekRecs, int rank)
    {
        archiveRecs = new ArrayList<>();
        int total = 0;
        context.setGroove(0);
        for(int day = 0; day < 6; day++)
        {
            CycleSchedule sched = new CycleSchedule(context, day+1, 0, rank);
            sched.setForFirstThreeWorkshops(thisWeekRecs.get(day).items);
            sched.setFourthWorkshop(thisWeekRecs.get(day).subItems);
            int groovelessValue = sched.getValue();
            sched.setStartingGroove(context.getGroove());
            int groovedValue = sched.getValue();

            addCraftedFromCycle(context, day+1, sched);
            context.dailySchedules.put(day+1, thisWeekRecs.get(day));

            LOG.info("Setting starting groove for C{} as {}", day+3, context.getGroove());
            context.setStartingGroove(day+2, context.getGroove());

            ArchiveSchedule rec = new ArchiveSchedule(sched.getItems(), sched.getSubItems(), groovelessValue, groovedValue, sched.getStartingGroove());
            archiveRecs.add(rec);
            if("live".equals(activeProfile))
            {
                CycleCraft crafts = new CycleCraft();
                crafts.setCraftID(new CraftID(week, day+1, rank));
                crafts.setCrafts(thisWeekRecs.get(day).items);
                crafts.setSubcrafts(thisWeekRecs.get(day).subItems);
                craftRepository.save(crafts);
                LOG.info("Saving crafts {} (sub {}) to db for week {}, day {}, and rank {}", thisWeekRecs.get(day).items, thisWeekRecs.get(day).subItems, week, day+2, rank);
            }
            LOG.info("Getting total for day {}, crafts {}, subcrafts {}: {} cowries", day+2, sched.getItems(), sched.getSubItems(), groovedValue);
            total += groovedValue;
        }
        LOG.info("Season total: {}", total);

        return total;
    }

    private void addCraftedFromCycle(CraftContext context, int day, CycleSchedule schedule)
    {
        //LOG.info("Setting info for cycle schedule {} rank {}", schedule, rank);
        if(schedule!=null)
        {
            if(schedule.numCrafted == null)
                schedule.getValue();

            Arrays.stream(items).forEach(item -> context.setCrafted(item.item, schedule.numCrafted.getOrDefault(item.item, 0), schedule.day));

            context.setGroove(schedule.getEndingGroove());
            context.setStartingGroove(day+1, context.getGroove());
        }
        else
        {
            Arrays.stream(items).forEach(item ->  context.setCrafted(item.item, 0, day));
            context.setStartingGroove(day+1, context.getStartingGroove(day));
        }
    }

    private void populateReservedItems(CraftContext context, int day)
    {
        int resFullWeek = 16;
        int res45=6;
        int res67=8;
        int resSingle=4;

        context.clearReserved();
        Map<ItemInfo, Integer> itemValues = new HashMap<>();
        for (ItemInfo item : items)
        {
            if (context.peaksOnOrBeforeDay(item.item, day))
                continue;
            int value = item.getSufficientValue(context);
            value = value * 8 / item.time;
            itemValues.put(item, value);
        }
        LinkedHashMap<ItemInfo, Integer> bestItems = itemValues
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> y, LinkedHashMap::new));
        var bestItemsEntries = bestItems.entrySet();
        List<Item> itemsThatGetReservations = new ArrayList<>();
        int currFullWeek = 0;
        int curr45 = 0;
        int curr67 = 0;
        int curr5 = 0;
        int curr6 = 0;
        int curr7 = 0;
        int current = 0;
        int cap = 0;
        for(var next : bestItemsEntries)
        {

            if(day==1 && !context.peaksOnDay(next.getKey().item, 1))
            {
               currFullWeek++;
               current = currFullWeek;
               cap = resFullWeek;
            }
            else if(day==2)
            {
                if(context.peaksOnDay(next.getKey().item, 3) || context.peaksOnDay(next.getKey().item, 4))
                {
                    curr45++;
                    current = curr45;
                    cap = res45;
                }
                else if(context.peaksOnDay(next.getKey().item, 5) || context.peaksOnDay(next.getKey().item, 6))
                {
                    curr67++;
                    current = curr67;
                    cap = res67;
                }
                else
                    cap=-1;
            }
            else if(day==3)
            {
                if(context.peaksOnDay(next.getKey().item, 4))
                {
                    curr5++;
                    current = curr5;
                    cap = resSingle;
                }
                else if(context.peaksOnDay(next.getKey().item, 5) || context.peaksOnDay(next.getKey().item, 6))
                {
                    curr67++;
                    current = curr67;
                    cap = res67;
                }
                else
                    cap = -1;
            }
            else if(day==4)
            {
                if(context.peaksOnDay(next.getKey().item, 5))
                {
                    curr6++;
                    current = curr6;
                    cap = resSingle;
                }
                else if(context.peaksOnDay(next.getKey().item, 6))
                {
                    curr7++;
                    current = curr7;
                    cap = resSingle;
                }
                else
                    cap = -1;
            }

            if(current <= cap)
            {
                LOG.info("Reserving item {} ({})", next.getKey().item, next.getValue());
                context.getReservedItems().add(next.getKey().item);
            }
            if (current <= cap/2)
                itemsThatGetReservations.add(next.getKey().item);
        }

        for (int i = 0; i < itemsThatGetReservations.size(); i++)
        {
            Item itemEnum = itemsThatGetReservations.get(i);
            ItemInfo mainItem = items[itemEnum.ordinal()];
            if (mainItem.time != 8)
                continue;
            int bestValue = 0;
            Item bestHelper = Macuahuitl; //This is the most useless thing I can think of
            int secondBest = 0;
            Item secondHelper = Macuahuitl;
            for (ItemInfo helper : items)
            {
                if (helper.time != 4 || !helper.getsEfficiencyBonus(mainItem))
                    continue;

                int value = helper.getSufficientValue(context);
                if (value > bestValue)
                {
                    secondBest = bestValue;
                    secondHelper = bestHelper;
                    bestValue = value;
                    bestHelper = helper.item;
                }
                else if (value > secondBest)
                {
                    secondBest = value;
                    secondHelper = helper.item;
                }
            }
            int swap = bestValue - secondBest;
            int stepDown = bestValue - (int) (bestValue * .6);
            if (swap > 0)
            {
                int penalty = Math.min(swap, stepDown);
                int finalPenalty = penalty / Math.max(i, 1) + 1;
                finalPenalty=Math.max((int)(finalPenalty*.3), 1); //Nerfing this hard since it doesn't seem to help
                LOG.info("Reserving helper " + bestHelper + " to go with main item " + itemEnum + " (#" + (i + 1) + "), difference between " + bestHelper + " and " + secondHelper + "? " + swap + " cost of stepping down? " + stepDown + " Penalty: " + finalPenalty);

                context.getReservedHelpers().put(itemEnum, new ReservedHelper(bestHelper, finalPenalty));
            }
        }
    }

    private int getGrooveMadeWithSchedule(ScheduleSet previous)
    {
        int effCrafts = 0;
        for(int i=1;i<previous.size();i++)
        {
            if(items[previous.items.get(i).ordinal()].getsEfficiencyBonus(items[previous.items.get(i-1).ordinal()]))
                effCrafts++;
        }

        effCrafts*=3;
        for(int i=1;i<previous.subItems.size();i++)
        {
            if(items[previous.subItems.get(i).ordinal()].getsEfficiencyBonus(items[previous.subItems.get(i-1).ordinal()]))
                effCrafts++;
        }

        return effCrafts;
    }

    private void addDailyRecToList(CraftContext context, BruteForceSchedules recs, int day, int groove, int rank, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(context, day, groove, rank);
        bestSchedule.setForFirstThreeWorkshops(recs.getBestRec().getItems());
        bestSchedule.setFourthWorkshop(recs.getBestSubItems());
        addCraftedFromCycle(context, day, bestSchedule);
        var newRec = new DailyRecommendation(day, rank, recs, bestSchedule);
        //LOG.info("Adding late-week rec for C{} {} ({}): {}",day+1, bestSchedule.getItems(), bestSchedule.getSubItems(), bestSchedule.getValue());
        recommendations.add(newRec);
    }
    private void addRestToList(CraftContext context, BruteForceSchedules recs, int day, int rank, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(context, day, context.getGroove(), rank);
        bestSchedule.setForFirstThreeWorkshops(recs.get(0).getKey().getItems());
        bestSchedule.setFourthWorkshop(recs.getBestSubItems());
        addCraftedFromCycle(context, day, null);
        var newRec = new DailyRecommendation(day, rank, recs, bestSchedule, true);
        //LOG.info("Resting for late-week C{} ", day+1);
        recommendations.add(newRec);
    }
    public List<DailyRecommendation> getLastTwoDays(CraftContext context, int rank, Set<Item> forbiddenItems, int startingGroove, int restDay)
    {
        List<DailyRecommendation> recs = new ArrayList<>();

        var cycle6Sched = getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, null, 6, alternatives, rank);
        var cycle7Sched = getBestBruteForceSchedules(context, 6, startingGroove, forbiddenItems, null, 6, alternatives, rank);

        if(restDay < 5) //Use both days
        {
            addCraftedFromCycle(context, 5, cycle6Sched.getBestRec());
            int nextGroove6 = cycle6Sched.getBestRec().getEndingGroove();
            var recalced7Sched = getBestBruteForceSchedules(context, 6, nextGroove6, forbiddenItems, null, 6, alternatives, rank);

            int basedOn6Total = cycle6Sched.getBestRec().getWeightedValue() + recalced7Sched.getBestRec().getWeightedValue();

            var newLimited = cycle7Sched.getBestRec().getLimitedUses(null);
            var recalced6Sched = getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, newLimited, 6, alternatives, rank);
            addCraftedFromCycle(context, 5, recalced6Sched.getBestRec());
            int nextGroove7 = recalced6Sched.getBestRec().getEndingGroove();
            var updated7Sched = getBestBruteForceSchedules(context, 6, nextGroove7, forbiddenItems, null, 6, alternatives, rank);

            int basedOn7Total = recalced6Sched.getBestRec().getWeightedValue() + updated7Sched.getBestRec().getWeightedValue();

            if(basedOn7Total > basedOn6Total)
            {
                //LOG.info("7 > 6 {}: {} + {}\n{}: {} + {}", basedOn7Total,recalced6Sched.get(0),updated7Sched.get(0), basedOn6Total, cycle6Sched.get(0), recalced7Sched.get(0));
                addDailyRecToList(context, recalced6Sched, 5, startingGroove, rank, recs);
                addDailyRecToList(context, updated7Sched, 6, nextGroove7, rank, recs);
            }
            else
            {
                //LOG.info("6 >= 7 {}: {} + {}\n{}: {} + {}",  basedOn6Total, cycle6Sched.get(0), recalced7Sched.get(0), basedOn7Total,recalced6Sched.get(0),updated7Sched.get(0));
                addDailyRecToList(context, cycle6Sched, 5, startingGroove, rank, recs);
                addDailyRecToList(context, recalced7Sched, 6, nextGroove6, rank, recs);
            }

        }
        else if(restDay == 5)
            {
                var newLimited = cycle7Sched.getBestRec().getLimitedUses(null);
                addRestToList(context, getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, newLimited, 6, alternatives, rank), 5, rank, recs);
                addDailyRecToList(context, cycle7Sched, 6, startingGroove, rank, recs);
            }
        else if(restDay == 6)
        {
            addDailyRecToList(context, cycle6Sched, 5, startingGroove, rank, recs);
            addRestToList(context, getBestBruteForceSchedules(context, 6, startingGroove, forbiddenItems, null, 6, alternatives, rank), 6, rank, recs);
        }

        return recs;
    }

    private int getTotalForRecs(List<DailyRecommendation> recs, boolean display)
    {
        int total = 0;
        for(DailyRecommendation rec : recs)
        {
            int value = rec.getDailyValue();

            if(!rec.isRestRecommended())
            {
                if(display)
                    LOG.info("Value for C"+(rec.getDay()+1)+" grooveless "+rec.getGroovelessValue()+", with "+rec.getBestRec().getStartingGroove()+" groove: "+value);

                total += value;
            }
            else if(display)
            {
                LOG.info("Value for C"+(rec.getDay()+1)+" grooveless "+rec.getGroovelessValue()+" (REST)");
            }
        }
        return total;
    }

    public List<DailyRecommendation> getLateDays(CraftContext context, int rank, Set<Item> forbiddenItems, int startingGroove, int restDay)
    {
        context.clearLateDayUsage();

        if(startingGroove == -1)
            startingGroove = context.getStartingGroove(4, rank);
        BruteForceSchedules cycle5Sched, cycle6Sched, cycle7Sched;

        // I'm just hardcoding this, This could almost certainly be improved
        List<DailyRecommendation> c5Recs = new ArrayList<>();
        if (restDay != 4) //Only calc on C5 if we aren't resting C5
        {
            cycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, null, 6, alternatives, rank);
            //LOG.info("Calcing based on c5");
            addDailyRecToList(context, cycle5Sched, 4, startingGroove, rank, c5Recs);

            int newStartingGroove = context.getGroove();
            cycle6Sched = getBestBruteForceSchedules(context, 5, newStartingGroove, forbiddenItems, null, 6, alternatives, rank);
            cycle7Sched = getBestBruteForceSchedules(context, 6, newStartingGroove, forbiddenItems, null, 6, alternatives, rank);

            if(restDay > 4) //Rested later
            {
                //Haven't rested, need to pick 6 or 7
                if(restDay==6)
                {
                    addDailyRecToList(context, cycle6Sched, 5, newStartingGroove, rank, c5Recs);
                    addRestToList(context, getBestBruteForceSchedules(context, 6, newStartingGroove, forbiddenItems, null, 6, alternatives, rank), 6, rank, c5Recs);
                }
                else if(restDay==5)
                {
                    var newLimited = cycle7Sched.getBestRec().getLimitedUses(null);
                    addRestToList(context, getBestBruteForceSchedules(context, 5, newStartingGroove, forbiddenItems, newLimited, 6, alternatives, rank), 5, rank, c5Recs);
                    addDailyRecToList(context, cycle7Sched, 6, newStartingGroove, rank, c5Recs);
                }
            }
            else //Rested earlier, using all 3
            {
                addCraftedFromCycle(context, 5, cycle6Sched.getBestRec());
                var recalced7Sched = getBestBruteForceSchedules(context, 6, newStartingGroove, forbiddenItems, null, 6, alternatives, rank);

                var only6Sched = getBestBruteForceSchedules(context, 5, newStartingGroove, forbiddenItems, null, 5, alternatives, rank);
                addCraftedFromCycle(context, 5, only6Sched.getBestRec());
                var only7Sched = getBestBruteForceSchedules(context, 6, newStartingGroove, forbiddenItems, null, 6, alternatives, rank);

                if(cycle6Sched.getBestRec().getWeightedValue() + recalced7Sched.getBestRec().getWeightedValue() > only6Sched.getBestRec().getWeightedValue() + only7Sched.getBestRec().getWeightedValue())
                {
                    addDailyRecToList(context, cycle6Sched, 5, newStartingGroove, rank, c5Recs);
                    addDailyRecToList(context, recalced7Sched, 6, context.getGroove(), rank, c5Recs);
                }
                else
                {
                    addDailyRecToList(context, only6Sched, 5, newStartingGroove, rank, c5Recs);
                    addDailyRecToList(context, only7Sched,6, context.getGroove(), rank, c5Recs);
                }
            }
        }

        context.setGroove(startingGroove);
        context.clearLateDayUsage();
        List<DailyRecommendation> c7Recs = new ArrayList<>();
        if (restDay != 6) // Only calc based on C7 if we aren't resting C7
        {
            cycle7Sched = getBestBruteForceSchedules(context, 6, startingGroove, forbiddenItems, null, 6, alternatives, rank);
            //LOG.info("Calcing based on c7");
            Map<Item,Integer> reserved7Set = cycle7Sched.getBestRec().getLimitedUses(null);

            if(restDay == 4 || restDay == 5) //We only care about one of 5 or 6
            {
                cycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, reserved7Set, 6, alternatives, rank);
                cycle6Sched = getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, reserved7Set, 6, alternatives, rank);

                if(restDay == 5)
                {
                    addDailyRecToList(context, cycle5Sched, 4, startingGroove, rank, c7Recs);
                    addRestToList(context, getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, reserved7Set, 6, alternatives, rank), 5, rank, c7Recs);
                }
                else //if(restDay == 4)
                {
                    var newLimited = cycle6Sched.getBestRec().getLimitedUses(reserved7Set);
                    addRestToList(context, getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, newLimited, 6, alternatives, rank), 4, rank, c7Recs);
                    addDailyRecToList(context, cycle6Sched, 5, startingGroove, rank, c7Recs);
                }
            }
            else //Rested earlier, using all 3 schedules
            {
                cycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, reserved7Set, 6, alternatives, rank);

                int total65 = 0;
                int grooveFrom5 = getGrooveMadeWithSchedule(new ScheduleSet(cycle5Sched.get(0).getKey().getItems(), cycle5Sched.getBestSubItems()));
                cycle6Sched = getBestBruteForceSchedules(context, 5, startingGroove + grooveFrom5, forbiddenItems, reserved7Set, 6, alternatives, rank);
                //try deriving 5 from 6
                Map<Item,Integer> reserved67Items = cycle6Sched.getBestRec().getLimitedUses(reserved7Set);
                var recalcedCycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, reserved67Items, 6, alternatives, rank);

                total65 += recalcedCycle5Sched.getBestRec().getWeightedValue();
                addCraftedFromCycle(context, 4, recalcedCycle5Sched.getBestRec());
                cycle6Sched = getBestBruteForceSchedules(context, 5, context.getGroove(), forbiddenItems, reserved7Set, 6, alternatives, rank);
                total65 += cycle6Sched.getBestRec().getWeightedValue();

                /*LOG.info("Derived 5 from 6 Total: {} (reserved {})\n6:{} ({}) {}\n5:{} ({}) {}", total65, reserved67Items, cycle6Sched.getBestRec().getItems(), cycle6Sched.getBestRec().getSubItems(),cycle6Sched.getBestRec().getWeightedValue(),
                        recalcedCycle5Sched.getBestRec().getItems(), recalcedCycle5Sched.getBestRec().getSubItems(),recalcedCycle5Sched.getBestRec().getWeightedValue());
*/
                //Try deriving 6 from 5
                int total56 = 0;

                total56 += cycle5Sched.getBestRec().getWeightedValue();
                addCraftedFromCycle(context, 4, cycle5Sched.getBestRec());

                var basedOn56Sched = getBestBruteForceSchedules(context, 5, context.getGroove(), forbiddenItems, reserved7Set, 6, alternatives, rank);
                total56 += basedOn56Sched.getBestRec().getWeightedValue();

                /*LOG.info("Derived 6 from 5 Total: {}:\n5:{} ({}) {}\n6:{} ({}) {}", total56, cycle5Sched.getBestRec().getItems(), cycle5Sched.getBestRec().getSubItems(),cycle5Sched.getBestRec().getWeightedValue(),
                        basedOn56Sched.getBestRec().getItems(), basedOn56Sched.getBestRec().getSubItems(),basedOn56Sched.getBestRec().getWeightedValue());
*/
                if(total65 > total56)
                {
                    //System.out.println("Basing on 6 is better");
                    addDailyRecToList(context, recalcedCycle5Sched, 4, startingGroove, rank, c7Recs);
                    addDailyRecToList(context, cycle6Sched, 5, context.getGroove(), rank, c7Recs);
                }
                else
                {
                    //System.out.println("Basing on 5 is better");
                    addDailyRecToList(context, cycle5Sched, 4, startingGroove, rank, c7Recs);
                    addDailyRecToList(context, basedOn56Sched, 5, context.getGroove(), rank, c7Recs);
                }
            }

            addDailyRecToList(context, getBestBruteForceSchedules(context, 6, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 6, context.getGroove(), rank, c7Recs);
        }
        context.setGroove(startingGroove);
        context.clearLateDayUsage();
        List<DailyRecommendation> c6Recs = new ArrayList<>();
        if(restDay != 5) // Only calc based on C6 if we aren't resting C6
        {
            cycle6Sched = getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, null, 6, alternatives, rank);
            //LOG.info("Calcing based on c6");
            addCraftedFromCycle(context, 5, cycle6Sched.getBestRec());

            Map<Item,Integer> reserved6 = cycle6Sched.getBestRec().getLimitedUses(null);
            //System.out.println("Recalcing D5 allowing D6's items");

            var recalcedCycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, reserved6, 6, alternatives, rank);
            var recalcedCycle7Sched = getBestBruteForceSchedules(context, 6, startingGroove, forbiddenItems, null, 6, alternatives, rank);
            /*System.out.println("c5 sched:" +Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())+ " ("
                    +recalcedCycle5Sched.getValue()+") compared to c7: "+Arrays.toString(recalcedCycle7Sched.getKey().getItems().toArray())
            +" ("+recalcedCycle7Sched.getValue()+")");*/

            var onlyCycle6Sched = getBestBruteForceSchedules(context, 5, startingGroove, forbiddenItems, null, 5, alternatives, rank);
            addCraftedFromCycle(context, 5, onlyCycle6Sched.getBestRec());
            var onlyCycle7Sched = getBestBruteForceSchedules(context, 6, startingGroove, forbiddenItems, null, 6, alternatives, rank);



            Map<Item,Integer> reservedOnly6 = onlyCycle6Sched.getBestRec().getLimitedUses(null);
            var onlyCycle5Sched = getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems,
                    reservedOnly6, 6, alternatives, rank);

            if(restDay == 4 || restDay == 6)
            {
                //We only care about either 5 or 7, not both

                if(restDay == 6) //Rested C7
                {
                    //Using best 5-6 combo
                    addDailyRecToList(context, recalcedCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(context, getBestBruteForceSchedules(context, 5, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 5, context.getGroove(), rank, c6Recs);
                    addRestToList(context, getBestBruteForceSchedules(context, 6, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 6, rank, c6Recs);
                }
                else //rested C5
                {
                    int best67Combo = cycle6Sched.getBestRec().getWeightedValue() + recalcedCycle7Sched.getBestRec().getWeightedValue();
                    int best76Combo = onlyCycle6Sched.getBestRec().getWeightedValue() + onlyCycle7Sched.getBestRec().getWeightedValue();

                    if(best67Combo > best76Combo)
                    {
                        var newLimited = cycle6Sched.getBestRec().getLimitedUses(null);
                        addRestToList(context, getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, newLimited, 6, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(context, cycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    else
                    {
                        var newLimited = onlyCycle6Sched.getBestRec().getLimitedUses(null);
                        addRestToList(context, getBestBruteForceSchedules(context, 4, startingGroove, forbiddenItems, newLimited, 6, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(context, onlyCycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    addDailyRecToList(context, getBestBruteForceSchedules(context, 6, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 6, context.getGroove(), rank, c6Recs);
                }
            }
            else //We're using all 3 days
            {
                if(cycle6Sched.getBestRec().getWeightedValue() + recalcedCycle5Sched.getBestRec().getWeightedValue() + recalcedCycle7Sched.getBestRec().getWeightedValue()
                        > onlyCycle5Sched.getBestRec().getWeightedValue() + onlyCycle6Sched.getBestRec().getWeightedValue() + onlyCycle7Sched.getBestRec().getWeightedValue())
                {
                    //Using 6 first
                    addDailyRecToList(context, recalcedCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(context, getBestBruteForceSchedules(context, 5, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 5, context.getGroove(), rank, c6Recs);
                }
                else
                {
                    //6 takes too much from 7 so we just do it straight
                    addDailyRecToList(context, onlyCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(context, getBestBruteForceSchedules(context, 5, context.getGroove(), forbiddenItems, null, 5, alternatives, rank), 5, context.getGroove(), rank, c6Recs);
                }
                addDailyRecToList(context, getBestBruteForceSchedules(context, 6, context.getGroove(), forbiddenItems, null, 6, alternatives, rank), 6, context.getGroove(), rank, c6Recs);
            }
        }

        int c5Value = getTotalForRecs(c5Recs, false);
        int c6Value = getTotalForRecs(c6Recs, false);
        int c7Value = getTotalForRecs(c7Recs, false);
        int bestValue = Math.max(c5Value, Math.max(c6Value, c7Value));

        //LOG.info("Based on C5 total: {}, Based on C6 total: {}, Based on C7 total: {}", c5Value, c6Value, c7Value);
        if(bestValue == c5Value)
        {
           // LOG.info("Recs based on C5 are best");
            return c5Recs;
        }
        else if(bestValue == c6Value)
        {
           // LOG.info("Recs based on C6 are best");
            return c6Recs;
        }
      //  LOG.info("Recs based on C7 are best");
        return c7Recs;
    }
    public BruteForceSchedules getRestOfDayRecs(CraftContext context, int day, int hoursLeft, int rank, Item startingItem)
    {
        if(rank > maxIslandRank)
            rank = maxIslandRank;
        LOG.info("Last day (hours) calculated: {} ({}). Searching for {} ({})", this.day, hoursLeftInDay.get(rank), day, hoursLeft);

        if(day == this.day && hoursLeftInDay.containsKey(rank) && hoursLeftInDay.get(rank) == hoursLeft && startingItem == null)
        {
            LOG.info("Returning rest of day recs from cache");
            return restOfDay.get(rank);
        }


        LOG.info("Recalculating today's recs");

        BruteForceSchedules restOfDayRank = null;


        int startingGroove = context.getStartingGroove(day, rank);

        Map<Item, Integer> limitedItems = null;
        int lastDaySet = day+1;
        if(day >= 3)
            lastDaySet = 6;

        LOG.info("Reserving future crafts made through day {}", lastDaySet+1);


        for(int i=day+1; i<=lastDaySet; i++)
        {
            var crafts = context.dailySchedules.get(i);
            if(crafts == null)
            {
                lastDaySet = i-1;
                break;
            }
            LOG.info("Reserving future crafts for day {}: {}", i+1, crafts);
            limitedItems = new WorkshopSchedule(context, crafts.items, rank).getLimitedUses(limitedItems, false);
            limitedItems = new WorkshopSchedule(context, crafts.subItems, rank).getLimitedUses(limitedItems, true);
        }

        LOG.info("Getting rest of day schedules for day {} with groove {}, limited items {} through day {}",
                day, startingGroove, limitedItems, lastDaySet);
        var schedules = getBestBruteForceSchedules(context, day, startingGroove, null, limitedItems, lastDaySet, 5, startingItem, hoursLeft, rank);

        if(schedules == null || schedules.size() == 0)
            return null;

        for(var schedule : schedules)
            LOG.info("Rest of day rec: {} ({})", schedule.getKey().getItems(), schedule.getValue().getWeighted());

        if(schedules.size() > 0 && schedules.get(0).getKey().getItems().size() > 0)
            restOfDayRank = schedules;

        if(day == this.day)
        {
            restOfDay.put(rank, restOfDayRank);
            hoursLeftInDay.put(rank, hoursLeft);
        }


        return restOfDayRank;
    }

    private int generateVacationRecs(int peakWeek, int realWeek)
    {
        //generate vacation recs
        var popData = popularityRepository.findByWeek(peakWeek);

        nextWeekContext = new CraftContext(realWeek+1);

        LOG.info("Getting popularity data for next week: {}", popData.getNextPopularity());
        int nextPop = popData.getNextPopularity();

        Integer[] popularities = csvImporter.popularityRatios[nextPop];

        int nextPeakWeek = (peakWeek+1 - 59) % 100 + 59; //159 should be 59. 201 should be 101. 378 should be 78

        List<CraftPeaks> nextWeekPeaks = null;

        LOG.info("Getting peak data for next week: {}", nextPeakWeek);
        nextWeekPeaks = peakRepository.findPeaksByDay(nextPeakWeek, 3);

        for(int i=0;i<items.length;i++)
        {
            int ratio = popularities[i];
            //LOG.info("Setting {} to initial data of {} and {}", items[i].item, ratio, Unknown);
            PeakCycle peak = Unknown;
            if(nextWeekPeaks!=null && nextWeekPeaks.size()>i)
                peak = nextWeekPeaks.get(i).getPeakEnum();

            nextWeekContext.addInitialData(ratio, peak);
        }

        return popData.getPopularity();
    }

    private BruteForceSchedules getBestBruteForceSchedules(CraftContext context, int day, int groove, Set<Item> forbiddenItems,
                                                           Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, int islandRank)
    {
        return getBestBruteForceSchedules(context, day, groove, forbiddenItems, limitedUse, allowUpToDay, numToReturn,  null, 24, islandRank);
    }

    private Collection<List<Item>> getBruteForceSchedules(CraftContext context, int day, int allowUpToDay, int islandRank)
    {
        Collection<List<Item>> filteredItemLists;

        if(csvImporter.allEfficientChains.size() == 0)
        {
            LOG.error("No efficient chains found in CSV importer. Reimporting");
            try{
                csvImporter = new CSVImporter();
            }
            catch(IOException e)
            {
                LOG.error("Failed to re-import efficient chain CSV");
            }
        }

        if(csvImporter.allEfficientChains == null || csvImporter.allEfficientChains.size() == 0)
        {
            LOG.error("Still no efficient chains found in CSV importer");
            return null;
        }



        filteredItemLists = csvImporter.allEfficientChains.stream()
                .filter(list -> list.stream().allMatch(
                        item -> items[item.ordinal()].rankUnlocked <= islandRank))
                .filter(list -> list.stream().allMatch(
                        item -> context.peaksOnOrBeforeDay(item, allowUpToDay)))
                .collect(Collectors.toList());
        //int numAfterFilter = filteredItemLists.size();

        //If it's a 6-craft, *something* has to peak today to make it worthwhile.
        if(day > 3)
            filteredItemLists = filteredItemLists.stream()
                    .filter(list -> list.size() < 6 || list.stream().anyMatch(
                            item -> context.peaksOnDay(item, day)))
                    .collect(Collectors.toList());
        //LOG.info("Removed {} 6-crafts from list for not having any items that peak today", numAfterFilter-filteredItemLists.size());

        return filteredItemLists;
    }

    private Collection<List<Item>> getGeneratedSchedules(CraftContext context, int day, int allowUpToDay, int islandRank, Set<Item> forbiddenItems)
    {
        Set<List<Item>> allEfficientChains = new HashSet<>();
        var fourHour = new ArrayList<ItemInfo>();
        var eightHour = new ArrayList<ItemInfo>();
        var sixHour = new ArrayList<ItemInfo>();

        int topEightsAllowed=5;
        int topSixesAllowed=4;
        int topFoursAllowed=3;

        int eightMatchesAllowed = 3;
        int sixMatchesAllowed = 3;
        int fourMatchesAllowed = 3;

        for (ItemInfo item : items)
        {
            List<ItemInfo> bucket = null;


            if (item.time == 4)
                bucket = fourHour;
            else if (item.time == 6)
                bucket = sixHour;
            else if (item.time == 8)
                bucket = eightHour;

            if(!context.peaksOnOrBeforeDay(item.item, allowUpToDay) || item.rankUnlocked > islandRank)
                bucket = null;
            else if(forbiddenItems != null && forbiddenItems.contains(item.item))
                bucket = null;

            if(bucket != null)
                bucket.add(item);
        }
        Comparator<ItemInfo> compareByValue = Comparator.comparingInt(o -> -1 * o.getValueOnDay(context, day));
        fourHour.sort(compareByValue);
        sixHour.sort(compareByValue);
        eightHour.sort(compareByValue);

        List<Item> four = new ArrayList<>();
        List<Item> eight = new ArrayList<>();
        //Find schedules based on 8-hour crafts
        for (int i=0; i<topEightsAllowed && i < eightHour.size(); i++)
        {
            var topItem = eightHour.get(i);
            List<ItemInfo> eightMatches = new ArrayList<>();
            //8-8-8

            for (ItemInfo eightMatchMatch : eightHour) {
                if (!eightMatchMatch.getsEfficiencyBonus(topItem))
                    continue;
                eightMatches.add(eightMatchMatch);
                allEfficientChains.add(List.of(topItem.item, eightMatchMatch.item, topItem.item));
            }

            //4-8-4-8 and 4-4-4-4-8
            int firstFourMatchCount = 0;
            for (ItemInfo firstFourMatch : fourHour)
            {
                if(firstFourMatchCount > fourMatchesAllowed)
                    break;
                if (!firstFourMatch.getsEfficiencyBonus(topItem))
                    continue;

                firstFourMatchCount++;
                //Add all efficient 4-8 pairs to parallel lists. We'll deal with them later
                four.add(firstFourMatch.item);
                eight.add(topItem.item);

                int secondFourMatchCount = 0;
                for (ItemInfo secondFourMatch : fourHour) {
                    if(secondFourMatchCount > fourMatchesAllowed)
                        break;
                    if (!secondFourMatch.getsEfficiencyBonus(firstFourMatch))
                        continue;

                    secondFourMatchCount++;

                    //4-4-8-8
                    for (var eightMatch : eightMatches)
                        allEfficientChains.add(List.of(secondFourMatch.item, firstFourMatch.item, topItem.item, eightMatch.item));

                    //4-4-4-4-8
                    int thirdFourMatchCount=0;
                    for (ItemInfo thirdFourMatch : fourHour) {
                        if(thirdFourMatchCount > fourMatchesAllowed)
                            break;
                        if (!secondFourMatch.getsEfficiencyBonus(thirdFourMatch))
                            continue;

                        thirdFourMatchCount++;

                        int fourthFourMatchCount = 0;
                        for (ItemInfo fourthFourMatch : fourHour)
                        {
                            if(fourthFourMatchCount > fourMatchesAllowed)
                                break;

                            if (fourthFourMatch.getsEfficiencyBonus(thirdFourMatch))
                            {
                                fourthFourMatchCount++;
                                allEfficientChains.add(List.of(fourthFourMatch.item, thirdFourMatch.item, secondFourMatch.item, firstFourMatch.item, topItem.item));
                            }
                        }
                    }
                }
            }


            int sixHourMatchCount = 0;
            for (ItemInfo sixHourMatch : sixHour)
            {
                if(sixHourMatchCount > sixMatchesAllowed)
                    break;
                if (!sixHourMatch.getsEfficiencyBonus(topItem))
                    continue;
                sixHourMatchCount++;

                //4-6-6-8
                int sixSixMatchCount = 0;
                for (ItemInfo sixSixMatch : sixHour)
                {
                    if(sixSixMatchCount > sixMatchesAllowed)
                        break;
                    if (!sixSixMatch.getsEfficiencyBonus(sixHourMatch))
                        continue;
                    sixSixMatchCount++;

                    int fourSixMatchCount = 0;
                    for(var fourSixMatch : fourHour)
                    {
                        if(fourSixMatchCount > fourMatchesAllowed)
                            break;
                        if(fourSixMatch.getsEfficiencyBonus(sixSixMatch))
                        {
                            fourSixMatchCount++;
                            allEfficientChains.add(List.of(fourSixMatch.item, sixSixMatch.item, sixHourMatch.item, topItem.item));
                        }
                    }
                    int fourEightMatchCount = 0;
                    for(var fourEightMatch : fourHour)
                    {
                        if(fourEightMatchCount > fourMatchesAllowed)
                            break;
                        if(fourEightMatch.getsEfficiencyBonus(topItem))
                        {
                            fourEightMatchCount++;
                            allEfficientChains.add(List.of(fourEightMatch.item, topItem.item, sixHourMatch.item, sixSixMatch.item));
                        }
                    }
                }

                //4-6-8-6
                int fourMatchCount = 0;
                for (ItemInfo fourMatch : fourHour)
                {
                    if(fourMatchCount > fourMatchesAllowed)
                        break;
                    if (!fourMatch.getsEfficiencyBonus(sixHourMatch))
                        continue;
                    fourMatchCount++;
                    int other6MatchCount = 0;
                    for(var other6Match : sixHour)
                    {
                        if(other6MatchCount > sixMatchesAllowed)
                            break;
                        if(other6Match.getsEfficiencyBonus(topItem))
                        {
                            other6MatchCount++;
                            allEfficientChains.add(List.of(fourMatch.item, sixHourMatch.item, topItem.item, other6Match.item));
                        }
                    }
                }
            }
        }

        for(int i=0; i<four.size(); i++)
        {
            for(int j=0; j<four.size(); j++)
            {
                allEfficientChains.add(List.of(four.get(i), eight.get(i), four.get(j), eight.get(j)));
            }
        }

        //Find schedules based on 6-hour crafts
        for (int i=0; i<topSixesAllowed && i < sixHour.size(); i++)
        {
            var topItem = sixHour.get(i);
            //6-6-6-6

            HashSet<ItemInfo> sixMatches = new HashSet<>();
            int sixMatchCount = 0;
            for (ItemInfo sixMatch : sixHour) {
                if(sixMatchCount > sixMatchesAllowed)
                    break;
                if (!sixMatch.getsEfficiencyBonus(topItem))
                    continue;
                sixMatchCount++;
                sixMatches.add(sixMatch);
            }
            for (ItemInfo firstSix : sixMatches)
            {
                for (ItemInfo secondSix : sixMatches)
                {
                    allEfficientChains.add(List.of( secondSix.item, topItem.item, firstSix.item, topItem.item ));
                }
            }

            int firstFourMatchCount = 0;
            for (ItemInfo firstFourMatch : fourHour)
            {
                if(firstFourMatchCount > fourMatchesAllowed)
                    break;
                if (!firstFourMatch.getsEfficiencyBonus(topItem))
                    continue;
                firstFourMatchCount++;
                for(var sixMatch : sixHour)
                {
                    if(!sixMatch.getsEfficiencyBonus(firstFourMatch))
                        continue;
                    int secondFourMatchCount = 0;
                    for (ItemInfo secondFourMatch : fourHour)
                    {
                        if(secondFourMatchCount > fourMatchesAllowed)
                            break;
                        if (!secondFourMatch.getsEfficiencyBonus(sixMatch))
                            continue;
                        secondFourMatchCount++;

                        //We'll let this one just go
                        for (ItemInfo thirdFourMatch : fourHour)
                        {
                            //4-4-6-4-6
                            if(thirdFourMatch.getsEfficiencyBonus(secondFourMatch))
                                allEfficientChains.add(List.of(thirdFourMatch.item, secondFourMatch.item, sixMatch.item, firstFourMatch.item, topItem.item));
                            //4-6-4-6-4
                            if(thirdFourMatch.getsEfficiencyBonus(topItem))
                                allEfficientChains.add(List.of(secondFourMatch.item, sixMatch.item, firstFourMatch.item, topItem.item, thirdFourMatch.item));
                        }
                    }
                }
            }

            //4-6-6-8
            int eightMatchCount = 0;
            for(var eightMatch : eightHour)
            {
                if(eightMatchCount > eightMatchesAllowed)
                    break;
                if(!eightMatch.getsEfficiencyBonus(topItem))
                    continue;
                eightMatchCount++;
                for (ItemInfo sixSixMatch : sixMatches)
                {
                    int fourSixMatchCount = 0;
                    for(var fourSixMatch : fourHour)
                    {
                        if(fourSixMatchCount > fourMatchesAllowed)
                            break;
                        if(fourSixMatch.getsEfficiencyBonus(sixSixMatch))
                        {
                            fourSixMatchCount++;
                            allEfficientChains.add(List.of(fourSixMatch.item, sixSixMatch.item, topItem.item, eightMatch.item));
                        }
                    }
                }
            }


            //4-6-8-6
            eightMatchCount = 0;
            for(var eightMatch : eightHour)
            {
                if(eightMatchCount > eightMatchesAllowed)
                    break;
                if(!eightMatch.getsEfficiencyBonus(topItem))
                    continue;
                eightMatchCount++;

                int sixEightMatchCount = 0;
                for(var sixEightMatch : sixHour)
                {
                    if(sixEightMatchCount > sixMatchesAllowed)
                        break;
                    if(!sixEightMatch.getsEfficiencyBonus(eightMatch))
                        continue;
                    sixEightMatchCount++;
                    int fourMatchCount = 0;
                    for (ItemInfo fourMatch : fourHour)
                    {
                        if(fourMatchCount > fourMatchesAllowed)
                            break;
                        if (fourMatch.getsEfficiencyBonus(sixEightMatch)) {
                            allEfficientChains.add(List.of(fourMatch.item, sixEightMatch.item, eightMatch.item, topItem.item));
                            fourMatchCount++;
                        }
                    }
                }
            }
        }

        for (int i=0; i<topFoursAllowed && i < fourHour.size(); i++)
        {
            var topItem = fourHour.get(i);
            int fourMatchCount = 0;
            for(var fourMatch : fourHour)
            {
                if(fourMatchCount > fourMatchesAllowed)
                    break;
                if(!fourMatch.getsEfficiencyBonus(topItem))
                    continue;
                fourMatchCount++;
                int secondFourMatchCount = 0;
                for(var secondFourMatch : fourHour)
                {
                    if(secondFourMatchCount > fourMatchesAllowed)
                        break;
                    if(!secondFourMatch.getsEfficiencyBonus(fourMatch))
                        continue;
                    secondFourMatchCount++;
                    int thirdFourMatchCount = 0;
                    for(var thirdFourMatch : fourHour)
                    {
                        if(thirdFourMatchCount > fourMatchesAllowed)
                            break;
                        if(!secondFourMatch.getsEfficiencyBonus(thirdFourMatch))
                            continue;
                        thirdFourMatchCount++;
                        int fourthFourMatchCount = 0;
                        for(var fourthFourMatch : fourHour)
                        {
                            if(fourthFourMatchCount > fourMatchesAllowed)
                                break;
                            if(!fourthFourMatch.getsEfficiencyBonus(thirdFourMatch))
                                continue;

                            fourthFourMatchCount++;
                            int fifthFourMatchCount = 0;
                            for(var fifthFourMatch : fourHour)
                            {
                                if (fifthFourMatchCount > fourMatchesAllowed)
                                    break;
                                if (fourthFourMatch.getsEfficiencyBonus(fifthFourMatch))
                                {
                                    fifthFourMatchCount++;
                                    allEfficientChains.add(List.of(fifthFourMatch.item, fourthFourMatch.item, thirdFourMatch.item, secondFourMatch.item, fourMatch.item,
                                            topItem.item));
                                }
                            }
                        }
                    }
                }
            }
        }

        return allEfficientChains;
    }

    private BruteForceSchedules getBestBruteForceSchedules(CraftContext context, int day, int groove, Set<Item> forbiddenItems,
                                                           Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, Item startingItem, int hoursLeft, int islandRank)
    {
        //long start = System.currentTimeMillis();
        if(numToReturn<2)
            numToReturn = 2; //We need at least 2 to do the 4th schedule thing

        /*LOG.info("Getting best schedule for day {}. groove {}. limitedUse {}, allowUpToDay {}, startingItem {}, hoursLeft {} and chains {}",
                day+1, groove, limitedUse, allowUpToDay, startingItem, hoursLeft, csvImporter.allEfficientChains.size());*/
        HashMap<WorkshopSchedule, WorkshopValue> safeSchedules = new HashMap<>();
        Map<WorkshopSchedule, WorkshopValue> semiSafeSchedules = new HashMap<>();
        if(groove > getMaxGroove(islandRank))
            groove = getMaxGroove(islandRank);

        Collection<List<Item>> filteredItemLists;

        if(startingItem != null || hoursLeft < 24)
        {
            filteredItemLists = getBruteForceSchedules(context, day, allowUpToDay, islandRank);

            if(filteredItemLists == null || filteredItemLists.size() == 0)
            {
                LOG.error("No valid schedules found after filtering by rank {} and peak day {}", islandRank, allowUpToDay+1);
                return null;
            }

            if(startingItem != null)
                filteredItemLists = filteredItemLists.stream().filter (list -> list.stream().limit(1)
                        .allMatch(item -> item == startingItem)).collect(Collectors.toList());

            if(filteredItemLists.size() == 0)
            {
                LOG.error("No valid schedules found after filtering by starting item {}",startingItem);
                return null;
            }


            if(hoursLeft < 24)
            {
                Set<List<Item>> smallLists = new HashSet<>();
                for (List<Item> schedule : filteredItemLists)
                {
                    List<Item> newSchedule = new ArrayList<>(schedule);
                    while (getHoursUsed(newSchedule) > hoursLeft && newSchedule.size() > 0)
                    {
                        newSchedule.remove(newSchedule.size() - 1);
                    }
                    if(newSchedule.size() > 0)
                        smallLists.add(newSchedule);
                }

                filteredItemLists = smallLists;
            }

            if(filteredItemLists.size() == 0)
            {
                LOG.warn("No valid schedules found after filtering by hours left {}", hoursLeft);
                return null;
            }
        }
        else
        {
            filteredItemLists = getGeneratedSchedules(context, day, allowUpToDay, islandRank, forbiddenItems);
        }

        //LOG.info("Evaluating {} schedules for day {}", filteredItemLists.size(), day+1);
        for (List<Item> list : filteredItemLists)
        {
            addToScheduleMap(context, list, day, groove, islandRank, limitedUse, safeSchedules, semiSafeSchedules, false);
        }

        if(safeSchedules.size() == 0)
        {
            LOG.error("No valid schedules found after checking limitedUse, ignoring");
            for (List<Item> list : filteredItemLists)
            {
                addToScheduleMap(context, list, day, groove, islandRank, null, safeSchedules, semiSafeSchedules, false);
            }

        }

        var sortedSchedules = safeSchedules
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());

        var sortedSemiSafe = semiSafeSchedules
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());

        List<Integer> redundantIndices = new ArrayList<>();
        var sets = new HashSet<Map<RareMaterial, Integer>>();

        for(int i=0; i<numToReturn * 3 && i<sortedSchedules.size(); i++)
        {
            WorkshopSchedule sched = sortedSchedules.get(i).getKey();
            if(sched.isItemSuperset(sets))
            {
                //LOG.info("Sched {} is redundant with rare mats {}", sched.getItems(), sched.rareMaterialsRequired);
                redundantIndices.add(i);
            }
            else
            {
                //LOG.info("Adding rare mats {} from highest-rated schedule {}", sched.rareMaterialsRequired, sched.getItems());
                sets.add(sched.rareMaterialsRequired);
            }

        }

        for(int j = redundantIndices.size() - 1; j >=0; j--)
        {
            //Remove from the end forward because the indices will change once you start removing
            int i = redundantIndices.get(j);
            var removed = sortedSchedules.remove(i);
            //LOG.info("Removed redundant schedule {}", removed.getKey().getItems());\
        }

        if(sortedSchedules.size() == 0)
        {
            LOG.error("No valid schedules found after reducing {} redundant schedules", redundantIndices.size());
            return null;
        }

        BruteForceSchedules schedules = new BruteForceSchedules(context, sortedSchedules.stream().limit(numToReturn).collect(Collectors.toList()), day, groove);
        schedules.setBestSubItems(sortedSemiSafe, context.restedAlready(day), context.getReservedHelpers(), islandRank);

        //LOG.info("Ran brute force schedules in "+(System.currentTimeMillis()-start)+"ms.");
        return schedules;
    }

    public List<Item> getItemsBetween(int hours, Item item1, Item item2)
    {
        if(hours == 10)
        {
            List<ItemInfo> item14Links = new ArrayList<>();
            List<ItemInfo> item16Links = new ArrayList<>();
            for(var item : items)
            {
                if(items[item1.ordinal()].getsEfficiencyBonus(item))
                {
                    if(item.time == 4)
                        item14Links.add(item);
                    else if(item.time==6)
                        item16Links.add(item);
                }
            }
            for(var item : item14Links)
            {
                for(var link : items)
                {
                    if(link.time == 6 && link.getsEfficiencyBonus(item) && link.getsEfficiencyBonus(items[item2.ordinal()]))
                        return List.of(item.item, link.item);
                }
            }
            for(var item : item16Links)
            {
                for(var link : items)
                {
                    if(link.time == 4 && link.getsEfficiencyBonus(item) && link.getsEfficiencyBonus(items[item2.ordinal()]))
                        return List.of(item.item, link.item);
                }
            }
        }
        return null;
    }
    private static int getHoursUsed(List<Item> schedule)
    {
        return schedule.stream().mapToInt(item -> items[item.ordinal()].time).sum();
    }
    private void addToScheduleMap(CraftContext context, List<Item> list, int day, int groove, int rank, Map<Item,Integer> limitedUse,
            HashMap<WorkshopSchedule, WorkshopValue> safeSchedules, Map<WorkshopSchedule, WorkshopValue> semiSafeSchedules, boolean verboseSolverLogging)
    {
        if(verboseSolverLogging)
            LOG.info("Checking schedule {} against {} safe schedules", list, safeSchedules.size());

        WorkshopSchedule workshop = new WorkshopSchedule(context, list, rank);
        if(workshop.usesTooMany(limitedUse, true, verboseSolverLogging))
        {
            if(verboseSolverLogging)
                LOG.info("Not using schedule {} because it uses too many limited use items {}", list, limitedUse);
            return;
        }

        if(!workshop.usesTooMany(limitedUse, false, verboseSolverLogging))
        {
            WorkshopValue mainValue = workshop.getValueWithGrooveEstimate(day, groove, context.restedAlready(day), context.getReservedHelpers(), false);

            if(verboseSolverLogging)
                LOG.info("Schedule has value {}", mainValue.getWeighted());

            // Only add if we don't already have one with this schedule or ours is better
            int oldValue = -99999;
            if(safeSchedules.containsKey(workshop))
                oldValue = safeSchedules.get(workshop).getWeighted();

            if (oldValue < mainValue.getWeighted())
            {
                if (verboseSolverLogging && oldValue > 0)
                    LOG.info("Replacing schedule with mats " + workshop.rareMaterialsRequired + " with " + list + " because " + mainValue.getWeighted() + " is higher than " + oldValue);

                safeSchedules.remove(workshop); // It doesn't seem to update the key when updating the value, so we delete the key first
                safeSchedules.put(workshop, mainValue);
            }
            else if(verboseSolverLogging)
            {
                LOG.info("Not replacing because old value {} is higher than {}", oldValue, mainValue.getWeighted());
            }
        }
        WorkshopValue subValue = workshop.getValueWithGrooveEstimate(day, groove, context.restedAlready(day), context.getReservedHelpers(),true);
        int oldSubValue = -99999;
        if(semiSafeSchedules.containsKey(workshop))
            oldSubValue = semiSafeSchedules.get(workshop).getWeighted();

        if (oldSubValue < subValue.getWeighted())
        {
            semiSafeSchedules.remove(workshop); // It doesn't seem to update the key when updating the value, so we delete the key first
            semiSafeSchedules.put(workshop, subValue);
        }
    }
}
