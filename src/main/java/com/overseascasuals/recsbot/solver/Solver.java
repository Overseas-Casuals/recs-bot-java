package com.overseascasuals.recsbot.solver;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.data.*;
import com.overseascasuals.recsbot.json.PopularityJson;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.mysql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
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
    RestService restService;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    CraftRepository craftRepository;

    static int WORKSHOP_BONUS;
    @Autowired
    private void setWorkshopBonus(@Value("${solver.island.workshopBonus}")int bonus) {WORKSHOP_BONUS = bonus;}
    static int GROOVE_MAX;
    @Autowired
    private void setMaxGroove(@Value("${solver.island.maxGroove}")int groove) {GROOVE_MAX = groove;}

    static int NUM_WORKSHOPS;
    @Autowired
    private void setNumWorkshops(@Value("${solver.island.workshops}")int numWorkshops) {NUM_WORKSHOPS = numWorkshops;}
    static int averageDayValue;
    @Autowired
    private void setAverageDayValue(@Value("${solver.averageDayValue}")int dayValue) {averageDayValue = dayValue;}
    private static int maxIslandRank;

    @Autowired
    private void setIslandRank(@Value("${solver.island.rank}")int rank) {maxIslandRank = rank;}
    public static double materialWeight;
    @Autowired
    private void setMaterialWeight(@Value("${solver.materialWeight}")double weight) {materialWeight = weight;}
    private static int alternatives;
    @Autowired
    private void setNumAlternatives(@Value("${solver.alternatives}")int numAlts) {alternatives = numAlts;}
    private static int itemsToReserve;
    @Autowired
    private void setItemsToReserve(@Value("${solver.itemsToReserve}")int items) {itemsToReserve = items;}

    private static int middayUpdateThreshold;
    @Autowired
    private void setMiddayUpdateThreshold(@Value("${solver.middayUpdateThreshold}")int threshold) {middayUpdateThreshold = threshold;}

    private static int chinaUpdateThreshold;
    @Autowired
    private void setChinaUpdateThreshold(@Value("${solver.chinaUpdateThreshold}")int threshold) {chinaUpdateThreshold = threshold;}

    private static boolean testC2Imposters;
    @Autowired
    private void setTestC2Imposters(@Value("${solver.testC2Imposters}")boolean test) {testC2Imposters = test;}
    private static boolean testC3Imposters;
    @Autowired
    private void setTestC3Imposters(@Value("${solver.testC3Imposters}")boolean test) {testC3Imposters = test;}

    @Value("${spring.profiles.active}")
    private String activeProfile;

    static int helperPenalty;
    @Autowired
    private void setHelperPenalty(@Value("${solver.reservedHelperPenalty}")int penalty) {helperPenalty = penalty;}
    
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
            new ItemInfo(Dressing,Ingredients,Invalid,52,4,11,Map.of(Onion,2))};

    public static int getNumItems()
    {
        return items.length;
    }

    public static int getHoursForItem(Item item)
    {
        return items[item.ordinal()].time;
    }
    
    private int groove = 0;

    private final static ObjectMapper objectMapper = new ObjectMapper();
    public int rested = -1;
    private boolean guaranteeRestD5 = false;
    private final Set<Item> reservedItems = new HashSet<>();
    private final Map<Item, ReservedHelper> reservedHelpers = new HashMap<>();
    private Map<Item, Boolean> d2Troublemakers = null;
    private Map<Item, Boolean> d3Troublemakers = null;
    private int confirmedD2Strong = 0;
    private int confirmedD2Weak = 0;
    private Set<Item> d2Bystanders;
    private final Map<Integer, List<List<Item>>> vacationRecs = new HashMap<>();

    public List<List<Item>> getVacationRecs (int rank) { return vacationRecs.get(rank);}

    private final Map<Integer, List<List<Item>>> restOfWeek = new HashMap<>();

    private final Map<Integer, Integer> startingGroovePerDay = new HashMap<>();

    private final Map<Integer, List<Entry<WorkshopSchedule, WorkshopValue>>> restOfDay = new HashMap<>();
    private final Map<Integer, Integer> hoursLeftInDay = new HashMap<>();
    private final Map<String, List<DailyRecommendation>> cachedAltRecs = new HashMap<>();
    public final List<List<DailyRecommendation>> crimeTimeRecs = new ArrayList<>();
    public int crimeTimeValue = 0;
    public int totalValue = 0;


    private boolean autocompletePeaks = false;
    private boolean allC3Set = false;

    private int week = 0;
    public int getWeek() {return week;}
    private int day = 0;
    public int getDay() {return day;}

    private CSVImporter csvImporter;

    public boolean hasRunRecs = false;
    public boolean isRunningRecs = false;
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

    public List<DailyRecommendation> redoDay2Recs()
    {
        return getDailyRecommendations(week, 0, false);
    }
    public List<DailyRecommendation> getDailyRecommendations(int week, int day, boolean hardRefresh)
    {
        return getDailyRecommendations(week, day, hardRefresh, null);
    }
    public List<DailyRecommendation> getDailyRecommendations(int week, int day, boolean hardRefresh, List<CraftPeaks> peaks)
    {
        isRunningRecs = true;
        LOG.info("Getting recommendations for week {} day {}, hardrefresh? {}. update threshold {}", week, day, hardRefresh, middayUpdateThreshold);



        if(peaks == null)
        {
            LOG.info("No peaks passed in, grabbing from DB");
            peaks = peakRepository.findPeaksByDay(week, Math.min(day,3));
            if(peaks.size() < items.length)
            {
                LOG.error("No peaks found in db for day {}.", day+1);
                return null;
            }
        }


        if(hardRefresh || this.week != week)
        {
            startingGroovePerDay.clear();
            startingGroovePerDay.put(0,0);
            startingGroovePerDay.put(1,0);
            autocompletePeaks = false;
            allC3Set = false;
            rested = -1;
            groove = 0;
            guaranteeRestD5 = false;
            reservedItems.clear();
            reservedHelpers.clear();
            d2Troublemakers = null;
            d2Bystanders = null;
            confirmedD2Weak = 0;
            confirmedD2Strong = 0;
            vacationRecs.clear();
            crimeTimeRecs.clear();
            crimeTimeValue = 0;
            totalValue = 0;

            int currentPop = generateVacationRecs(week);

            String popResponse;
            try{
                popResponse= restService.getURLResponse("https://xivapi.com/MJICraftworksPopularity/"+currentPop);
            }
            catch(RestClientException e)
            {
                LOG.error("Couldn't connect to XIV API to get popularity info. Abandoning ship", e);
                return null;
            }

            PopularityJson popJson;
            try {
                popJson = objectMapper.readValue(popResponse, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                LOG.error("Couldn't read pop json from XIV API", e);
                return null;
            }

            for(int i=0;i<items.length;i++)
            {
                items[i].popularityRatio = popJson.getPopularities()[i].getRatio();
                items[i].peak = peaks.get(i).getPeakEnum();

                LOG.info("Setting item {} to ratio {} and peak {}", items[i].item, items[i].popularityRatio, items[i].peak);
            }

            //Load previous crafts from db
            for(int i=1; i<=day; i++)
            {
                CycleCraft crafts = craftRepository.findCraftsByDay(week, i, maxIslandRank);
                if(crafts == null)
                    continue;
                LOG.info("Found history for day {}: {}", i+1, crafts);
                if(crafts.getCraft1() == null || crafts.getCraft1().isEmpty())
                {
                    LOG.info("Found rest day on day {}", i+1);
                    rested = i;
                }
                else
                {
                    List<ItemInfo> todaysItems = new ArrayList<>();
                    var craftsAsItems = crafts.getCrafts();
                    for(int c=0; c<craftsAsItems.size(); c++)
                    {
                        Item item = craftsAsItems.get(c);
                        ItemInfo itemInfo = items[item.ordinal()];
                        todaysItems.add(itemInfo);
                        int numToAdd = NUM_WORKSHOPS;
                        if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                        {
                            numToAdd = NUM_WORKSHOPS * 2;
                            groove+=NUM_WORKSHOPS;
                        }

                        itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);

                    }
                    groove = Math.min(groove, GROOVE_MAX);
                }
                LOG.info("groove after day {}: {}", i+1, groove);
                startingGroovePerDay.put(i+1, groove);
            }

            this.day = day;
            this.week = week;
        }
        else if(this.day != day)
        {
            for(int i=0;i<items.length;i++)
            {
                items[i].peak = peaks.get(i).getPeakEnum();
                LOG.info("Setting item {} to ratio {} and peak {}", items[i].item, items[i].popularityRatio, items[i].peak);
            }
            this.day = day;
        }


        List<DailyRecommendation> listOfRecs = new ArrayList<>();

        restOfDay.clear();
        restOfWeek.clear();
        hoursLeftInDay.clear();
        cachedAltRecs.clear();

        if((day == 1 || day == 2) && rested != day) //The only days when pre-peaks are unknown
        {
            populateReservedItems(day);

            for(int rank = 11; rank < 12; rank++)
            {
                List<Item> currentCrafts = craftRepository.findCraftsByDay(week, day, rank).getCrafts();
                if(currentCrafts == null || currentCrafts.size() == 0)
                    continue;

                int startingGroove =  groove - getGrooveMadeWithSchedule(currentCrafts);
                if(startingGroovePerDay.containsKey(day))
                {
                    startingGroove = startingGroovePerDay.get(day);
                }

                LOG.info("Rechecking day {}'s rank {} recs starting at {} groove with craft {}", day+1, rank, startingGroove, currentCrafts.get(0));


                WorkshopValue oldValue = new WorkshopSchedule(currentCrafts).getValueWithGrooveEstimate(day, startingGroove, restedAlready(), reservedHelpers);
                var newBest = getBestBruteForceSchedules(day, startingGroove,
                        null, day, 1, currentCrafts.get(0), 24, rank);

                int newValue = -1;
                List<Item> newCrafts = null;
                if(newBest != null && newBest.size() > 0)
                {
                    newCrafts = newBest.get(0).getKey().getItems();
                    newValue = newBest.get(0).getValue().getWeighted();
                }

                LOG.info("Old value for day {}: {}: ({}), new value {}: ({})", day+1, currentCrafts, oldValue.getWeighted(), newCrafts, newValue);

                if(newValue > oldValue.getWeighted())
                {
                    assert newBest != null;
                    LOG.info("Schedule updated detected for day {}! Now crafting {}", day+1,
                            Arrays.toString(newBest.get(0).getKey().getItems().toArray()));

                    CycleSchedule oldSched = new CycleSchedule(day, startingGroove);
                    CycleSchedule newSched = new CycleSchedule(day, startingGroove);
                    newSched.setForAllWorkshops(newCrafts);
                    oldSched.setForAllWorkshops(currentCrafts);
                    listOfRecs.add(new DailyRecommendation(day, rank, newBest, newSched, oldSched, oldValue));
                    addCraftedFromCycle(day, newSched, rank, true);
                }
                else if(newValue < oldValue.getWeighted())
                {
                    LOG.error("Somehow the best schedule for today is worse than what we generated before?? Help");
                    return null;
                }
            }
        }

        populateReservedItems(day+1);
        crimeTimeRecs.clear();
        int dayToSolve = day+1;
        for(int rank = 11; rank < 12; rank++)
        {
            groove = startingGroovePerDay.get(dayToSolve);
            if(day < 3)
            {
                if(rested == dayToSolve)
                    rested = -1;

                var recs = getRecForSingleDay(dayToSolve, rank, null, true);

                if(recs == null || recs.size() == 0 || recs.get(0) == null)
                {
                    LOG.error("Null rec returned for day {} and rank {}", dayToSolve, rank);
                    return null;
                }
                var rec = recs.get(0);

                listOfRecs.add(rec);
                if(rec.isRestRecommended())
                    rested = dayToSolve;

                if(dayToSolve==1 && !autocompletePeaks && testC2Imposters)
                    rec.setTroublemakers(getTentativeD2Items(), d2Bystanders);
                else if(dayToSolve == 2 && !allC3Set && testC3Imposters)
                    rec.setTroublemakers(getC3Troublemakers(), new HashSet<>());

                LOG.info("{}", rec);
                addCraftedFromCycle(rec.getDay(), rec.getBestRec(), rank, true);
            }
            else if(day < 6)
            {
                if(day == 3)
                {
                    generateCrimeTimeRecs(rank);
                    setCraftedFromHistory();
                }

                //Try days 5-7
                listOfRecs = getRecForSingleDay(dayToSolve, rank, null, true);
                for(var rec : listOfRecs)
                {
                    addCraftedFromCycle(rec.getDay(), rec.getBestRec(), rec.getMaxRank(), true);
                }

                if(day == 3 && rank==maxIslandRank)
                    totalValue = generateTotalValue(listOfRecs);
            }
        }


        hasRunRecs = true;
        isRunningRecs = false;
        return listOfRecs;
    }

    private boolean restedAlready()
    {
        return rested >0 && rested <= day;
    }

    private void setCraftedFromHistory()
    {
        for(int i=1; i<=day; i++)
        {
            CycleCraft crafts = craftRepository.findCraftsByDay(week, i, maxIslandRank);
            if(crafts == null)
                continue;

            clearDayUsage(List.of(i));

            if(crafts.getCraft1() != null && !crafts.getCraft1().isEmpty())
            {
                List<ItemInfo> todaysItems = new ArrayList<>();
                var craftsAsItems = crafts.getCrafts();
                for(int c=0; c<craftsAsItems.size(); c++)
                {
                    Item item = craftsAsItems.get(c);
                    ItemInfo itemInfo = items[item.ordinal()];
                    todaysItems.add(itemInfo);
                    int numToAdd = NUM_WORKSHOPS;
                    if(c>0 && itemInfo.getsEfficiencyBonus(todaysItems.get(c-1)))
                    {
                        numToAdd = NUM_WORKSHOPS * 2;
                    }

                    itemInfo.setCrafted(numToAdd + itemInfo.getCraftedOnDay(i), i);
                }
            }
        }
    }

    private String getKeyForAltRequest(int dayToSolve, int rank, List<Item> items)
    {
        if(rank < 5)
            rank = 1;
        if(rank > maxIslandRank)
            rank = maxIslandRank;
        String key =  dayToSolve+"-"+rank;
        if(items != null && items.size() > 0)
        {
            key+="-"+items.stream().map(Item::toString).collect(Collectors.joining("-"));
        }
        return key;
    }

    public List<DailyRecommendation> getRecForSingleDay(int dayToSolve, int rank, List<Item> limitedItems, boolean force)
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

        if(dayToSolve != 4 && dayToSolve != 5 && dayToSolve < 7)
        {
            DailyRecommendation rec;
            var todayRecs =  getBestBruteForceSchedules(dayToSolve, startingGroovePerDay.get(dayToSolve),
                    limitedUse, dayToSolve, alternatives, rank);
            if(todayRecs == null || todayRecs.size() == 0)
                return null;
            var bestSchedule = todayRecs.get(0);
            boolean shouldRest = false;
            CycleSchedule schedule = new CycleSchedule(dayToSolve, startingGroovePerDay.get(dayToSolve));
            schedule.setForAllWorkshops(bestSchedule.getKey().getItems());

            if(!restedAlready()) //If we haven't already rested, check to see if we should now
            {
                if(day < 2 && isWorseThanAllFollowing(bestSchedule, dayToSolve, false, rank, limitedUse))
                    shouldRest = true;
                else if(day == 2)
                {
                    guaranteeRestD5 = false;
                    boolean worst = isWorseThanAllFollowing(bestSchedule,  dayToSolve, true, rank, limitedUse);
                    if(worst)
                        shouldRest = true;
                    else if(guaranteeRestD5)
                    {
                        LOG.debug("Guaranteed resting D5 so recalculating D4");
                        todayRecs = getBestBruteForceSchedules(dayToSolve, startingGroovePerDay.get(dayToSolve),  limitedUse, dayToSolve + 1, alternatives, rank);
                        bestSchedule = todayRecs.get(0);
                        schedule.setForAllWorkshops(bestSchedule.getKey().getItems());
                    }
                }
            }

            if(shouldRest)
            {
                LOG.info("Should rest");
                rec = new DailyRecommendation(dayToSolve, rank, todayRecs);
            }
            else
            {
                rec = new DailyRecommendation(dayToSolve, rank, todayRecs, schedule);
            }
            recs.add(rec);
        }
        else if (dayToSolve == 4 || dayToSolve == 5)
        {
            recs = getLateDays(rank, limitedUse);
        }


        if(day+1>=Math.min(dayToSolve, 4)) //If we have the peaks for the day we're trying to solve
        {
            LOG.info("Caching results for "+cacheKey);
            cachedAltRecs.put(cacheKey, recs);
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

    public void generateCrimeTimeRecs(int rank)
    {
        List<int[]> crafted = new ArrayList<>();
        for(var item : items)
            crafted.add(item.craftedPerDay);
        int oldRested = rested;
        rested = 2;
        List<Item> crimeCrafts1 = Arrays.asList(Potion, CoconutJuice, Honey, TomatoRelish, SquidInk, TomatoRelish);
        List<Item> crimeCrafts2 = Arrays.asList(Firesand, PowderedPaprika, Isloaf, PopotoSalad, ParsnipSalad, PopotoSalad);
        List<Item> crimeCrafts3 = Arrays.asList(Necklace, Brush, Rope, CulinaryKnife, Earrings, CulinaryKnife);

        int totalCowries = 0;

        CycleSchedule crime1 = new CycleSchedule(1, 0);
        crime1.setWorkshop(0, crimeCrafts1);
        crime1.setWorkshop(1, crimeCrafts2);
        crime1.setWorkshop(2, crimeCrafts3);
        addCraftedFromCycle(1, crime1, maxIslandRank, false);
        int c1Value = crime1.getValue();
        LOG.info("Crime time C2: "+c1Value);
        totalCowries+=c1Value;

        CycleSchedule crime2 = new CycleSchedule(2, groove);
        crime2.setForAllWorkshops(new ArrayList<>());
        addCraftedFromCycle(2, crime2, maxIslandRank, false);

        CycleSchedule crime3 = new CycleSchedule(3, groove);
        crime3.setWorkshop(0, crimeCrafts1);
        crime3.setWorkshop(1, crimeCrafts2);
        crime3.setWorkshop(2, crimeCrafts3);
        addCraftedFromCycle(3, crime3, maxIslandRank, false);
        int c3Value = crime3.getValue();
        LOG.info("Crime time C4: "+c3Value);
        totalCowries+=c3Value;


        crimeTimeRecs.add(getLateDays(rank, null, 30));

        for(var rec : crimeTimeRecs.get(crimeTimeRecs.size()-1))
        {
            int crimeValue = rec.getDailyValue();
            LOG.info("Crime rec {}, crime value {}", rec.getBestRec().getItems(), crimeValue);
            totalCowries+=crimeValue;
        }

        if(rank == maxIslandRank)
            crimeTimeValue=totalCowries;

        rested = oldRested;
    }

    public int generateTotalValue(List<DailyRecommendation> lateWeekRecs)
    {
        int total = 0;
        for(int day = 1; day < 4; day++)
        {
            var crafts = craftRepository.findCraftsByDay(week, day, maxIslandRank);
            CycleSchedule sched = new CycleSchedule(day, startingGroovePerDay.get(day));
            sched.setForAllWorkshops(crafts.getCrafts());
            int today = sched.getValue();
            LOG.info("Getting total for day {}, crafts {}: {} cowries", day+1, sched.getItems(), today);
            total += today;
        }
        for(var rec : lateWeekRecs)
        {
            if(!rec.isRestRecommended())
            {
                LOG.info("Getting total for day {}, crafts {}: {} cowries", rec.getDay(), rec.getBestRec().getItems(), rec.getDailyValue());
                total+=rec.getDailyValue();
            }
        }
        return total;
    }
    public void setScheduleCommand(int day, int rank, List<Item> newItems)
    {
        int grooveSoFar = 0;

        if(!startingGroovePerDay.containsKey(day))
        {
            for(int i=1;i<day; i++)
            {
                grooveSoFar+= getGrooveMadeWithSchedule(craftRepository.findCraftsByDay(week, i, rank).getCrafts());
                startingGroovePerDay.put(i+1, grooveSoFar);
            }
        }
        grooveSoFar = startingGroovePerDay.get(day);

        if(rested == day && newItems.size() > 0)
            rested = -1;
        else if(rested == -1 && newItems.size() == 0)
            rested = day;


        LOG.info("Setting schedule for day {} to {} with starting groove {}", day+1, newItems, grooveSoFar);

        CycleSchedule newSched = new CycleSchedule(day, grooveSoFar);
        newSched.setForAllWorkshops(newItems);
        addCraftedFromCycle(day, newSched, rank, true);

        restOfWeek.clear();
        hoursLeftInDay.clear();
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
        else if(real && rank == maxIslandRank)
        {
            Arrays.stream(items).forEach(item -> item.setCrafted(0, day));
            startingGroovePerDay.put(day+1, startingGroovePerDay.get(day));
        }

        if(real && "live".equals(activeProfile))
        {
            CycleCraft crafts = new CycleCraft();
            crafts.setCraftID(new CraftID(week, day, rank));
            List<Item> items;
            if(schedule!=null)
                items = schedule.getItems();
            else
                items = new ArrayList<>();
            crafts.setCrafts(items);
            craftRepository.save(crafts);
            LOG.info("Saving crafts {} to db for week {}, day {}, and rank {}", items, week, day, rank);
        }
        else
            LOG.info("Not saving crafts because {}", real?"we're running locally":"we're just trying out values");
    }

    public boolean updatePeak(Item item, PeakCycle peak)
    {
        if(day==0)
        {
            autocompletePeaks = false;
            boolean changed = false;
            if(d2Troublemakers.containsKey(item)) {
                groove = 0;
                d2Troublemakers.put(item, true);
                changed = true;
            }
            else if(d2Bystanders.contains(item))
            {
                changed = true;
            }

            if(changed)
            {
                List<CraftPeaks> peaksToSave = new ArrayList<>();
                items[item.ordinal()].peak = peak;

                int strong = confirmedD2Strong;
                int weak = confirmedD2Weak;
                for(var kvp : d2Troublemakers.entrySet())
                {
                    if(kvp.getValue())
                    {
                        PeakCycle setPeak = items[kvp.getKey().ordinal()].peak;
                        if(setPeak == Cycle2Strong)
                            strong++;
                        else if(setPeak == Cycle2Weak)
                            weak++;
                    }
                }

                for(var bystander : d2Bystanders)
                {
                    PeakCycle setPeak = items[bystander.ordinal()].peak;
                    if(setPeak == Cycle2Strong)
                        strong++;
                    else if(setPeak == Cycle2Weak)
                        weak++;
                }

                LOG.info("Currently have {}/5 strong peaks and {}/5 weak peaks", strong, weak);

                if(strong == 5)
                {
                    setAllTentativePeaks(Cycle2Weak, peaksToSave);
                }
                else if(weak == 5)
                {
                    setAllTentativePeaks(Cycle2Strong, peaksToSave);
                }


                if("live".equals(activeProfile))
                {
                    CraftPeaks singlePeak = new CraftPeaks();
                    singlePeak.setPeakFromEnum(peak);
                    singlePeak.setPeakID(new PeakID(week, day, item.ordinal()+1));
                    peaksToSave.add(singlePeak);
                    peakRepository.saveAll(peaksToSave);
                }

            }

            return changed;
        }
        else if(day==1 && d3Troublemakers.containsKey(item))
        {
            groove = startingGroovePerDay.get(2);
            d3Troublemakers.put(item, true);
            if(peak == Cycle3Weak && "live".equals(activeProfile))
            {
                CraftPeaks singlePeak = new CraftPeaks();
                singlePeak.setPeakFromEnum(peak);
                singlePeak.setPeakID(new PeakID(week, day, item.ordinal()+1));
                List<CraftPeaks> peaksToSave = new ArrayList<>();
                peaksToSave.add(singlePeak);
                peakRepository.saveAll(peaksToSave);
            }
            return true;
        }
        return false;
    }

    private void setAllTentativePeaks(PeakCycle peak, List<CraftPeaks> peaksToSave)
    {
        LOG.info("Found 5 C2 peaks of a strength, setting the rest to "+peak);
        for(var kvp : d2Troublemakers.entrySet())
        {
            if(!kvp.getValue())
            {
                LOG.info("Defaulting troublemaker {} to {} ", kvp.getKey(), peak);
                items[kvp.getKey().ordinal()].peak = peak;
                CraftPeaks singlePeak = new CraftPeaks();
                singlePeak.setPeakFromEnum(peak);
                singlePeak.setPeakID(new PeakID(week, day, kvp.getKey().ordinal()+1));
                peaksToSave.add(singlePeak);
            }

        }

        for(var bystander : d2Bystanders)
        {
            if(items[bystander.ordinal()].peak == Cycle2Unknown)
            {
                LOG.info("Defaulting bystander {} to {} ", bystander, peak);
                items[bystander.ordinal()].peak = peak;
                CraftPeaks singlePeak = new CraftPeaks();
                singlePeak.setPeakFromEnum(peak);
                singlePeak.setPeakID(new PeakID(week, day, bystander.ordinal()+1));
                peaksToSave.add(singlePeak);
            }
        }

        autocompletePeaks = true;
    }

    private void populateReservedItems(int day)
    {
        reservedItems.clear();
        Map<Item, Integer> itemValues = new HashMap<>();
        for (ItemInfo item : items)
        {
            if (item.peaksOnOrBeforeDay(day, null))
                continue;
            int value = item.getValueWithSupply(Supply.Sufficient);
            value = value * 8 / item.time;
            itemValues.put(item.item, value);
        }
        LinkedHashMap<Item, Integer> bestItems = itemValues
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> y, LinkedHashMap::new));
        var bestItemsEntries = bestItems.entrySet();
        Iterator<Entry<Item, Integer>> itemIterator = bestItemsEntries.iterator();

        List<Item> itemsThatGetReservations = new ArrayList<>();
        for (int i = 0; i < itemsToReserve-(2*day) && itemIterator.hasNext(); i++)
        {
            var next = itemIterator.next();
            LOG.info("Reserving item {} ({})", next.getKey(), next.getValue());
            reservedItems.add(next.getKey());
            if (i < (5-day)*2)
                itemsThatGetReservations.add(next.getKey());
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
                LOG.info("Reserving helper " + bestHelper + " to go with main item " + itemEnum + " (#" + (i + 1) + "), difference between " + bestHelper + " and " + secondHelper + "? " + swap + " cost of stepping down? " + stepDown + " Penalty: " + finalPenalty);

                reservedHelpers.put(itemEnum, new ReservedHelper(bestHelper, finalPenalty));
            }
        }
    }

    private int getGrooveMadeWithSchedule(List<Item> previous)
    {
        int effCrafts = 0;
        for(int i=1;i<previous.size();i++)
        {
            if(items[previous.get(i).ordinal()].getsEfficiencyBonus(items[previous.get(i-1).ordinal()]))
                effCrafts++;
        }

        return effCrafts * NUM_WORKSHOPS;
    }

    private void addDailyRecToList(List<Entry<WorkshopSchedule, WorkshopValue>> recs, int day, int groove, int rank, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(day, groove);
        bestSchedule.setForAllWorkshops(recs.get(0).getKey().getItems());
        addCraftedFromCycle(day, bestSchedule, rank, false);
        var newRec = new DailyRecommendation(day, rank, recs, bestSchedule);
        LOG.info("Adding late-week rec {}", newRec);
        recommendations.add(newRec);
    }
    private void addRestToList(List<Entry<WorkshopSchedule, WorkshopValue>> recs, int day, int rank, List<DailyRecommendation> recommendations)
    {
        addCraftedFromCycle(day, null, rank, false);
        var newRec = new DailyRecommendation(day, rank, recs);
        LOG.info("Resting for late-week rec {}", newRec);
        recommendations.add(newRec);
    }
    public List<DailyRecommendation> getLateDays(int rank, Map<Item, Integer> limitedUse)
    {
        return getLateDays(rank, limitedUse, -1);
    }

    /*public List<DailyRecommendation> getBestLateDays(int rank, Map<Item, Integer> limitedUse, List<Integer> daysToTest, int startingGroove, int daySet, WorkshopSchedule itemsSet)
    {
        LOG.info("Getting best late days for days {}. Previous day set: {}", daysToTest, daySet);
        List<DailyRecommendation> recommendations = null;

        for(Integer bestDay : daysToTest)
        {
            clearDayUsage(daysToTest);
            groove = startingGroove;
            Map<Item, Integer> realLimited = limitedUse;
            if(daySet > bestDay)
                realLimited = itemsSet.getLimitedUses(limitedUse);
            List<DailyRecommendation> basedOnSingle = new ArrayList<>();
            LOG.info("Getting best schedule for best day {} and groove {}. Limited items: {}", bestDay,startingGroove, realLimited);
            var sched = getBestBruteForceSchedules(bestDay, startingGroove, realLimited, Math.min(6,bestDay + 1), alternatives, rank);
            addDailyRecToList(sched, bestDay, startingGroove, rank, basedOnSingle);
            List<Integer> newDays = new ArrayList<>();
            for(int day : daysToTest)
                if(day != bestDay)
                    newDays.add(day);
            if(newDays.size() > 1)
            {
                LOG.info("Having set day {}, now getting best of days {}", bestDay, newDays);
                basedOnSingle.addAll(getBestLateDays(rank, realLimited, newDays, startingGroove, bestDay, sched.get(0).getKey()));
            }
            else
            {
                if(bestDay > newDays.get(0))
                    realLimited = sched.get(0).getKey().getLimitedUses(realLimited);
                LOG.info("Only one day left. Getting best schedule for best day {} and groove {}. Limited items: {}", newDays.get(0),startingGroove, realLimited);
                addDailyRecToList(getBestBruteForceSchedules(newDays.get(0), startingGroove, realLimited, Math.min(6,newDays.get(0) + 1), alternatives, rank), newDays.get(0), startingGroove,rank,basedOnSingle);
            }

            if(recommendations == null || (getTotalForRecs(basedOnSingle) > getTotalForRecs(recommendations)))
            {
                LOG.info("New best day detected! Of {}, best is {}", daysToTest, bestDay);
                recommendations = basedOnSingle;
            }
        }
        return recommendations;
    }*/

    private int getTotalForRecs(List<DailyRecommendation> recs)
    {
        int total = 0;
        for(DailyRecommendation rec : recs)
        {
            total += rec.getDailyValue();
        }
        return total;
    }

    public List<DailyRecommendation> getLateDays(int rank, Map<Item, Integer> limitedUse, int startingGroove)
    {
        clearLateDayUsage();


        if(startingGroove == -1)
            startingGroove = startingGroovePerDay.get(4);
        var cycle5Sched = getBestBruteForceSchedules(4, startingGroove, limitedUse, 5, alternatives, rank);
        var cycle6Sched = getBestBruteForceSchedules(5, startingGroove, limitedUse, 6, alternatives, rank);
        var cycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);

        if(cycle5Sched == null || cycle5Sched.size() == 0 || cycle6Sched == null || cycle6Sched.size() == 0 || cycle7Sched == null || cycle7Sched.size() == 0)
            return null;

        // I'm just hardcoding this, This could almost certainly be improved

        List<Entry<WorkshopSchedule, WorkshopValue>> endOfWeekSchedules = new ArrayList<>();
        endOfWeekSchedules.add(cycle5Sched.get(0));
        endOfWeekSchedules.add(cycle6Sched.get(0));
        endOfWeekSchedules.add(cycle7Sched.get(0));

        int bestDay = -1;
        int bestDayValue = -1;

        for (int i = 0; i < 3; i++)
        {
            int value = endOfWeekSchedules.get(i).getValue().getWeighted();
            if (bestDay == -1 || value > bestDayValue)
            {
                bestDay = i + 4;
                bestDayValue = value;
            }
        }

        List<DailyRecommendation> c5Recs = new ArrayList<>();
        //if (bestDay == 4) // Day 5 is best
        {
            LOG.info("Calcing based on c5");
            addDailyRecToList(cycle5Sched, 4, startingGroove, rank, c5Recs);

            int newStartingGroove = groove;
            cycle6Sched = getBestBruteForceSchedules(5, newStartingGroove, limitedUse, 6, alternatives, rank);
            cycle7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

            if(rested < 0 || rested >= 4)
            {
                //Haven't rested, need to pick 5 or 7
                if(cycle6Sched.get(0).getValue().getWeighted() > cycle7Sched.get(0).getValue().getWeighted())
                {
                    addDailyRecToList(cycle6Sched, 5, newStartingGroove, rank, c5Recs);
                    addRestToList(getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank), 6, rank, c5Recs);
                }
                else
                {
                    var newLimited = cycle7Sched.get(0).getKey().getLimitedUses(limitedUse);
                    addRestToList(getBestBruteForceSchedules(5, newStartingGroove, newLimited, 6, alternatives, rank), 5, rank, c5Recs);
                    addDailyRecToList(cycle7Sched, 6, newStartingGroove, rank, c5Recs);
                }
            }
            else //Using all 3
            {
                CycleSchedule best6 = new CycleSchedule(5, newStartingGroove);
                best6.setForAllWorkshops(cycle6Sched.get(0).getKey().getItems());
                addCraftedFromCycle(5, best6, rank, false);
                var recalced7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

                var only6Sched = getBestBruteForceSchedules(5, newStartingGroove, limitedUse, 5, alternatives, rank);
                CycleSchedule bestOnly6 = new CycleSchedule(5, newStartingGroove);
                bestOnly6.setForAllWorkshops(only6Sched.get(0).getKey().getItems());
                addCraftedFromCycle(5, bestOnly6, rank, false);
                var only7Sched = getBestBruteForceSchedules(6, newStartingGroove, limitedUse, 6, alternatives, rank);

                if(cycle6Sched.get(0).getValue().getWeighted() + recalced7Sched.get(0).getValue().getWeighted() > only6Sched.get(0).getValue().getWeighted() + only7Sched.get(0).getValue().getWeighted())
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
            LOG.info("Calcing based on c7");
            Map<Item,Integer> reserved7Set = cycle7Sched.get(0).getKey().getLimitedUses(limitedUse);

            if(rested < 0 || rested >= 4)//We only care about one of 5 or 6
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 6, alternatives, rank);
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives, rank);

                if(cycle5Sched.get(0).getValue().getWeighted() > cycle6Sched.get(0).getValue().getWeighted())
                {
                    addDailyRecToList(cycle5Sched, 4, startingGroove, rank, c7Recs);
                    addRestToList(getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives, rank), 5, rank, c7Recs);
                }
                else
                {
                    var newLimited = cycle6Sched.get(0).getKey().getLimitedUses(reserved7Set);
                    addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 6, alternatives, rank), 4, rank, c7Recs);
                    addDailyRecToList(cycle6Sched, 5, startingGroove, rank, c7Recs);
                }
            }
            else
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 4, alternatives, rank);

                int total65 = 0;
                int grooveFrom5 = getGrooveMadeWithSchedule(cycle5Sched.get(0).getKey().getItems());
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove + grooveFrom5, reserved7Set, 6, alternatives, rank);
                //try deriving 5 from 6
                Map<Item,Integer> reserved67Items = cycle6Sched.get(0).getKey().getLimitedUses(reserved7Set);
                var recalcedCycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved67Items, 6, alternatives, rank);
                CycleSchedule best65 = new CycleSchedule(4, startingGroove);
                best65.setForAllWorkshops(recalcedCycle5Sched.get(0).getKey().getItems());
                total65 += best65.getValue() * 2 - best65.getMaterialCost();
                addCraftedFromCycle(4, best65, rank, false);
                CycleSchedule best6 = new CycleSchedule(6, groove);
                best6.setForAllWorkshops(cycle6Sched.get(0).getKey().getItems());
                total65 += best6.getValue() * 2 - best6.getMaterialCost();

                /*System.out.println("Trying to prioritize day 6:"+Arrays.toString(recalcedCycle6Sched.getKey().getItems().toArray())
                        +" ("+recalcedCycle6Sched.getValue()+"), so day 5: "+Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())
                        +" ("+recalcedCycle5Sched.getValue()+") total: "+total65);*/

                //Try deriving 6 from 5
                int total56 = 0;
                CycleSchedule best5 = new CycleSchedule(4, startingGroove);
                best5.setForAllWorkshops(cycle5Sched.get(0).getKey().getItems());
                total56 += best5.getValue() * 2 - best5.getMaterialCost();
                addCraftedFromCycle(4, best5, rank, false);

                var basedOn56Sched = getBestBruteForceSchedules(5, groove, reserved7Set, 6, alternatives, rank);

                CycleSchedule best56 = new CycleSchedule(5, groove);
                best56.setForAllWorkshops(basedOn56Sched.get(0).getKey().getItems());
                total56 += best56.getValue() * 2 - best56.getMaterialCost();

                /*System.out.println("Trying to prioritize day 5:"+Arrays.toString(cycle5Sched.getKey().getItems().toArray())
                        +" ("+cycle5Sched.getValue()+"), so day 6: "+Arrays.toString(basedOn56Sched.getKey().getItems().toArray())
                        +" ("+basedOn56Sched.getValue()+") total: "+total56);*/

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
            LOG.info("Calcing based on c6");
            CycleSchedule best6 = new CycleSchedule(5, startingGroove);
            best6.setForAllWorkshops(cycle6Sched.get(0).getKey().getItems());
            addCraftedFromCycle(5, best6, rank, false);

            Map<Item,Integer> reserved6 = cycle6Sched.get(0).getKey().getLimitedUses(limitedUse);
            //System.out.println("Recalcing D5 allowing D6's items");

            var recalcedCycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved6, 5, alternatives, rank);
            var recalcedCycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);
            /*System.out.println("c5 sched:" +Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())+ " ("
                    +recalcedCycle5Sched.getValue()+") compared to c7: "+Arrays.toString(recalcedCycle7Sched.getKey().getItems().toArray())
            +" ("+recalcedCycle7Sched.getValue()+")");*/

            var onlyCycle6Sched = getBestBruteForceSchedules(5, startingGroove, limitedUse, 5, alternatives, rank);
            CycleSchedule bestOnly6 = new CycleSchedule(5, startingGroove);
            bestOnly6.setForAllWorkshops(onlyCycle6Sched.get(0).getKey().getItems());
            addCraftedFromCycle(5, bestOnly6, rank, false);
            var onlyCycle7Sched = getBestBruteForceSchedules(6, startingGroove, limitedUse, 6, alternatives, rank);



            Map<Item,Integer> reservedOnly6 = onlyCycle6Sched.get(0).getKey().getLimitedUses(limitedUse);
            var onlyCycle5Sched = getBestBruteForceSchedules(4, startingGroove,
                    reservedOnly6, 5, alternatives, rank);

            if(rested < 0 || rested >= 4)
            {
                //We only care about either 5 or 7, not both
                int best56Combo = cycle6Sched.get(0).getValue().getWeighted() + recalcedCycle5Sched.get(0).getValue().getWeighted();
                int best67Combo = cycle6Sched.get(0).getValue().getWeighted() + recalcedCycle7Sched.get(0).getValue().getWeighted();
                int best76Combo = onlyCycle6Sched.get(0).getValue().getWeighted() + onlyCycle7Sched.get(0).getValue().getWeighted();

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
                        var newLimited = cycle6Sched.get(0).getKey().getLimitedUses(limitedUse);
                        addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 5, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(cycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    else
                    {
                        var newLimited = onlyCycle6Sched.get(0).getKey().getLimitedUses(limitedUse);
                        addRestToList(getBestBruteForceSchedules(4, startingGroove, newLimited, 5, alternatives, rank), 4, rank, c6Recs);
                        addDailyRecToList(onlyCycle6Sched, 5, startingGroove, rank, c6Recs);
                    }
                    addDailyRecToList(getBestBruteForceSchedules(6, groove, limitedUse, 6, alternatives, rank), 6, groove, rank, c6Recs);
                }
            }
            else //We're using all 3 days
            {
                if(cycle6Sched.get(0).getValue().getWeighted() + recalcedCycle5Sched.get(0).getValue().getWeighted() + recalcedCycle7Sched.get(0).getValue().getWeighted()
                        > onlyCycle5Sched.get(0).getValue().getWeighted() + onlyCycle6Sched.get(0).getValue().getWeighted() + onlyCycle7Sched.get(0).getValue().getWeighted())
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

        List<DailyRecommendation> bestRecs = c5Recs;
        int c5Value = getTotalForRecs(c5Recs);
        int c6Value = getTotalForRecs(c6Recs);
        int c7Value = getTotalForRecs(c7Recs);
        int bestValue = Math.max(c5Value, Math.max(c6Value, c7Value));

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
    
    private boolean isWorseThanAllFollowing(Entry<WorkshopSchedule, WorkshopValue> rec,
            int day, int rank)
    {
        return isWorseThanAllFollowing(rec, day, false, rank, null);
    }

    private boolean isWorseThanAllFollowing(Entry<WorkshopSchedule, WorkshopValue> rec,
            int day, boolean checkD5, int rank, Map<Item,Integer> limitedUse)
    {
        int groove = startingGroovePerDay.get(day);
        int worstInFuture = 99999;
        boolean bestD5IsWorst = true;
        int bestD5 = 0;
        int weightedValue = rec.getValue().getWeighted();
        LOG.info("Comparing d{} rank {}: {} ({}) to worst-case future days", (day + 1), rank, rec.getKey().getItems(), weightedValue);


        Map<Item,Integer> reservedSet = null;
        if(limitedUse != null)
            reservedSet = new HashMap<>(limitedUse);

        for (int d = day + 1; d < 7; d++)
        {
            Entry<WorkshopSchedule, WorkshopValue> solution;
            if (day == 3 && d == 4) // We have a lot of info about this specific pair so
                                    // we might as well use it
            {
                solution = getD5EV(rank);
                if(solution == null)
                {
                    LOG.error("Failed to get D5 EV. Abandoning rest checks.");
                    return false;
                }

                bestD5 = solution.getValue().getWeighted();
            }
            else
                solution = getBestSchedule(d, groove, reservedSet, rank);
            if(solution == null)
            {
                LOG.error("Failed to get rest comparison for day {}. Abandoning rest checks.", d+1);
                return false;
            }

            LOG.debug("Day " + (d + 1) + ", crafts: "
                        + Arrays.toString(solution.getKey().getItems().toArray())
                        + " value: " + solution.getValue());
            worstInFuture = Math.min(worstInFuture, solution.getValue().getWeighted());
            reservedSet = solution.getKey().getLimitedUses(reservedSet);
            
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

        return weightedValue <= worstInFuture;
    }

    // Specifically for comparing D4 to D5
    public Entry<WorkshopSchedule, WorkshopValue> getD5EV(int rank)
    {
        int groove = startingGroovePerDay.get(3);
        var solution = getBestSchedule(4, groove,null, rank );
        if(solution == null)
            return null;

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
                boolean strong = ((p) & (1 << i)) != 0; // I can't believe I'm using a bitwise and
                LOG.trace("Checking permutation " + p + " for item "+ c5Peaks.get(i).item + " " + (strong ? "strong" : "weak"));
                if (strong)
                    c5Peaks.get(i).peak = Cycle5Strong;
                else
                    c5Peaks.get(i).peak = Cycle5Weak;
            }

            int toAdd = solution.getKey().getValueWithGrooveEstimate(4, groove, restedAlready(), reservedHelpers).getWeighted();
            LOG.trace("Permutation " + p + " has value " + toAdd);
            sum += toAdd;
        }

        LOG.trace("Sum: " + sum + " average: " + sum / permutations);
        sum /= permutations;
        WorkshopValue value = new WorkshopValue(sum, 0, 0, 0, 0 );
        solution.setValue(value);

        for (ItemInfo item : c5Peaks)
        {
            item.peak = Cycle5; // Set back to normal
        }

        return solution;
    }

    public List<Entry<WorkshopSchedule, WorkshopValue>> getRestOfDayRecs(int day, int hoursLeft, int rank, Item startingItem)
    {
        if(rank > maxIslandRank)
            rank = maxIslandRank;
        LOG.info("Last day (hours) calculated: {} ({}). Searching for {} ({})", this.day, hoursLeftInDay.get(rank), day, hoursLeft);

        if(day == this.day && hoursLeftInDay.containsKey(rank) && hoursLeftInDay.get(rank) == hoursLeft)
        {
            LOG.info("Returning rest of day recs from cache");
            return restOfDay.get(rank);
        }


        LOG.info("Recalculating today's recs");

        List<Map.Entry<WorkshopSchedule, WorkshopValue>> restOfDayRank = new ArrayList<>();


        int startingGroove = startingGroovePerDay.get(day);

        Map<Item, Integer> limitedItems = null;
        int lastDaySet = day+1;
        if(day >= 3)
            lastDaySet = 6;

        LOG.info("Reserving future crafts made through day {}", lastDaySet+1);


        for(int i=day+1; i<=lastDaySet; i++)
        {
            var futureCrafts = craftRepository.findCraftsByDay(week, i, rank);
            if(futureCrafts == null)
            {
                if(rank != maxIslandRank)
                    futureCrafts = craftRepository.findCraftsByDay(week, i, maxIslandRank);

                if(futureCrafts == null)
                {
                    lastDaySet = i-1;
                    break;
                }
            }
            var crafts = futureCrafts.getCrafts();
            LOG.info("Reserving future crafts for day {}: {}", i+1, crafts);
            limitedItems = new WorkshopSchedule(crafts).getLimitedUses(limitedItems);
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

    public List<List<Item>> getRestOfWeekRecs(int rank)
    {
        if(rank > maxIslandRank)
            rank = maxIslandRank;

        if(restOfWeek.containsKey(rank))
        {
            LOG.info("Returning rest of week from cache");
            return restOfWeek.get(rank);
        }


        Map<Item,Integer> reservedSet = new HashMap<>();
        var restOfWeekRank = new ArrayList<List<Item>>();

        int worstIndex = -1;
        int worstValue = 99999;
        int estGroove = (groove + GROOVE_MAX) / 2;

        for (int d = day + 2; d < 7; d++)
        {
            var solution = getBestSchedule(d, estGroove, reservedSet, rank);
            if(solution==null)
            {
                LOG.error("Failed to generate rest of week recs for day {}", d+1);
                break;
            }

            int value = solution.getValue().getWeighted();

            if(value <= worstValue) //Equals because we want to pick the latest day with the worst value
            {
                worstValue = value;
                worstIndex = restOfWeekRank.size();
            }

            restOfWeekRank.add(solution.getKey().getItems());
            reservedSet = solution.getKey().getLimitedUses(reservedSet);
        }

        if(rested == -1 && worstIndex>=0)
        {
            //If we haven't rested or scheduled to rest, rest the worst day
            restOfWeekRank.remove(worstIndex);
            restOfWeekRank.add(worstIndex, new ArrayList<>());
        }
        if(restOfWeekRank.size() == 5) //If we're at day 1, we have no real idea, so put our best guess at C6, the second-best day to craft
        {
            var best = restOfWeekRank.set(0, restOfWeekRank.get(3));
            restOfWeekRank.set(3, best);
        }

        for(var list : restOfWeekRank)
        {
            LOG.info("rest of week ({}): {}", rank, list);
        }
        restOfWeek.put(rank, restOfWeekRank);

        return restOfWeekRank;
    }

    private int generateVacationRecs(int currentWeek)
    {
        LOG.info("Generating vacation recs");

        //generate vacation recs
        var popData = popularityRepository.findByWeek(currentWeek);
        LOG.info("Getting popularity data for next week: {}", popData.getNextPopularity());
        int nextPop = popData.getNextPopularity();
        String popResponse;
        try{
            popResponse = restService.getURLResponse("https://xivapi.com/MJICraftworksPopularity/"+nextPop);
        }
        catch(RestClientException e)
        {
            LOG.error("Couldn't connect to XIV API to get popularity info. Abandoning ship", e);
            return popData.getPopularity();
        }

        PopularityJson popJson;
        try {
            popJson = objectMapper.readValue(popResponse, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            LOG.error("Couldn't read pop json from XIV API", e);
            return popData.getPopularity();
        }

        for(int i=0;i<items.length;i++)
        {
            int ratio = popJson.getPopularities()[i].getRatio();
            //LOG.info("Setting {} to initial data of {} and {}", items[i].item, ratio, Unknown);
            items[i].setInitialData(ratio, Unknown);
        }

        vacationRecs.put(9, vacationRecsHelper(9));
        vacationRecs.put(10, vacationRecsHelper(10));
        vacationRecs.put(11, vacationRecsHelper(11));
        return popData.getPopularity();
    }

    private List<List<Item>> vacationRecsHelper(int rank)
    {
        Map<Item,Integer> reservedSet = null;
        var vacationRecs = new ArrayList<List<Item>>();
        for (int d = 0; d < 5; d++)
        {
            var schedule = getBestSchedule(3, GROOVE_MAX/2, reservedSet, rank);
            if(schedule == null)
            {
                LOG.error("Failed to get vacation schedule {} with day {}, groove {}, and reserved {}", d, 3, GROOVE_MAX/2, reservedSet);
                break;
            }
            WorkshopSchedule solution = schedule.getKey();
            vacationRecs.add(solution.getItems());
            reservedSet = solution.getLimitedUses(reservedSet);
        }
        return vacationRecs;
    }



    public boolean isSolvedD2()
    {
        //LOG.info("Day {} troublemakers: {}", day, d2Troublemakers==null?"null":String.valueOf(d2Troublemakers.size()));
        return !testC2Imposters || day != 0 || d2Troublemakers == null || d2Troublemakers.size() == 0;
    }

    public boolean isSolvedD3()
    {
        return !testC3Imposters || day != 1 || d3Troublemakers == null || d3Troublemakers.size() == 0;
    }

    public boolean allTentativeD2Set()
    {
        if(isSolvedD2() || autocompletePeaks)
            return true;

        for(var value : d2Troublemakers.values())
            if(!value)
                return false;

        return true;
    }

    public boolean allTentativeD3Set()
    {
        if(isSolvedD3())
            return true;

        for(var value : d3Troublemakers.values())
            if(!value)
                return false;

        allC3Set = true;
        return true;
    }


    public Map<Item, Boolean> getTentativeD2Items()
    {
        if(d2Troublemakers != null)
            return d2Troublemakers;

        d2Troublemakers = new HashMap<>();
        d2Bystanders = new HashSet<>();
        Map<Item, Integer> troubleValues = new HashMap<>();
        List<ItemInfo> c2Unknowns = new ArrayList<>();
        confirmedD2Strong = 0;
        confirmedD2Weak = 0;
        for (ItemInfo item : items)
        {
            if (item.peak == Cycle2Unknown)
                c2Unknowns.add(item);
            else if(item.peak == Cycle2Strong)
                confirmedD2Strong++;
            else if(item.peak == Cycle2Weak)
                confirmedD2Weak++;
        }

        autocompletePeaks = false;

        LOG.info("Checking {} unknown D2 peaks: {}", c2Unknowns.size(), c2Unknowns.stream().map(itemInfo -> itemInfo.item).collect(Collectors.toList()));

        int permutations = (int) Math.pow(2, c2Unknowns.size());



        for(int rank = 11; rank < 12; rank++)
        {
            Map<Integer, List<Item>> betterPermuts = new HashMap<>();
            var schedule = getBestSchedule(1, 0, null, rank);
            int value = 0;
            if (schedule == null)
            {
                continue;
            }

            value = schedule.getValue().getWeighted();

            boolean shouldRest =  isWorseThanAllFollowing(schedule, 1, rank);


            for (int p = 1; p < permutations; p++)
            {
                for (int i = 0; i < c2Unknowns.size(); i++)
                {
                    boolean strong = ((p) & (1 << i)) != 0; // I can't believe I'm using a bitwise and
                    if (strong)
                        c2Unknowns.get(i).peak = Cycle2Strong;
                    else
                        c2Unknowns.get(i).peak = Cycle2Weak;
                }

                var newSchedule = getBestSchedule(1, 0, null, rank);

                if (newSchedule == null)
                    continue;

                int newValue = newSchedule.getValue().getWeighted();

                if(newValue > value + middayUpdateThreshold && (betterPermuts.size() > 0 || !newSchedule.getKey().getItems().equals(schedule.getKey().getItems())))
                {
                    if(shouldRest && isWorseThanAllFollowing(newSchedule, 1, rank))
                        continue;
                    CycleSchedule cycleSchedule = new CycleSchedule(1, 0);
                    cycleSchedule.setForAllWorkshops(newSchedule.getKey().getItems());


                    LOG.info("Schedule {} ({}) is better with permutation {} on rank {}. Value: {}", newSchedule.getKey().getItems(), newValue, p, rank, cycleSchedule.getValue());
                    betterPermuts.put(p, newSchedule.getKey().getItems());
                }
            }

            for(Integer p : betterPermuts.keySet())
            {
                for (int i = 0; i < c2Unknowns.size(); i++)
                {
                    boolean strong = ((p) & (1 << i)) != 0;
                    if (strong && betterPermuts.get(p).contains(c2Unknowns.get(i).item))
                        d2Troublemakers.put(c2Unknowns.get(i).item, false);
                }
            }
        }

        for (var c2Unknown: c2Unknowns)
        {
            if(!d2Troublemakers.containsKey(c2Unknown.item))
                d2Bystanders.add(c2Unknown.item);
            c2Unknown.peak = Cycle2Unknown;
        }

        return d2Troublemakers;
    }

    private Map<Item,Boolean> getC3Troublemakers()
    {
        d3Troublemakers = new HashMap<>();
        allC3Set = false;
        for(int rank = 11; rank < 12; rank++)
        {
            var schedule = getBestSchedule(2, groove, null, rank);
            int baseValue = 0;
            if (schedule == null)
            {
                continue;
            }

            baseValue = schedule.getValue().getWeighted();

            boolean shouldRest =  isWorseThanAllFollowing(schedule, 2, rank);

            for(int i=50; i<items.length; i++) //Only check imposters in post-6.3 items
            {
                var item = items[i];
                if(item.peak == Cycle67)
                {
                    item.peak = Cycle3Weak;
                    var newSchedule = getBestSchedule(2, groove, null, rank);
                    if(newSchedule == null)
                        continue;
                    int newValue = newSchedule.getValue().getWeighted();
                    boolean itemIsGoodEnough = newValue > baseValue + middayUpdateThreshold;
                    if(itemIsGoodEnough && !newSchedule.getKey().getItems().equals(schedule.getKey().getItems()))
                    {
                        if (shouldRest && isWorseThanAllFollowing(newSchedule, 2, rank))
                            continue;
                        d3Troublemakers.put(item.item, false);
                        CycleSchedule cycleSchedule = new CycleSchedule(2, groove);
                        cycleSchedule.setForAllWorkshops(newSchedule.getKey().getItems());
                        LOG.info("Schedule {} ({}) is better on C3 with weak {} on rank {}. Total value: {}", newSchedule.getKey().getItems(),newValue, item.item, rank, cycleSchedule.getValue());
                    }
                    item.peak = Cycle67;
                }
            }
        }

        return d3Troublemakers;
    }

    private Map.Entry<WorkshopSchedule, WorkshopValue> getBestSchedule(int day, int groove, Map<Item,Integer> limitedUse, int rank)
    {
        var schedules = getBestBruteForceSchedules(day, groove, limitedUse, day, 1, rank);
        if(schedules == null || schedules.size() == 0)
        {
            LOG.info("Best schedule for day {} rank {} with {} groove and {} limited items: null", day+1, rank, groove, limitedUse==null?0:limitedUse.size());
            return null;
        }
        var bestSchedule = schedules.get(0);
        LOG.info("Best schedule for day {} rank {} with {} groove and {} limited items: {} ({})", day+1, rank, groove, limitedUse==null?0:limitedUse.size(), Arrays.toString(bestSchedule.getKey().getItems().toArray()), bestSchedule.getValue().getWeighted());
        return bestSchedule;
    }

    private List<Map.Entry<WorkshopSchedule, WorkshopValue>> getBestBruteForceSchedules(int day, int groove,
                                            Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, int islandRank)
    {
        return getBestBruteForceSchedules(day, groove, limitedUse, allowUpToDay, numToReturn, null, 24, islandRank);
    }

    private List<Map.Entry<WorkshopSchedule, WorkshopValue>> getBestBruteForceSchedules(int day, int groove,
            Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, Item startingItem, int hoursLeft, int islandRank)
    {
        /*LOG.info("Getting best schedule for day {}. groove {}. limitedUse {}, allowUpToDay {}, startingItem {}, hoursLeft {} and chains {}",
                day+1, groove, limitedUse, allowUpToDay, startingItem, hoursLeft, csvImporter.allEfficientChains.size());*/
        HashMap<WorkshopSchedule, WorkshopValue> safeSchedules = new HashMap<>();
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

        if(filteredItemLists.size() == 0)
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

        for (List<Item> list : filteredItemLists)
        {
            addToScheduleMap(list, day, groove, limitedUse, safeSchedules, false);
        }

        if(safeSchedules.size() == 0)
        {
            LOG.error("No valid schedules found after checking limitedUse {}", limitedUse);

            for (List<Item> list : filteredItemLists)
            {
                addToScheduleMap(list, day, groove, limitedUse, safeSchedules, true);
            }

            if(safeSchedules.size() == 0)
                return null;
        }

        var sortedSchedules = safeSchedules
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());

        List<Integer> redundantIndices = new ArrayList<>();
        var sets = new HashSet<Map<RareMaterial, Integer>>();

        if(numToReturn > 1)
        {
            for(int i=0; i<numToReturn * 3 && i<sortedSchedules.size(); i++)
            {
                WorkshopSchedule sched = sortedSchedules.get(i).getKey();
                if(sched.isItemSuperset(sets))
                {
                    redundantIndices.add(i);
                }
                else
                    sets.add(sched.rareMaterialsRequired);
            }
        }


        for(int j = redundantIndices.size() - 1; j >=0; j--)
        {
            //Remove from the end forward because the indices will change once you start removing
            int i = redundantIndices.get(j);
            sortedSchedules.remove(i);
        }

        if(sortedSchedules.size() == 0)
        {
            LOG.error("No valid schedules found after reducing {} redundant schedules", redundantIndices.size());
            return null;
        }

        return sortedSchedules.stream().limit(numToReturn).collect(Collectors.toList());
    }
    private static int getHoursUsed(List<Item> schedule)
    {
        return schedule.stream().mapToInt(item -> items[item.ordinal()].time).sum();
    }
    private void addToScheduleMap(List<Item> list, int day, int groove, Map<Item,Integer> limitedUse,
            HashMap<WorkshopSchedule, WorkshopValue> safeSchedules, boolean verboseSolverLogging)
    {
        if(verboseSolverLogging)
            LOG.info("Checking schedule {} against {} safe schedules", list, safeSchedules.size());

        WorkshopSchedule workshop = new WorkshopSchedule(list);
        if(workshop.usesTooMany(limitedUse, verboseSolverLogging))
        {
            if(verboseSolverLogging)
                LOG.info("Not using schedule {} because it uses too many limited use items {}", list, limitedUse);
            return;
        }

        WorkshopValue value = workshop.getValueWithGrooveEstimate(day, groove, restedAlready(), reservedHelpers);

        if(verboseSolverLogging)
            LOG.info("Schedule has value {}", value.getWeighted());

        // Only add if we don't already have one with this schedule or ours is better
        int oldValue = -99999;
        if(safeSchedules.containsKey(workshop))
            oldValue = safeSchedules.get(workshop).getWeighted();

        if (oldValue < value.getWeighted())
        {
            if (verboseSolverLogging && oldValue > 0)
                LOG.info("Replacing schedule with mats " + workshop.rareMaterialsRequired + " with " + list + " because " + value.getWeighted() + " is higher than " + oldValue);

            safeSchedules.remove(workshop); // It doesn't seem to update the key when
                                            // updating the value, so we delete the key
                                            // first
            safeSchedules.put(workshop, value);
        }
        else if(verboseSolverLogging)
        {
            LOG.info("Not replacing because old value {} is higher than {}", oldValue, value.getWeighted());
        }
    }
}
