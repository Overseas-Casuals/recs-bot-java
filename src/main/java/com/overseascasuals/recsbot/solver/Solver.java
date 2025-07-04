package com.overseascasuals.recsbot.solver;

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
    static int getNumWorkshops(int rank)
    {
        if(rank >= 15)
            return 4;
        if (rank >= 5)
            return 3;
        return 2;
    }

    private static boolean sameDayUpdate = false;
    static int NUM_WORKSHOPS = 4;

    private static int averageWorkshopValue = 1123;
    public static int getAverageDayValue(int rank)
    {
        return averageWorkshopValue * getWorkshopBonus(rank) * getNumWorkshops(rank) / 100;
    }
    public static int maxIslandRank = 19;
    public static double materialWeight = 0.5;
    private static final int alternatives = 5;

    private static final int middayUpdateThreshold = 150;

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

    public final static List<Item> rareMatItems = Arrays.stream(items).filter(i -> i.materialsRequired != null).map(i -> i.item).collect(Collectors.toList());

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
    private int groove = 0;
    public int rested = -1;
    private boolean c5WorstFuture = false;
    private int c5AverageValue = 0;
    private final Set<Item> reservedItems = new HashSet<>();
    private final Map<Item, ReservedHelper> reservedHelpers = new HashMap<>();
    private final Map<Integer, List<CycleSchedule>> vacationRecs = new HashMap<>();

    public List<CycleSchedule> getVacationRecs (int rank)
    {
        for(int i=rank;i>=5;i--)
        {
            if(vacationRecs.containsKey(i))
                return vacationRecs.get(i);
        }
        return null;
    }

    private final Map<String, RestOfWeekRec> restOfWeek = new HashMap<>();

    private final Map<Integer, Integer> startingGroovePerDay = new HashMap<>();
    public int getStartingGroove(int day, int rank)
    {
        if(getNumWorkshops(rank) == getNumWorkshops(maxIslandRank))
            return startingGroovePerDay.get(day);
        else
            return startingGroovePerDay.get(day)/getNumWorkshops(maxIslandRank) * getNumWorkshops(rank);
    }

    private final Map<Integer, BruteForceSchedules> restOfDay = new HashMap<>();
    private final Map<Integer, Integer> hoursLeftInDay = new HashMap<>();
    private final Map<String, List<DailyRecommendation>> cachedAltRecs = new HashMap<>();
    public RestOfWeekRec fortuneTellerRecs;
    public int totalValue = 0;
    public int fortuneValue = 0;
    Map<Integer,ScheduleSet> dailySchedules = new HashMap<>();

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

    public List<ScheduleSet> getDailyRecommendations(int week, int day, boolean hardRefresh)
    {
        return getDailyRecommendations(week, day, hardRefresh, null);
    }
    public List<ScheduleSet> getDailyRecommendations(int week, int day, boolean hardRefresh, List<CraftPeaks> peaks)
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
            startingGroovePerDay.clear();
            startingGroovePerDay.put(0,0);
            startingGroovePerDay.put(1,0);
            rested = -1;
            groove = 0;
            c5WorstFuture = false;
            reservedItems.clear();
            reservedHelpers.clear();
            vacationRecs.clear();
            totalValue = 0;
            dailySchedules.clear();
            archiveRecs = null;
            this.day = 0; //Have it 0 while we generate vacation recs, then figure out what day it actually is

            int currentPop = generateVacationRecs(peakWeek);

            Integer[] popularities = csvImporter.popularityRatios[currentPop];
            for(int i=0;i<items.length&&i<peaks.size();i++)
            {
                items[i].setInitialData(popularities[i], peaks.get(i).getPeakEnum());

                LOG.info("Setting item {} to ratio {} and peak {}", items[i].item, items[i].popularityRatio, items[i].peak);
            }

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
                    rested = i;
                    dailySchedules.put(i, new ScheduleSet());
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
                            groove+=3;
                        }

                        itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);
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
                            groove++;
                        }

                        itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);
                    }
                    groove = Math.min(groove, getMaxGroove(maxIslandRank));
                    dailySchedules.put(i, new ScheduleSet(craftsAsItems, subcraftsAsItems));
                }
                LOG.info("groove after day {}: {}", i+1, groove);
                startingGroovePerDay.put(i+1, groove);
            }

            this.day = day;
            this.week = week;
        }


        List<ScheduleSet> listOfRecs = new ArrayList<>();

        restOfDay.clear();
        restOfWeek.clear();
        hoursLeftInDay.clear();
        cachedAltRecs.clear();

        populateReservedItems(day+1);

        //If we're on live and we already have a schedule for this day, make sure our state is accurate and move on
        if("live".equals(activeProfile) && dailySchedules.containsKey(Math.min(day + 1,6)))
        {
            setCraftedFromHistory();
            hasRunRecs = true;
            isRunningRecs = false;
            return listOfRecs;
        }
        int rank = maxIslandRank;
        groove = 0;

        //Get FT recs
        {
            clearDayUsage(List.of(1));
            int ftRank = maxIslandRank;
            var c3 = getBestBruteForceSchedules(2, 0,
                    null, 2, 1, ftRank);
            addCraftedFromCycle(2, c3.getBestRec(), ftRank, false);
            List<CycleSchedule> schedules = new ArrayList<>();
            schedules.add(c3.getBestRec());
            RestOfWeekRec restOfWeek = getRestOfWeekRecs(ftRank, 1, null);
            schedules.addAll(restOfWeek.getRecs());
            fortuneTellerRecs = new RestOfWeekRec(schedules, -1, true);

            int index = 2;
            for(var sched : schedules)
            {
                if("live".equals(activeProfile)) {
                    CycleCraft crafts = new CycleCraft();
                    crafts.setCraftID(new CraftID(week, index, -1));
                    crafts.setCrafts(sched.getItems());
                    crafts.setSubcrafts(sched.getSubItems());
                    craftRepository.save(crafts);
                }
                LOG.info("Fortuneteller rec for week {}, day {}: {}", week, index+1, sched);
                index++;
            }
            setCraftedFromHistory();
        }

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

            LOG.info("Found history for week {} day {}: {}", week-100, i + 1, crafts);
            if (crafts.getCraft1() == null || crafts.getCraft1().isEmpty())
            {
                LOG.info("Found rest day on day {}", i + 1);
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
        int previousTotal = getValueForWeek(previousRecs, maxIslandRank);

        for(int dayToSolve = 1; dayToSolve < 4; dayToSolve++)
        {
            var recs = getRecForSingleDay(dayToSolve, rank, null, true);

            if(recs == null || recs.size() == 0 || recs.get(0) == null)
            {
                LOG.error("Null rec returned for day {} and rank {}", dayToSolve, rank);
                return null;
            }
            var rec = recs.get(0);

            if(rec.isRestRecommended())
            {
                rested = dayToSolve;
                listOfRecs.add(new ScheduleSet());
            }
            else
            {
                listOfRecs.add(new ScheduleSet(rec.getBestRec().getItems(), rec.getBestRec().getSubItems()));
            }

            LOG.info("{}", rec);
            addCraftedFromCycle(rec.getDay(), rec.isRestRecommended()?null:rec.getBestRec(), rank, true);
        }
        //Add days 5-7
        var lateRecs = getRecForSingleDay(4, rank, null, true);
        for(var lateRec : lateRecs)
        {
            if(lateRec.isRestRecommended())
            {
                listOfRecs.add(new ScheduleSet());
            }
            else
            {
                listOfRecs.add(new ScheduleSet(lateRec.getBestRec().getItems(), lateRec.getBestRec().getSubItems()));
            }
        }

        LOG.info("Getting value for current week's recs");
        int todayValue = getValueForWeek(listOfRecs, maxIslandRank);

        if(previousTotal > todayValue)
        {
            LOG.info("We had a better schedule last time, just run that");
            listOfRecs = previousRecs;
        }

        totalValue = setArchiveValues(listOfRecs, maxIslandRank);


        //Idk if I need this?
        /*for(var rec : listOfRecs)
        {
            rec.getBestRec().setGrooveBonus(restedAlready(rec.getDay()), reservedHelpers);
        }*/

        LOG.info("Free heap memory: "+Runtime.getRuntime().freeMemory() +"/"+ Runtime.getRuntime().totalMemory());

        hasRunRecs = true;
        isRunningRecs = false;
        return listOfRecs;
    }

    private boolean restedAlready()
    {
        return restedAlready(day);
    }

    private boolean restedAlready(int today)
    {
        return rested > 0 && rested <= today;
    }

    private void setStrongRatios()
    {
        int weak=0;
        int strong=0;

        for(int i=0;i<50;i++)
        {
            if(items[i].peak == Cycle2Strong)
                strong++;
            else if(items[i].peak == Cycle2Weak)
                weak++;
        }
        strongRatio62 = (4.0-strong)/(8.0-weak-strong);
        LOG.info("Setting C2 strong ratio to {} ({}/4 strong and {}/4 weak)", strongRatio62, strong, weak);
    }

    private void setCraftedFromHistory()
    {
        for(int i=1; i<=6; i++)
        {
            ScheduleSet craftsAsItems = dailySchedules.get(i);
            List<ItemInfo> todaysItems = new ArrayList<>();
            clearDayUsage(List.of(i));
            if(craftsAsItems != null && craftsAsItems.size() > 0)
            {

                for(int c=0; c<craftsAsItems.size(); c++)
                {
                    Item item = craftsAsItems.items.get(c);
                    ItemInfo itemInfo = items[item.ordinal()];
                    todaysItems.add(itemInfo);
                    int numToAdd = 3;
                    if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                    {
                        numToAdd *= 2;
                    }

                    itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);
                }
                todaysItems.clear();
                for(int c=0; c<craftsAsItems.subItems.size(); c++)
                {
                    Item item = craftsAsItems.subItems.get(c);
                    ItemInfo itemInfo = items[item.ordinal()];
                    todaysItems.add(itemInfo);
                    int numToAdd = 1;
                    if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                    {
                        numToAdd *= 2;
                    }

                    itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);
                }
            }

        }
    }

    public static Item getBestLink(int hour, Item item)
    {
        return getBestLink(hour, item, null);
    }

    public static Item getBestLink(int hour, Item item1, Item item2)
    {
        Item bestLink = null;
        int bestValue = -1;
        for(var item : items)
        {
            if(item.time == hour && item.getsEfficiencyBonus(items[item1.ordinal()])
                    && (item2==null || item.getsEfficiencyBonus(items[item2.ordinal()]))
                    && item.getValueWithSupply(Supply.Sufficient)>bestValue)
            {
                bestLink = item.item;
                bestValue = item.getValueWithSupply(Supply.Sufficient);
            }
        }
        return bestLink;
    }

    private String getKeyForAltRequest(int dayToSolve, int rank, List<Item> items)
    {
        if(rank < 5)
            rank = 1;
        if(rank > maxIslandRank)
            rank = maxIslandRank;
        String key = dayToSolve+"-"+rank;

        if(items == rareMatItems)
            key+="-all";
        else if(items != null && items.size() > 0)
            key+="-"+items.stream().map(Item::toString).collect(Collectors.joining("-"));

        return key;
    }

    public List<DailyRecommendation> getRecForSingleDay(int dayToSolve, int rank, List<Item> limitedItems, boolean force)
    {
        return getRecForSingleDay(dayToSolve, rank, limitedItems, force, true);
    }
    public List<DailyRecommendation> getRecForSingleDay(int dayToSolve, int rank, List<Item> limitedItems, boolean force, boolean checkRest)
    {
        String cacheKey = getKeyForAltRequest(dayToSolve, rank, limitedItems);
        if(!force && cachedAltRecs.containsKey(cacheKey))
        {
            LOG.info("Found key {} in cache, returning", cacheKey);
            return cachedAltRecs.get(cacheKey);
        }


        HashMap<Item,Integer> limitedUse = null;
        if(limitedItems !=  null && limitedItems.size() > 0)
        {
            limitedUse = new HashMap<>();
            for(Item item: limitedItems)
                limitedUse.put(item, 0);
        }
        List<DailyRecommendation> recs = new ArrayList<>();
        LOG.info("Solving recs for day {}, rank {}",(dayToSolve+1), rank);

        if (dayToSolve == 4)
        {
            recs = getLateDays(rank, limitedUse);
        }
        else if(dayToSolve == 5)
        {
            recs = getLastTwoDays(rank, limitedUse);
        }
        else if(dayToSolve < 7)
        {
            DailyRecommendation rec;
            var todayRecs =  getBestBruteForceSchedules(dayToSolve, getStartingGroove(dayToSolve, rank),
                    limitedUse, dayToSolve, alternatives, rank);
            if(todayRecs == null || todayRecs.size() == 0)
                return null;
            var bestSchedule = todayRecs.getBestRec();
            boolean shouldRest = false;

            if(checkRest && !restedAlready(dayToSolve - 1)) //If we haven't already rested, check to see if we should now
            {
                if(day < 2 && isWorseThanAllFollowing(bestSchedule, dayToSolve, false, rank, limitedUse))
                    shouldRest = true;
                else if(day == 2)
                {
                    boolean worst = isWorseThanAllFollowing(bestSchedule,  dayToSolve, true, rank, limitedUse);
                    if(c5WorstFuture)
                    {
                        LOG.info("C5 is the worst future day, so seeing if C4+C5 is better than C4 alone");
                        var possibleRecs = getBestBruteForceSchedules(dayToSolve, getStartingGroove(dayToSolve, rank),  limitedUse, dayToSolve + 1, alternatives, rank);
                        int recalced4Value = possibleRecs.getBestRec().getWeightedValue();
                        if(recalced4Value > c5AverageValue)
                        {
                            todayRecs = possibleRecs;
                            bestSchedule = todayRecs.getBestRec();
                            LOG.info("It is! Using C4 schedule {} ({})", bestSchedule, bestSchedule.getWeightedValue());
                        }
                        else
                        {
                            LOG.info("Recalced C4 schedule {} ({}) is still worse. Resting.", possibleRecs.getBestRec(), recalced4Value);
                            shouldRest = true;
                        }
                    }
                    else if(worst)
                        shouldRest = true;
                    else
                    {
                        LOG.info("Can't guarantee resting C5 or C4");
                    }
                }
            }

            if(shouldRest)
                LOG.info("Should rest");

            rec = new DailyRecommendation(dayToSolve, rank, todayRecs, bestSchedule, shouldRest);
            recs.add(rec);
        }


        if(day+1>=Math.min(dayToSolve, 4)) //If we have the peaks for the day we're trying to solve
        {
            LOG.info("Caching results for "+cacheKey);
            cachedAltRecs.put(cacheKey, new ArrayList<>(recs));
        }

        return recs;
    }

    private void clearDayUsage(List<Integer> days)
    {
        for(ItemInfo item : items)
        {
            for(Integer day : days)
                item.clearCrafted(day);
        }
    }

    private void clearLateDayUsage()
    {
        clearDayUsage(List.of(4,5,6));
    }

    public void clearCache(String key)
    {
        cachedAltRecs.remove(key);
    }

    public int getValueForWeek(List<ScheduleSet> scheduleSets, int rank)
    {
        int previousGroove = groove;
        groove = 0;
        int total = 0;
        for(int day = 0; day < 6; day++)
        {
            CycleSchedule sched = new CycleSchedule(day+1, groove, rank);
            sched.setForFirstThreeWorkshops(scheduleSets.get(day).items);
            sched.setFourthWorkshop(scheduleSets.get(day).subItems);
            addCraftedFromCycle(day+1, sched, rank, false);
            int value = sched.getValue();
            LOG.info("Value for day {}: {}", day+2, value);
            total += value;
        }
        LOG.info("Total value for week: {}", total);
        setCraftedFromHistory();
        groove = previousGroove;
        return total;
    }
    public int setArchiveValues(List<ScheduleSet> thisWeekRecs, int rank)
    {
        archiveRecs = new ArrayList<>();
        int total = 0;
        groove = 0;
        for(int day = 0; day < 6; day++)
        {
            CycleSchedule sched = new CycleSchedule(day+1, 0, rank);
            sched.setForFirstThreeWorkshops(thisWeekRecs.get(day).items);
            sched.setFourthWorkshop(thisWeekRecs.get(day).subItems);
            int grooveless = sched.getValue();
            sched.setStartingGroove(groove);
            int today = sched.getValue();
            addCraftedFromCycle(day+1, sched, maxIslandRank, true);
            ArchiveSchedule rec = new ArchiveSchedule(sched.getItems(), sched.getSubItems(), grooveless, today, sched.getStartingGroove());
            archiveRecs.add(rec);
            if("live".equals(activeProfile))
            {
                CycleCraft crafts = new CycleCraft();
                crafts.setCraftID(new CraftID(week, day, rank));
                crafts.setCrafts(thisWeekRecs.get(day).items);
                crafts.setSubcrafts(thisWeekRecs.get(day).subItems);
                craftRepository.save(crafts);
                LOG.info("Saving crafts {} (sub {}) to db for week {}, day {}, and rank {}", thisWeekRecs.get(day).items, thisWeekRecs.get(day).subItems, week, day+2, rank);
            }
            LOG.info("Getting total for day {}, crafts {}, subcrafts {}: {} cowries", day+2, sched.getItems(), sched.getSubItems(), today);
            total += today;
        }
        LOG.info("Season total: {}", total);

        fortuneValue = 0;
        try
        {
            CycleSchedule crime2 = new CycleSchedule(1, 0, maxIslandRank);
            crime2.setForFirstThreeWorkshops(new ArrayList<>());
            crime2.setFourthWorkshop(new ArrayList<>());
            addCraftedFromCycle(1, crime2, maxIslandRank, false);
            for(int day=2; day<7; day++)
            {
                CycleSchedule sched = new CycleSchedule(day, groove, maxIslandRank);
                if(fortuneTellerRecs == null)
                {
                    LOG.info("Getting FT recs from database");
                    var crafts = craftRepository.findCraftsByDay(week, day, -1);
                    sched.setForFirstThreeWorkshops(crafts.getCrafts());
                    sched.setFourthWorkshop(crafts.getSubcrafts());
                }
                else
                {
                    LOG.info("Getting FT recs from local cache");
                    var cachedSched = fortuneTellerRecs.getRecs().get(day-2);
                    sched.setForFirstThreeWorkshops(cachedSched.getItems());
                    sched.setFourthWorkshop(cachedSched.getSubItems());
                }

                addCraftedFromCycle(day, sched, maxIslandRank, false);

                int today = sched.getValue();
                LOG.info("Getting FT total for day {}, starting groove {} crafts {}, subcrafts {}: {} cowries", day+1, sched.getStartingGroove(), sched.getItems(), sched.getSubItems(), today);
                fortuneValue += today;
            }
        }
        catch(Exception e)
        {
            fortuneValue = -1;
            LOG.error("Exception determining value of FT recs for week: ",e);
        }
        LOG.info("FT total: {}", fortuneValue);
        setCraftedFromHistory();

        return total;
    }

    private void addCraftedFromCycle(int day, CycleSchedule schedule, int rank, boolean real)
    {
        LOG.info("Setting info for cycle schedule {} rank {} (real? {})", schedule, rank, real);
        if(schedule!=null)
        {
            if(schedule.numCrafted == null)
                schedule.getValue();

            Arrays.stream(items).forEach(item -> item.setCrafted(schedule.numCrafted.getOrDefault(item.item, 0), schedule.day));

            groove = schedule.getEndingGroove();
            if(real && rank == maxIslandRank)
            {
                startingGroovePerDay.put(day+1, groove);
            }
        }
        else
        {
            Arrays.stream(items).forEach(item -> item.setCrafted(0, day));
            if(real && rank == maxIslandRank)
                startingGroovePerDay.put(day+1, startingGroovePerDay.get(day));
        }

        if(real)
        {
            List<Item> items;
            List<Item> subItems;
            if(schedule!=null)
            {
                subItems = schedule.getSubItems();
                items = schedule.getItems();
            }
            else
            {
                items = new ArrayList<>();
                subItems = new ArrayList<>();
            }

            if(rank == maxIslandRank)
                dailySchedules.put(day, new ScheduleSet(items, subItems));


            /*else
                LOG.info("Not saving crafts because we're running locally");*/
        }
        /*else
            LOG.info("Not saving crafts because we're just trying out values");*/
    }


    private void populateReservedItems(int day)
    {
        int resFullWeek = 16;
        int res45=6;
        int res67=8;
        int resSingle=4;

        reservedItems.clear();
        Map<ItemInfo, Integer> itemValues = new HashMap<>();
        for (ItemInfo item : items)
        {
            if (item.peaksOnOrBeforeDay(day, null))
                continue;
            int value = item.getValueWithSupply(Supply.Sufficient);
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
            if(day==1 && !next.getKey().peaksOnDay(1))
            {
               currFullWeek++;
               current = currFullWeek;
               cap = resFullWeek;
            }
            else if(day==2)
            {
                if(next.getKey().peaksOnDay(3) || next.getKey().peaksOnDay(4))
                {
                    curr45++;
                    current = curr45;
                    cap = res45;
                }
                else if(next.getKey().peaksOnDay(5) || next.getKey().peaksOnDay(6))
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
                if(next.getKey().peaksOnDay(4))
                {
                    curr5++;
                    current = curr5;
                    cap = resSingle;
                }
                else if(next.getKey().peaksOnDay(5) || next.getKey().peaksOnDay(6))
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
                if(next.getKey().peaksOnDay(5))
                {
                    curr6++;
                    current = curr6;
                    cap = resSingle;
                }
                else if(next.getKey().peaksOnDay(6))
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
                reservedItems.add(next.getKey().item);
            }
            if (current <= cap/2)
                itemsThatGetReservations.add(next.getKey().item);
        }

        reservedHelpers.clear();
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

                int value = helper.getValueWithSupply(Supply.Sufficient);
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

                reservedHelpers.put(itemEnum, new ReservedHelper(bestHelper, finalPenalty));
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

    private void addDailyRecToList(BruteForceSchedules recs, int day, int groove, int rank, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(day, groove, rank);
        bestSchedule.setForFirstThreeWorkshops(recs.getBestRec().getItems());
        bestSchedule.setFourthWorkshop(recs.getBestSubItems());
        addCraftedFromCycle(day, bestSchedule, rank, false);
        var newRec = new DailyRecommendation(day, rank, recs, bestSchedule);
        LOG.info("Adding late-week rec for C{} {} ({}): {}",day+1, bestSchedule.getItems(), bestSchedule.getSubItems(), bestSchedule.getValue());
        recommendations.add(newRec);
    }
    private void addRestToList(BruteForceSchedules recs, int day, int rank, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(day, groove, rank);
        bestSchedule.setForFirstThreeWorkshops(recs.get(0).getKey().getItems());
        bestSchedule.setFourthWorkshop(recs.getBestSubItems());
        addCraftedFromCycle(day, null, rank, false);
        var newRec = new DailyRecommendation(day, rank, recs, bestSchedule, true);
        LOG.info("Resting for late-week C{} ", day+1);
        recommendations.add(newRec);
    }
    public List<DailyRecommendation> getLateDays(int rank, Map<Item, Integer> limitedUse)
    {
        return getLateDays(rank, limitedUse, -1);
    }

    public List<DailyRecommendation> getLastTwoDays(int rank, Map<Item, Integer> limitedUse)
    {
        int startingGroove = getStartingGroove(5, rank);
        List<DailyRecommendation> recs = new ArrayList<>();

        var cycle6Sched = getBestBruteForceSchedules(5, startingGroove, limitedUse, 6, alternatives, rank);
        var cycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);

        if(restedAlready(4))
        {
            addCraftedFromCycle(5, cycle6Sched.getBestRec(), rank, false);
            int nextGroove6 = cycle6Sched.getBestRec().getEndingGroove();
            var recalced7Sched = getBestBruteForceSchedules(6, nextGroove6, limitedUse, 6, alternatives, rank);

            int basedOn6Total = cycle6Sched.getBestRec().getWeightedValue() + recalced7Sched.getBestRec().getWeightedValue();

            var newLimited = cycle7Sched.getBestRec().getLimitedUses(limitedUse);
            var recalced6Sched = getBestBruteForceSchedules(5, startingGroove, newLimited, 6, alternatives, rank);
            addCraftedFromCycle(5, recalced6Sched.getBestRec(), rank, false);
            int nextGroove7 = recalced6Sched.getBestRec().getEndingGroove();
            var updated7Sched = getBestBruteForceSchedules(6, nextGroove7, limitedUse, 6, alternatives, rank);

            int basedOn7Total = recalced6Sched.getBestRec().getWeightedValue() + updated7Sched.getBestRec().getWeightedValue();

            if(basedOn7Total > basedOn6Total)
            {
                LOG.info("7 > 6 {}: {} + {}\n{}: {} + {}", basedOn7Total,recalced6Sched.get(0),updated7Sched.get(0), basedOn6Total, cycle6Sched.get(0), recalced7Sched.get(0));
                addDailyRecToList(recalced6Sched, 5, startingGroove, rank, recs);
                addDailyRecToList(updated7Sched, 6, nextGroove7, rank, recs);
            }
            else
            {
                LOG.info("6 >= 7 {}: {} + {}\n{}: {} + {}",  basedOn6Total, cycle6Sched.get(0), recalced7Sched.get(0), basedOn7Total,recalced6Sched.get(0),updated7Sched.get(0));
                addDailyRecToList(cycle6Sched, 5, startingGroove, rank, recs);
                addDailyRecToList(recalced7Sched, 6, nextGroove6, rank, recs);
            }

        }
        else
        {
            if(cycle7Sched.getBestRec().getWeightedValue() > cycle6Sched.getBestRec().getWeightedValue())
            {
                var newLimited = cycle7Sched.getBestRec().getLimitedUses(limitedUse);
                addRestToList(getBestBruteForceSchedules(5, startingGroove, newLimited, 6, alternatives, rank), 5, rank, recs);
                addDailyRecToList(cycle7Sched, 6, startingGroove, rank, recs);
            }
            else
            {
                addDailyRecToList(cycle6Sched, 5, startingGroove, rank, recs);
                addRestToList(getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank), 6, rank, recs);
            }
        }
        return recs;
    }

    private int getTotalForRecs(List<DailyRecommendation> recs)
    {
        int total = 0;
        for(DailyRecommendation rec : recs)
        {
            if(!rec.isRestRecommended())
                total += rec.getDailyValue();
        }
        return total;
    }

    public List<DailyRecommendation> getLateDays(int rank, Map<Item, Integer> limitedUse, int startingGroove)
    {
        clearLateDayUsage();


        if(startingGroove == -1)
            startingGroove = getStartingGroove(4, rank);
        BruteForceSchedules cycle5Sched, cycle6Sched, cycle7Sched;

        // I'm just hardcoding this, This could almost certainly be improved
        List<DailyRecommendation> c5Recs = new ArrayList<>();
        //if (bestDay == 4) // Day 5 is best
        {
            cycle5Sched = getBestBruteForceSchedules(4, startingGroove, limitedUse, 6, alternatives, rank);
            LOG.info("Calcing based on c5");
            addDailyRecToList(cycle5Sched, 4, startingGroove, rank, c5Recs);

            int newStartingGroove = groove;
            cycle6Sched = getBestBruteForceSchedules(5, newStartingGroove, limitedUse, 6, alternatives, rank);
            cycle7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

            if(rested < 0 || rested >= 4)
            {
                //Haven't rested, need to pick 5 or 7
                if(cycle6Sched.getBestRec().getWeightedValue() > cycle7Sched.getBestRec().getWeightedValue())
                {
                    addDailyRecToList(cycle6Sched, 5, newStartingGroove, rank, c5Recs);
                    addRestToList(getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank), 6, rank, c5Recs);
                }
                else
                {
                    var newLimited = cycle7Sched.getBestRec().getLimitedUses(limitedUse);
                    addRestToList(getBestBruteForceSchedules(5, newStartingGroove, newLimited, 6, alternatives, rank), 5, rank, c5Recs);
                    addDailyRecToList(cycle7Sched, 6, newStartingGroove, rank, c5Recs);
                }
            }
            else //Using all 3
            {
                addCraftedFromCycle(5, cycle6Sched.getBestRec(), rank, false);
                var recalced7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

                var only6Sched = getBestBruteForceSchedules(5, newStartingGroove, limitedUse, 5, alternatives, rank);
                addCraftedFromCycle(5, only6Sched.getBestRec(), rank, false);
                var only7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

                if(cycle6Sched.getBestRec().getWeightedValue() + recalced7Sched.getBestRec().getWeightedValue() > only6Sched.getBestRec().getWeightedValue() + only7Sched.getBestRec().getWeightedValue())
                {
                    addDailyRecToList(cycle6Sched, 5, newStartingGroove, rank, c5Recs);
                    addDailyRecToList(recalced7Sched, 6, groove, rank, c5Recs);
                }
                else
                {
                    addDailyRecToList(only6Sched, 5, newStartingGroove, rank, c5Recs);
                    addDailyRecToList(only7Sched,6,groove, rank, c5Recs);
                }
            }
        }

        groove = startingGroove;
        clearLateDayUsage();
        List<DailyRecommendation> c7Recs = new ArrayList<>();
        //else if (bestDay == 6) // Day 7 is best
        {
            cycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);
            LOG.info("Calcing based on c7");
            Map<Item,Integer> reserved7Set = cycle7Sched.getBestRec().getLimitedUses(limitedUse);

            if(rested < 0 || rested >= 4)//We only care about one of 5 or 6
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 6, alternatives, rank);
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives, rank);

                if(cycle5Sched.getBestRec().getWeightedValue() > cycle6Sched.getBestRec().getWeightedValue())
                {
                    addDailyRecToList(cycle5Sched, 4, startingGroove, rank, c7Recs);
                    addRestToList(getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives, rank), 5, rank, c7Recs);
                }
                else
                {
                    var newLimited = cycle6Sched.getBestRec().getLimitedUses(reserved7Set);
                    addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 6, alternatives, rank), 4, rank, c7Recs);
                    addDailyRecToList(cycle6Sched, 5, startingGroove, rank, c7Recs);
                }
            }
            else
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 6, alternatives, rank);

                int total65 = 0;
                int grooveFrom5 = getGrooveMadeWithSchedule(new ScheduleSet(cycle5Sched.get(0).getKey().getItems(), cycle5Sched.getBestSubItems()));
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove + grooveFrom5, reserved7Set, 6, alternatives, rank);
                //try deriving 5 from 6
                Map<Item,Integer> reserved67Items = cycle6Sched.getBestRec().getLimitedUses(reserved7Set);
                var recalcedCycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved67Items, 6, alternatives, rank);

                total65 += recalcedCycle5Sched.getBestRec().getWeightedValue();
                addCraftedFromCycle(4, recalcedCycle5Sched.getBestRec(), rank, false);
                cycle6Sched = getBestBruteForceSchedules(5, groove, reserved7Set, 6, alternatives, rank);
                total65 += cycle6Sched.getBestRec().getWeightedValue();

                /*LOG.info("Derived 5 from 6 Total: {} (reserved {})\n6:{} ({}) {}\n5:{} ({}) {}", total65, reserved67Items, cycle6Sched.getBestRec().getItems(), cycle6Sched.getBestRec().getSubItems(),cycle6Sched.getBestRec().getWeightedValue(),
                        recalcedCycle5Sched.getBestRec().getItems(), recalcedCycle5Sched.getBestRec().getSubItems(),recalcedCycle5Sched.getBestRec().getWeightedValue());
*/
                //Try deriving 6 from 5
                int total56 = 0;

                total56 += cycle5Sched.getBestRec().getWeightedValue();
                addCraftedFromCycle(4, cycle5Sched.getBestRec(), rank, false);

                var basedOn56Sched = getBestBruteForceSchedules(5, groove, reserved7Set, 6, alternatives, rank);
                total56 += basedOn56Sched.getBestRec().getWeightedValue();

                /*LOG.info("Derived 6 from 5 Total: {}:\n5:{} ({}) {}\n6:{} ({}) {}", total56, cycle5Sched.getBestRec().getItems(), cycle5Sched.getBestRec().getSubItems(),cycle5Sched.getBestRec().getWeightedValue(),
                        basedOn56Sched.getBestRec().getItems(), basedOn56Sched.getBestRec().getSubItems(),basedOn56Sched.getBestRec().getWeightedValue());
*/
                if(total65 > total56)
                {
                    //System.out.println("Basing on 6 is better");
                    addDailyRecToList(recalcedCycle5Sched, 4, startingGroove, rank, c7Recs);
                    addDailyRecToList(cycle6Sched, 5, groove, rank, c7Recs);
                }
                else
                {
                    //System.out.println("Basing on 5 is better");
                    addDailyRecToList(cycle5Sched, 4, startingGroove, rank, c7Recs);
                    addDailyRecToList(basedOn56Sched, 5, groove, rank, c7Recs);
                }
            }

            addDailyRecToList(getBestBruteForceSchedules(6, groove, limitedUse, 6, alternatives, rank), 6, groove, rank, c7Recs);
        }
        groove = startingGroove;
        clearLateDayUsage();
        List<DailyRecommendation> c6Recs = new ArrayList<>();
        //else // Best day is Day 6
        {
            cycle6Sched = getBestBruteForceSchedules(5, startingGroove, limitedUse, 6, alternatives, rank);
            LOG.info("Calcing based on c6");
            addCraftedFromCycle(5, cycle6Sched.getBestRec(), rank, false);

            Map<Item,Integer> reserved6 = cycle6Sched.getBestRec().getLimitedUses(limitedUse);
            //System.out.println("Recalcing D5 allowing D6's items");

            var recalcedCycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved6, 6, alternatives, rank);
            var recalcedCycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);
            /*System.out.println("c5 sched:" +Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())+ " ("
                    +recalcedCycle5Sched.getValue()+") compared to c7: "+Arrays.toString(recalcedCycle7Sched.getKey().getItems().toArray())
            +" ("+recalcedCycle7Sched.getValue()+")");*/

            var onlyCycle6Sched = getBestBruteForceSchedules(5, startingGroove, limitedUse, 5, alternatives, rank);
            addCraftedFromCycle(5, onlyCycle6Sched.getBestRec(), rank, false);
            var onlyCycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);



            Map<Item,Integer> reservedOnly6 = onlyCycle6Sched.getBestRec().getLimitedUses(limitedUse);
            var onlyCycle5Sched = getBestBruteForceSchedules(4, startingGroove,
                    reservedOnly6, 6, alternatives, rank);

            if(rested < 0 || rested >= 4)
            {
                //We only care about either 5 or 7, not both
                int best56Combo = cycle6Sched.getBestRec().getWeightedValue() + recalcedCycle5Sched.getBestRec().getWeightedValue();
                int best67Combo = cycle6Sched.getBestRec().getWeightedValue() + recalcedCycle7Sched.getBestRec().getWeightedValue();
                int best76Combo = onlyCycle6Sched.getBestRec().getWeightedValue() + onlyCycle7Sched.getBestRec().getWeightedValue();

                int bestOverall = Math.max(best76Combo, Math.max(best67Combo, best56Combo));
                if(bestOverall == best56Combo)
                {
                    addDailyRecToList(recalcedCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(getBestBruteForceSchedules(5, groove, limitedUse, 6, alternatives, rank), 5, groove, rank, c6Recs);
                    addRestToList(getBestBruteForceSchedules(6, groove, limitedUse, 6, alternatives, rank), 6, rank, c6Recs);
                }
                else
                {
                    if(bestOverall == best67Combo)
                    {
                        var newLimited = cycle6Sched.getBestRec().getLimitedUses(limitedUse);
                        addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 6, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(cycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    else
                    {
                        var newLimited = onlyCycle6Sched.getBestRec().getLimitedUses(limitedUse);
                        addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 6, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(onlyCycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    addDailyRecToList(getBestBruteForceSchedules(6, groove, limitedUse, 6, alternatives, rank), 6, groove, rank, c6Recs);
                }
            }
            else //We're using all 3 days
            {
                if(cycle6Sched.getBestRec().getWeightedValue() + recalcedCycle5Sched.getBestRec().getWeightedValue() + recalcedCycle7Sched.getBestRec().getWeightedValue()
                        > onlyCycle5Sched.getBestRec().getWeightedValue() + onlyCycle6Sched.getBestRec().getWeightedValue() + onlyCycle7Sched.getBestRec().getWeightedValue())
                {
                    //Using 6 first
                    addDailyRecToList(recalcedCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(getBestBruteForceSchedules(5, groove, limitedUse, 6, alternatives, rank), 5, groove, rank, c6Recs);
                }
                else
                {
                    //6 takes too much from 7 so we just do it straight
                    addDailyRecToList(onlyCycle5Sched, 4, startingGroove, rank, c6Recs);
                    addDailyRecToList(getBestBruteForceSchedules(5, groove, limitedUse, 5, alternatives, rank), 5, groove, rank, c6Recs);
                }
                addDailyRecToList(getBestBruteForceSchedules(6, groove, limitedUse, 6, alternatives, rank), 6, groove, rank, c6Recs);
            }
        }

        int c5Value = getTotalForRecs(c5Recs);
        int c6Value = getTotalForRecs(c6Recs);
        int c7Value = getTotalForRecs(c7Recs);
        int bestValue = Math.max(c5Value, Math.max(c6Value, c7Value));

        LOG.info("Based on C5 total: {}, Based on C6 total: {}, Based on C7 total: {}", c5Value, c6Value, c7Value);
        if(bestValue == c5Value)
        {
            LOG.info("Recs based on C5 are best");
            return c5Recs;
        }
        else if(bestValue == c6Value)
        {
            LOG.info("Recs based on C6 are best");
            return c6Recs;
        }
        LOG.info("Recs based on C7 are best");
        return c7Recs;
    }

    private boolean isWorseThanAllFollowing(CycleSchedule rec,
            int day, boolean checkC5, int rank, Map<Item,Integer> limitedUse)
    {
        int groove = getStartingGroove(day, rank);
        int worstInFuture = 99999;
        c5WorstFuture = checkC5;
        c5AverageValue = 0;
        int weightedValue = rec.getWeightedValue();
        LOG.info("Comparing d{} rank {}: {} ({}) to worst-case future days", (day + 1), rank, rec, weightedValue);


        Map<Item,Integer> reservedSet = null;
        if(limitedUse != null)
            reservedSet = new HashMap<>(limitedUse);

        for (int d = day + 1; d < 7; d++)
        {
            CycleSchedule solution = getBestSchedule(d, groove, reservedSet, rank);
            if(solution == null)
            {
                LOG.error("Failed to get rest comparison for day {}. Abandoning rest checks.", d+1);
                return false;
            }

            int solutionValue = solution.getWeightedValue();
            if (day == 3 && d == 4) // We have a lot of info about this specific pair so
                                    // we might as well use it
            {
                c5AverageValue = getD5EV(solution);
                solutionValue = c5AverageValue;
                LOG.info("We're on C4, so compare to average value for C5 (instead of worst): "+c5AverageValue);
            }

            worstInFuture = Math.min(worstInFuture, solutionValue);
            reservedSet = solution.getLimitedUses(reservedSet);
            
            if (checkC5 && solutionValue < c5AverageValue) //If we're checking a later day and it's worse than our best D5
            {
                LOG.info("C{} ({}) worse than our C5 estimate ({}), so not guaranteed resting C5.", d+1, solutionValue, c5AverageValue);
                c5WorstFuture = false;
            }

        }
        LOG.info("Worst future day: {}", worstInFuture);
           // System.out.println("Worst future day: " + worstInFuture);

        return weightedValue <= worstInFuture;
    }

    // Specifically for comparing D4 to D5
    public int getD5EV(CycleSchedule solution)
    {
        LOG.trace("Testing against D5 solution " + solution);
        List<ItemInfo> c5Peaks = new ArrayList<>();
        for (Item item : solution.getItems())
            if (items[item.ordinal()].peak == Cycle5 && !c5Peaks.contains(items[item.ordinal()]))
                c5Peaks.add(items[item.ordinal()]);
        for (Item item : solution.getSubItems())
            if (items[item.ordinal()].peak == Cycle5 && !c5Peaks.contains(items[item.ordinal()]))
                c5Peaks.add(items[item.ordinal()]);


        int sum = solution.getWeightedValue();
        int permutations = (int) Math.pow(2, c5Peaks.size());
        LOG.trace("C5 peaks: " + c5Peaks.size() + ", permutations: " + permutations);

        for (int p = 1; p < permutations; p++)
        {
            for (int i = 0; i < c5Peaks.size(); i++)
            {
                boolean strong = ((p) & (1 << i)) != 0; // I can't believe I'm using a bitwise and
                LOG.trace("Checking permutation " + p + " for item "+ c5Peaks.get(i).item + " " + (strong ? "strong" : "weak"));
                if (strong)
                    c5Peaks.get(i).peak = Cycle5Strong;
                else
                    c5Peaks.get(i).peak = Cycle5Weak;
            }

            int toAdd = solution.getWeightedValue();
            LOG.trace("Permutation " + p + " has value " + toAdd);
            sum += toAdd;
        }

        LOG.trace("Sum: " + sum + " average: " + sum / permutations);
        sum /= permutations;

        for (ItemInfo item : c5Peaks)
        {
            item.peak = Cycle5; // Set back to normal
        }

        return sum;
    }

    public BruteForceSchedules getRestOfDayRecs(int day, int hoursLeft, int rank, Item startingItem)
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


        int startingGroove = getStartingGroove(day, rank);

        Map<Item, Integer> limitedItems = null;
        int lastDaySet = day+1;
        if(day >= 3)
            lastDaySet = 6;

        LOG.info("Reserving future crafts made through day {}", lastDaySet+1);


        for(int i=day+1; i<=lastDaySet; i++)
        {
            var crafts = dailySchedules.get(i);
            if(crafts == null)
            {
                lastDaySet = i-1;
                break;
            }
            LOG.info("Reserving future crafts for day {}: {}", i+1, crafts);
            limitedItems = new WorkshopSchedule(crafts.items, rank).getLimitedUses(limitedItems, false);
            limitedItems = new WorkshopSchedule(crafts.subItems, rank).getLimitedUses(limitedItems, true);
        }

        LOG.info("Getting rest of day schedules for day {} with groove {}, limited items {} through day {}",
                day, startingGroove, limitedItems, lastDaySet);
        var schedules = getBestBruteForceSchedules(day, startingGroove, limitedItems, lastDaySet, 5, startingItem, hoursLeft, rank);

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

    public RestOfWeekRec getThisWeekResult(int rank, List<Item> limitedItems)
    {
        if(rank > maxIslandRank)
            rank = maxIslandRank;

        int dayToSolve = day +1;

        String cacheKey = getKeyForAltRequest(dayToSolve, rank, limitedItems);

        if(restOfWeek.containsKey(cacheKey))
        {
            LOG.info("Returning rest of week from cache");
            return restOfWeek.get(cacheKey);
        }


        Map<Item,Integer> reservedSet = new HashMap<>();
        if(limitedItems!=null && limitedItems.size()>0)
        {
            for(Item i : limitedItems)
                reservedSet.put(i, 0);
        }


        RestOfWeekRec toReturn;
        if(dayToSolve < 4)
        {
            LOG.info("Solving rest of week with prediction");

            var singleDay = getRecForSingleDay(dayToSolve, rank, limitedItems, false);
            if(singleDay == null || singleDay.size() == 0)
                return null;

            var alt = singleDay.get(0);

            List<CycleSchedule> schedules = new ArrayList<>();
            schedules.add(alt.getBestRec());
            RestOfWeekRec restOfWeekRec = getRestOfWeekRecs(rank, day, limitedItems);
            schedules.addAll(restOfWeekRec.getRecs());
            toReturn = new RestOfWeekRec(schedules, alt.isRestRecommended()? 0 : restOfWeekRec.getWorstIndex()+1, restedAlready());
        }
        else
        {
            LOG.info("Solving rest of week with alts");
            var alts = getRecForSingleDay(dayToSolve, rank, limitedItems, false);
            int worstIndex = -1;
            List<CycleSchedule> schedules = new ArrayList<>();
            for(int i=0; i<alts.size(); i++)
            {
                if(alts.get(i).isRestRecommended())
                    worstIndex = i;
                schedules.add(alts.get(i).getBestRec());
            }
            toReturn = new RestOfWeekRec(schedules, worstIndex, worstIndex < 0);
        }

        restOfWeek.put(cacheKey, toReturn);

        return toReturn;

    }

    public RestOfWeekRec getRestOfWeekRecs(int rank, int day, List<Item> limitedItems)
    {
        if(rank > maxIslandRank)
            rank = maxIslandRank;

        Map<Item,Integer> reservedSet = new HashMap<>();
        if(limitedItems!=null && limitedItems.size()>0)
        {
            for(Item i : limitedItems)
                reservedSet.put(i, 0);
        }
        var restOfWeekRank = new ArrayList<CycleSchedule>();

        int worstIndex = -1;
        int worstValue = 99999;
        int estGroove = (groove + getMaxGroove(rank)) / 2;

        for (int d = day + 2; d < 7; d++)
        {
            var solution = getBestSchedule(d, estGroove, reservedSet, rank);
            if(solution==null)
            {
                LOG.error("Failed to generate rest of week recs for day {}", d+1);
                return null;
            }

            int value = solution.getWeightedValue();

            if(value <= worstValue) //Equals because we want to pick the latest day with the worst value
            {
                worstValue = value;
                worstIndex = restOfWeekRank.size();
            }

            restOfWeekRank.add(solution);
            reservedSet = solution.getLimitedUses(reservedSet);
        }

        for(var list : restOfWeekRank) {
            LOG.info("rest of week ({}): {}", rank, list);
        }
        var rec = new RestOfWeekRec(restOfWeekRank, worstIndex, rested > 0 && rested <= day + 1);

        return rec;
    }

    private int generateVacationRecs(int currentWeek)
    {
        //generate vacation recs
        var popData = popularityRepository.findByWeek(currentWeek);
        if(!"live".equals(activeProfile))
            return popData.getPopularity();

        LOG.info("Generating vacation recs");
        LOG.info("Getting popularity data for next week: {}", popData.getNextPopularity());
        int nextPop = popData.getNextPopularity();

        Integer[] popularities = csvImporter.popularityRatios[nextPop];

        int nextPeakWeek = (currentWeek+1 - 59) % 100 + 59; //159 should be 59. 201 should be 101. 378 should be 78

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

            items[i].setInitialData(ratio, peak);
        }

        int[] ranks = {5,9,11,15,18};

        if(nextWeekPeaks == null)
        {
            LOG.error("No nextWeekPeaks found for week {}. Using old next week algo.", currentWeek-99);
            for(int rank : ranks)
                vacationRecs.put(rank, vacationRecsHelper(rank));
        }
        else
        {
            LOG.info("Some peaks found, so generating next week schedules as if it were this week");
            for(int rank : ranks)
            {
                var c2 = getBestSchedule(1, 0, null, rank);
                addCraftedFromCycle(1, c2, rank, false);

                var recs = getRestOfWeekRecs(rank, 0,null);
                if(recs == null)
                    continue;

                List<CycleSchedule> schedules = recs.getRecs();
                schedules.set(recs.getWorstIndex(), null);
                schedules.add(0,c2);

                vacationRecs.put(rank, schedules);
            }
            groove = 0;
        }

        return popData.getPopularity();
    }

    private List<CycleSchedule> vacationRecsHelper(int rank)
    {
        Map<Item,Integer> reservedSet = null;
        var vacationRecs = new ArrayList<CycleSchedule>();
        for (int d = 0; d < 5; d++)
        {
            var schedule = getBestSchedule(3, getMaxGroove(rank)/2, reservedSet, rank);
            if(schedule == null)
            {
                LOG.error("Failed to get vacation schedule {} with day {}, groove {}, and reserved {}", d, 3, getMaxGroove(rank)/2, reservedSet);
                break;
            }
            vacationRecs.add(schedule);
            reservedSet = schedule.getLimitedUses(reservedSet);
        }
        //set sub schedules too
        return vacationRecs;
    }

    private CycleSchedule getBestSchedule(int day, int groove, Map<Item,Integer> limitedUse, int rank)
    {
        var schedules = getBestBruteForceSchedules(day, groove, limitedUse, day, 1, rank);
        if(schedules == null || schedules.size() == 0)
        {
            LOG.info("Best schedule for day {} rank {} with {} groove and {} limited items: null", day+1, rank, groove, limitedUse==null?0:limitedUse.size());
            return null;
        }
        var bestSchedule = schedules.getBestRec();
        LOG.info("Best schedule for day {} rank {} with {} groove and {} limited items: {} ({})", day+1, rank, groove, limitedUse==null?0:limitedUse.size(), bestSchedule, bestSchedule.getWeightedValue());
        return bestSchedule;
    }

    private BruteForceSchedules getBestBruteForceSchedules(int day, int groove,
                                            Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, int islandRank)
    {
        return getBestBruteForceSchedules(day, groove, limitedUse, allowUpToDay, numToReturn, null, 24, islandRank);
    }

    private Collection<List<Item>> getBruteForceSchedules(int day, int allowUpToDay, int islandRank)
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
                        item -> items[item.ordinal()].peaksOnOrBeforeDay(allowUpToDay, reservedItems)))
                .collect(Collectors.toList());
        //int numAfterFilter = filteredItemLists.size();

        //If it's a 6-craft, *something* has to peak today to make it worthwhile.
        if(day > 3)
            filteredItemLists = filteredItemLists.stream()
                    .filter(list -> list.size() < 6 || list.stream().anyMatch(
                            item -> items[item.ordinal()].peaksOnDay(day)))
                    .collect(Collectors.toList());
        //LOG.info("Removed {} 6-crafts from list for not having any items that peak today", numAfterFilter-filteredItemLists.size());

        return filteredItemLists;
    }

    private Collection<List<Item>> getGeneratedSchedules(int day, int allowUpToDay, int islandRank, Map<Item,Integer> limitedUse)
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

            if(!item.peaksOnOrBeforeDay(allowUpToDay, reservedItems) || item.rankUnlocked > islandRank)
                bucket = null;
            else if(limitedUse != null && limitedUse.containsKey(item.item) && limitedUse.get(item.item) == 0)
                bucket = null;

            if(bucket != null)
                bucket.add(item);
        }
        Comparator<ItemInfo> compareByValue = Comparator.comparingInt(o -> -1 * o.getValueWithSupply(o.getSupplyBucketOnDay(day)));
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

    private BruteForceSchedules getBestBruteForceSchedules(int day, int groove,
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
            filteredItemLists = getBruteForceSchedules(day, allowUpToDay, islandRank);

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
            filteredItemLists = getGeneratedSchedules(day, allowUpToDay, islandRank, limitedUse);
        }


        //LOG.info("Evaluating {} schedules for day {}", filteredItemLists.size(), day+1);
        for (List<Item> list : filteredItemLists)
        {
            addToScheduleMap(list, day, groove, islandRank, limitedUse, safeSchedules, semiSafeSchedules, false);
        }

        if(safeSchedules.size() == 0)
        {
            LOG.error("No valid schedules found after checking limitedUse {}", limitedUse);

            for (List<Item> list : filteredItemLists)
            {
                addToScheduleMap(list, day, groove, islandRank, limitedUse, safeSchedules, semiSafeSchedules, true);
            }

            if(safeSchedules.size() == 0)
                return null;
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

        BruteForceSchedules schedules = new BruteForceSchedules(sortedSchedules.stream().limit(numToReturn).collect(Collectors.toList()), day, groove);
        schedules.setBestSubItems(sortedSemiSafe, restedAlready(), reservedHelpers, islandRank);

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
    private void addToScheduleMap(List<Item> list, int day, int groove, int rank, Map<Item,Integer> limitedUse,
            HashMap<WorkshopSchedule, WorkshopValue> safeSchedules, Map<WorkshopSchedule, WorkshopValue> semiSafeSchedules, boolean verboseSolverLogging)
    {
        if(verboseSolverLogging)
            LOG.info("Checking schedule {} against {} safe schedules", list, safeSchedules.size());

        WorkshopSchedule workshop = new WorkshopSchedule(list, rank);
        if(workshop.usesTooMany(limitedUse, true, verboseSolverLogging))
        {
            if(verboseSolverLogging)
                LOG.info("Not using schedule {} because it uses too many limited use items {}", list, limitedUse);
            return;
        }

        if(!workshop.usesTooMany(limitedUse, false, verboseSolverLogging))
        {
            WorkshopValue mainValue = workshop.getValueWithGrooveEstimate(day, groove, restedAlready(), reservedHelpers, false);

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
        WorkshopValue subValue = workshop.getValueWithGrooveEstimate(day, groove, restedAlready(), reservedHelpers, true);
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
