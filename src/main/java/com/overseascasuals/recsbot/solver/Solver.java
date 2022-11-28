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
    private static int islandRank;

    @Autowired
    private void setIslandRank(@Value("${solver.island.rank}")int rank) {islandRank = rank;}
    public static double materialWeight;
    @Autowired
    private void setMaterialWeight(@Value("${solver.materialWeight}")double weight) {materialWeight = weight;}
    private static int alternatives;
    @Autowired
    private void setNumAlternatives(@Value("${solver.alternatives}")int numAlts) {alternatives = numAlts;}
    private static boolean valuePerHour = true;
    private static int itemsToReserve;
    @Autowired
    private void setItemsToReserve(@Value("${solver.itemsToReserve}")int items) {itemsToReserve = items;}


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
            new ItemInfo(Crook,Arms,Woodworks,120,8,9,Map.of(Fang,4))};
    
    private int groove = 0;
    private int totalGross = 0;
    private int totalNet = 0;

    private final static ObjectMapper objectMapper = new ObjectMapper();
    public int rested = -1;
    private boolean guaranteeRestD5 = false;
    private Set<Item> reservedItems = new HashSet<>();
    private Map<Item, Item> reservedHelpers = new HashMap<>();
    private Map<Item, Boolean> d2Troublemakers;

    private List<Item> c2Crafts;
    private List<Item> c3Crafts;

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

    public List<DailyRecommendation> redoDay2Recs()
    {
        return getDailyRecommendations(week, 0, false);
    }
    public List<DailyRecommendation> getDailyRecommendations(int week, int day, boolean hardRefresh)
    {
        LOG.info("Getting recommendations for week {} day {}, hardrefresh? {}. helper penalty {}", week, day, hardRefresh, helperPenalty);
        if(hardRefresh || this.week != week)
        {
            rested = -1;
            groove = 0;
            reservedItems.clear();
            d2Troublemakers = null;
            int currentPop = popularityRepository.findByWeek(week).getPopularity();
            String popResponse = restService.getURLResponse("https://xivapi.com/MJICraftworksPopularity/"+currentPop);

            PopularityJson popJson = null;
            try {
                popJson = objectMapper.readValue(popResponse, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            var peaks = peakRepository.findPeaksByDay(week, Math.min(day,3));

            for(int i=0;i<items.length;i++)
            {
                items[i].setInitialData(popJson.getPopularities()[i].getRatio(), peaks.get(i).getPeakEnum());
            }

            //Load previous crafts from db
            for(int i=1; i<=day; i++)
            {
                CycleCraft crafts = craftRepository.findCraftsByDay(week, i);
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
                    if(i==1)
                        c2Crafts = new ArrayList<>();
                    else if(i==2)
                        c3Crafts = new ArrayList<>();
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
                        if(i==1)
                            c2Crafts.add(item);
                        else if(i==2)
                            c3Crafts.add(item);
                    }
                    groove = Math.min(groove, GROOVE_MAX);
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
            if((day == 1 || day == 2) && rested != day) //The only days when pre-peaks are unknown
            {
                List<Item> currentCrafts;
                if(day == 1)
                    currentCrafts = c2Crafts;
                else
                    currentCrafts = c3Crafts;

                int grooveMadeToday = (currentCrafts.size() - 1) * NUM_WORKSHOPS;

                LOG.info("Rechecking day {}'s recs starting at {} groove", day+1, groove - grooveMadeToday);


                var newBest = getBestBruteForceSchedules(day, groove-grooveMadeToday,
                        null, day, 1, currentCrafts.get(0));

                if(!newBest.get(0).getKey().getItems().equals(currentCrafts))
                {
                    LOG.info("Schedule updated detected for day {}! Now crafting {}", day+1,
                            Arrays.toString(newBest.get(0).getKey().getItems().toArray()));

                    CycleSchedule newSched = new CycleSchedule(day, groove-grooveMadeToday);
                    newSched.setForAllWorkshops(newBest.get(0).getKey().getItems());
                    listOfRecs.add(new DailyRecommendation(day, newBest, newSched));
                    addCraftedFromCycle(day, newSched);
                }
            }

            DailyRecommendation rec;
            int dayToSolve = day+1;
            if(rested == dayToSolve)
                rested = -1;
            LOG.info("Solving recs for day {}",(dayToSolve+1));
            var todayRecs =  getBestBruteForceSchedules(dayToSolve, groove,
                    null, dayToSolve, alternatives);
            var bestSchedule = todayRecs.get(0);
            boolean shouldRest = false;
            CycleSchedule schedule = new CycleSchedule(dayToSolve, groove);
            schedule.setForAllWorkshops(bestSchedule.getKey().getItems());

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
                rested = dayToSolve;
            }
            else
            {
                rec = new DailyRecommendation(dayToSolve, todayRecs, schedule);
                if(dayToSolve == 1)
                    c2Crafts = schedule.getItems();
                else if(dayToSolve == 2)
                    c3Crafts = schedule.getItems();
            }

            listOfRecs.add(rec);

            if(dayToSolve==1)
                rec.setTroublemakers(getTentativeD2Items(bestSchedule.getValue().getWeighted()));


            LOG.info("{}", rec);
            addCraftedFromCycle(rec.getDay(), rec.getBestRec());
        }
        else
        {
            //Try days 5-7
            listOfRecs = getLateDays();
        }

        return listOfRecs;
    }

    private void addCraftedFromCycle(int day, CycleSchedule schedule)
    {
        addCraftedFromCycle(day, schedule, true);
    }
    private void addCraftedFromCycle(int day, CycleSchedule schedule, boolean real)
    {
        if(schedule!=null)
        {
            LOG.info("Setting info for cycle schedule {} (real? {})", schedule, real);
            if(schedule.numCrafted == null)
                schedule.getValue();
            Arrays.stream(items).forEach(item -> item.setCrafted(schedule.numCrafted.getOrDefault(item.item, 0), schedule.day));

            groove = schedule.endingGroove;
        }

        if(real)
        {
            CycleCraft crafts = new CycleCraft();
            crafts.setCraftID(new CraftID(week, day));
            if(schedule!=null)
                crafts.setCrafts(schedule.getItems());
            else
                crafts.setCrafts(new ArrayList<>());
            craftRepository.save(crafts);
        }
    }

    public boolean updatePeak(Item item, PeakCycle peak)
    {
        if(d2Troublemakers.containsKey(item))
        {
            groove = 0;
            d2Troublemakers.put(item, true);
            items[item.ordinal()].peak = peak;
            CraftPeaks singlePeak = new CraftPeaks();
            singlePeak.setPeakFromEnum(peak);
            singlePeak.setPeakID(new PeakID(week, day, item.ordinal()+1));
            peakRepository.save(singlePeak);
            return true;
        }
        return false;
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
        var bestItemsEntries = bestItems.entrySet();
        Iterator<Entry<Item, Integer>> itemIterator = bestItems.entrySet().iterator();

        List<Item> itemsThatGetReservations = new ArrayList<>();
        for(int i=0;i<itemsToReserve && itemIterator.hasNext(); i++)
        {
            Item next = itemIterator.next().getKey();
            LOG.info("Reserving item {}", next);
            reservedItems.add(next);
            if(i<10)
                itemsThatGetReservations.add(next);
        }

        reservedHelpers.clear();
        for(int i=0;i<itemsThatGetReservations.size();i++)
        {
            Item itemEnum = itemsThatGetReservations.get(i);
            ItemInfo mainItem = items[itemEnum.ordinal()];
            if(mainItem.time != 8)
                continue;
            int bestValue = 0;
            Item bestHelper = Macuahuitl; //This is the most useless thing I can think of
            boolean hasMultiple = false;
            for(ItemInfo helper : items)
            {
                if(helper.time != 4 || !helper.getsEfficiencyBonus(mainItem))
                    continue;

                int value = helper.getValueWithSupply(Supply.Sufficient);
                if(value > bestValue)
                {
                    hasMultiple = false;
                    bestValue = value;
                    bestHelper = helper.item;
                }
                else if(value == bestValue)
                {
                    hasMultiple = true;
                }
            }
            if(!hasMultiple)
            {
                LOG.info("Reserving helper {} to go with main item {}", bestHelper, itemEnum);
                reservedHelpers.put(itemEnum, bestHelper);
            }
            else
            {
                LOG.info("Main item {} has multiple best helpers, so it gets none", itemEnum);
            }
        }
    }


    private void printRestDayInfo(List<Item> rec, int day)
    {
        CycleSchedule restedDay = new CycleSchedule(day, 0);
        restedDay.setForAllWorkshops(rec);
        LOG.info("Rest day " + (day + 1)+". Think we'll make more than "
                + restedDay.getValue() + " grooveless with " + rec + ". ");
    }

    private void addDailyRecToList(List<Entry<WorkshopSchedule, WorkshopValue>> recs, int day, int groove, List<DailyRecommendation> recommendations)
    {
        CycleSchedule bestSchedule = new CycleSchedule(day, groove);
        bestSchedule.setForAllWorkshops(recs.get(0).getKey().getItems());
        addCraftedFromCycle(day, bestSchedule);
        recommendations.add(new DailyRecommendation(day, recs, bestSchedule));
    }
    private void addRestToList(List<Entry<WorkshopSchedule, WorkshopValue>> recs, int day, List<DailyRecommendation> recommendations)
    {
        addCraftedFromCycle(day, null);
        recommendations.add(new DailyRecommendation(day, recs));
    }
    private List<DailyRecommendation> getLateDays()
    {
        List<DailyRecommendation> recommendations = new ArrayList<>();
        int startingGroove = groove;
        var cycle5Sched = getBestBruteForceSchedules(4, startingGroove, null, 4, alternatives);
        var cycle6Sched = getBestBruteForceSchedules(5, startingGroove, null, 5, alternatives);
        var cycle7Sched = getBestBruteForceSchedules(6, startingGroove, null, 6, alternatives);

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

        if (bestDay == 4) // Day 5 is best
        {
            // System.out.println("Day 5 is best. Adding as-is");
            addDailyRecToList(cycle5Sched, 4, startingGroove, recommendations);

            startingGroove = groove;
            cycle6Sched = getBestBruteForceSchedules(5, startingGroove, null, 5, alternatives);
            cycle7Sched = getBestBruteForceSchedules(6, startingGroove, null, 6, alternatives);
            if(cycle6Sched.get(0).getValue().getWeighted() > cycle7Sched.get(0).getValue().getWeighted())
            {
                if(rested == -1)
                {
                    //Recalc again to include d7 (since we need to rest then)
                    cycle6Sched = getBestBruteForceSchedules(5, startingGroove, null, 6, alternatives);

                    addDailyRecToList(cycle6Sched, 5, startingGroove, recommendations);
                    addRestToList(getBestBruteForceSchedules(6, startingGroove, null, 6, alternatives), 6, recommendations);

                }
                else
                {
                    addDailyRecToList(cycle6Sched, 5, startingGroove, recommendations);
                    addDailyRecToList(getBestBruteForceSchedules(6, groove, null, 6, alternatives), 6, groove, recommendations);
                }
            }
            else
            {
                if(rested == -1)
                    addRestToList(cycle6Sched, 5, recommendations);
                else
                {
                    Map<Item,Integer> reserved7Set = cycle7Sched.get(0).getKey().getLimitedUses();
                    addDailyRecToList(getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives), 5, startingGroove, recommendations);
                }

                addDailyRecToList(getBestBruteForceSchedules(6, groove, null, 6, alternatives), 6, groove, recommendations);
            }
        }
        else if (bestDay == 6) // Day 7 is best
        {
            //System.out.println("Day 7 is best");
            Map<Item,Integer> reserved7Set = cycle7Sched.get(0).getKey().getLimitedUses();

            if(rested == -1)//We only care about one of 5 or 6
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 6, alternatives);
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove, reserved7Set, 6, alternatives);

                if(cycle5Sched.get(0).getValue().getWeighted() > cycle6Sched.get(0).getValue().getWeighted())
                {
                    addDailyRecToList(cycle5Sched, 4, startingGroove, recommendations);
                    addRestToList(cycle6Sched, 5, recommendations);
                }
                else
                {
                    addRestToList(cycle5Sched, 4, recommendations);
                    addDailyRecToList(cycle6Sched, 5, startingGroove, recommendations);
                }
            }
            else
            {
                cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved7Set, 4, alternatives);

                int total65 = 0;
                int grooveFrom5 = (cycle5Sched.get(0).getKey().getItems().size() - 1) * NUM_WORKSHOPS;
                cycle6Sched = getBestBruteForceSchedules(5, startingGroove + grooveFrom5, reserved7Set, 6, alternatives);
                //try deriving 5 from 6
                Map<Item,Integer> reserved67Items = cycle6Sched.get(0).getKey().getLimitedUses(reserved7Set);
                var recalcedCycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved67Items, 6, alternatives);
                CycleSchedule best65 = new CycleSchedule(4, startingGroove);
                best65.setForAllWorkshops(recalcedCycle5Sched.get(0).getKey().getItems());
                total65 += best65.getValue() * 2 - best65.getMaterialCost();
                addCraftedFromCycle(4, best65, false);
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
                addCraftedFromCycle(4, best5, false);

                var basedOn56Sched = getBestBruteForceSchedules(5, groove, reserved7Set, 6, alternatives);

                CycleSchedule best56 = new CycleSchedule(5, groove);
                best56.setForAllWorkshops(basedOn56Sched.get(0).getKey().getItems());
                total56 += best56.getValue() * 2 - best56.getMaterialCost();

                /*System.out.println("Trying to prioritize day 5:"+Arrays.toString(cycle5Sched.getKey().getItems().toArray())
                        +" ("+cycle5Sched.getValue()+"), so day 6: "+Arrays.toString(basedOn56Sched.getKey().getItems().toArray())
                        +" ("+basedOn56Sched.getValue()+") total: "+total56);*/

                if(total65 > total56)
                {
                    //System.out.println("Basing on 6 is better");
                    addDailyRecToList(recalcedCycle5Sched, 4, startingGroove, recommendations);
                    addDailyRecToList(cycle6Sched, 5, groove, recommendations);
                }
                else
                {
                    //System.out.println("Basing on 5 is better");
                    addDailyRecToList(cycle5Sched, 4, startingGroove, recommendations);
                    addDailyRecToList(basedOn56Sched, 5, groove, recommendations);
                }
            }

            addDailyRecToList(getBestBruteForceSchedules(6, groove, null, 6, alternatives), 6, groove, recommendations);
        }
        else // Best day is Day 6
        {
            //System.out.println("Day 6 is best");
            CycleSchedule best6 = new CycleSchedule(5, startingGroove);
            best6.setForAllWorkshops(cycle6Sched.get(0).getKey().getItems());
            addCraftedFromCycle(5, best6, false);

            Map<Item,Integer> reserved6 = cycle6Sched.get(0).getKey().getLimitedUses();
            //System.out.println("Recalcing D5 allowing D6's items");

            cycle5Sched = getBestBruteForceSchedules(4, startingGroove, reserved6, 5, alternatives);
            cycle7Sched = getBestBruteForceSchedules(6, startingGroove, null, 6, alternatives);
            /*System.out.println("c5 sched:" +Arrays.toString(recalcedCycle5Sched.getKey().getItems().toArray())+ " ("
                    +recalcedCycle5Sched.getValue()+") compared to c7: "+Arrays.toString(recalcedCycle7Sched.getKey().getItems().toArray())
            +" ("+recalcedCycle7Sched.getValue()+")");*/

            int worstDay;
            if(cycle5Sched.get(0).getValue().getWeighted() > cycle7Sched.get(0).getValue().getWeighted())
                worstDay = 6;
            else
                worstDay = 4;


            if (rested > 0 || worstDay != 4)
            {
                addDailyRecToList(cycle5Sched, 4, startingGroove, recommendations);
                addDailyRecToList(getBestBruteForceSchedules(5, groove, null, 5, alternatives), 5, groove, recommendations);
            }
            else
            {
                addRestToList(cycle5Sched, 4, recommendations);
                addDailyRecToList(cycle6Sched, 5, startingGroove, recommendations);
            }

            cycle7Sched = getBestBruteForceSchedules(6, groove, null, 6, alternatives);
            if (rested > 0)
            {
                //System.out.println("Recalcing day 7 and adding");
                addDailyRecToList(cycle7Sched, 6, groove, recommendations);
            }
            else
                addRestToList(cycle7Sched, 6, recommendations);
        }
        return recommendations;
    }
    
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
        int weightedValue = rec.getValue().getWeighted();
        LOG.debug("Comparing d" + (day + 1) + " (" + weightedValue + ") to worst-case future days");
        
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

        return weightedValue <= worstInFuture;
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

            int toAdd = solution.getKey().getValueWithGrooveEstimate(4, groove, rested >= 0, reservedHelpers).getWeighted();
            LOG.trace("Permutation " + p + " has value " + toAdd);
            sum += toAdd;
        }

        LOG.trace("Sum: " + sum + " average: " + sum / permutations);
        sum /= permutations;
        WorkshopValue value = new WorkshopValue(sum, 0, 0, 0);
        solution.setValue(value);

        for (ItemInfo item : c5Peaks)
        {
            item.peak = Cycle5; // Set back to normal
        }

        return solution;
    }

    public boolean hasTentativeD2()
    {
        LOG.info("Day {} troublemakers: {}", day, d2Troublemakers==null?"null":String.valueOf(d2Troublemakers.size()));
        return day==0 && d2Troublemakers != null && d2Troublemakers.size() > 0;
    }

    public boolean allTentativeD2Set()
    {
        if(!hasTentativeD2())
            return true;

        for(var value : d2Troublemakers.values())
            if(!value)
                return false;

        return true;
    }

    public Map<Item, Boolean> getTentativeD2Items(int valueToCompare)
    {
        if(d2Troublemakers != null)
            return d2Troublemakers;

        Map<Item, Integer> troubleValues = new HashMap<>();
        List<ItemInfo> c2Unknowns = new ArrayList<>();
        for (ItemInfo item : items)
            if (item.peak == Cycle2Unknown)
                c2Unknowns.add(item);

        LOG.info("Checking {} unknown D2 peaks.", c2Unknowns.size());

        d2Troublemakers = new HashMap<>();
        for (int i = 0; i < c2Unknowns.size(); i++) {
            c2Unknowns.get(i).peak = Cycle2Strong;
            LOG.info("Setting {} to strong peak", c2Unknowns.get(i).item);
            var schedule = getBestSchedule(1, 0, null);
            int value = schedule.getValue().getWeighted();
            if (value > valueToCompare) {
                LOG.info("{} could star in a higher value schedule", c2Unknowns.get(i).item.getDisplayName());
                troubleValues.put(c2Unknowns.get(i).item, value);
            }
            c2Unknowns.get(i).peak = Cycle2Unknown;
        }

        for(var troublemaker : troubleValues.entrySet())
        {
            items[troublemaker.getKey().ordinal()].peak = Cycle2Strong;
            for (int i = 0; i < c2Unknowns.size(); i++)
            {
                if(troubleValues.containsKey(c2Unknowns.get(i).item)) //If we already know it's a problem, skip it
                    continue;
                c2Unknowns.get(i).peak = Cycle2Strong;
                LOG.info("Setting {} to strong peak to complement {}", c2Unknowns.get(i).item.getDisplayName(), troublemaker.getKey().getDisplayName());
                var schedule = getBestSchedule(1,0,null);
                int value = schedule.getValue().getWeighted();
                if(value>troublemaker.getValue())
                {
                    LOG.info("{} could help {} get to even greater heights!", c2Unknowns.get(i).item.getDisplayName(), troublemaker.getKey().getDisplayName());
                    d2Troublemakers.put(c2Unknowns.get(i).item, false);
                }
                c2Unknowns.get(i).peak = Cycle2Unknown;
            }
            items[troublemaker.getKey().ordinal()].peak = Cycle2Unknown;
        }

        for(var value : troubleValues.keySet())
            d2Troublemakers.put(value, false);
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
        return getBestBruteForceSchedules(day, groove, limitedUse, allowUpToDay, numToReturn, null);
    }

    private List<Map.Entry<WorkshopSchedule, WorkshopValue>> getBestBruteForceSchedules(int day, int groove,
            Map<Item,Integer> limitedUse, int allowUpToDay, int numToReturn, Item startingItem)
    {

        HashMap<WorkshopSchedule, WorkshopValue> safeSchedules = new HashMap<>();
        List<List<Item>> filteredItemLists;


        filteredItemLists = csvImporter.allEfficientChains.stream()
                .filter(list -> list.stream().allMatch(
                        item -> items[item.ordinal()].rankUnlocked <= islandRank))
                .filter(list -> list.stream().allMatch(
                        item -> items[item.ordinal()].peaksOnOrBeforeDay(allowUpToDay, reservedItems)))
                .collect(Collectors.toList());

        if(startingItem != null)
            filteredItemLists = filteredItemLists.stream().filter (list -> list.stream().limit(1)
                    .allMatch(item -> item == startingItem)).collect(Collectors.toList());



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
        
        WorkshopValue value = workshop.getValueWithGrooveEstimate(day, groove, rested >= 0, reservedHelpers);
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
