package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.Solver;

public enum Item
{
    Potion("Potion","<:OC_Potion:1035399553551188008>"),
    Firesand("Firesand","<:OC_Firesand:1035379578643951687>"),
    WoodenChair("Wooden Chair","<:OC_WoodenChair:1035379564521726012>"),
    GrilledClam("Grilled Clam","<:OC_GrilledClam:1035379585564545054>"),
    Necklace("Necklace","<:OC_Necklace:1035399543170277417>"),
    CoralRing("Coral Ring","<:OC_CoralRing:1035379567919116290>"),
    Barbut("Barbut","<:OC_Barbut:1035379551351619604>"),
    Macuahuitl("Macuahuitl","<:OC_Macuahuitl:1035379597828694146>"),
    Sauerkraut("Sauerkraut","<:OC_Sauerkraut:1035399564041129985>"),
    BakedPumpkin("Baked Pumpkin","<:OC_BakedPumpkin:1035379550433058826>"),
    Tunic("Tunic","<:OC_Tunic:1035399586438717490>"),
    CulinaryKnife("Culinary Knife","<:OC_CulinaryKnife:1035379596314550272>"),
    Brush("Brush","<:OC_Brush:1035379557081034753>"),
    BoiledEgg("Boiled Egg","<:OC_BoiledEgg:1035379553977237534>"),
    Hora("Hora","<:OC_Hora:1035379589016469524>"),
    Earrings("Earrings","<:OC_Earrings:1035379573514317864>"),
    Butter("Butter","<:OC_Butter:1035379558096056400>"),
    BrickCounter("Brick Counter","<:OC_BrickCounter:1035379554862256268>"),
    BronzeSheep("Bronze Sheep","<:OC_BronzeSheep:1035379556258959412>"),
    GrowthFormula("Growth Formula","<:OC_GrowthFormula:1035379586831241276>"),
    GarnetRapier("Garnet Rapier","<:OC_GarnetRapier:1035399557305073705>"),
    SpruceRoundShield("Spruce Round Shield","<:OC_SpruceRoundShield:1035399577064443995>"),
    SharkOil("Shark Oil","<:OC_SharkOil:1035399570831724645>"),
    SilverEarCuffs("Silver Ear Cuffs","<:OC_SilverEarCuffs:1035399573595770961>"),
    SweetPopoto("Sweet Popoto","<:OC_SweetPopoto:1035399583959883786>"),
    ParsnipSalad("Parsnip Salad","<:OC_ParsnipSalad:1035399548253777931>"),
    Caramels("Caramels","<:OC_Caramels:1035379560826548254>"),
    Ribbon("Ribbon","<:OC_Ribbon:1035399558294933584>"),
    Rope("Rope","<:OC_Rope:1035399559351894107>"),
    CavaliersHat("Cavalier's Hat","<:OC_CavaliersHat:1035379562919510017>"),
    Horn("Horn","<:OC_HornCraft:1035379590748713140>"),
    SaltCod("Salt Cod","<:OC_SaltCod:1035399561914622023>"),
    SquidInk("Squid Ink","<:OC_SquidInk:1035399578704433222>"),
    EssentialDraught("Essential Draught","<:OC_EssentialDraught:1035379572281200660>"),
    Jam("Jam","<:OC_IsleberryJam:1035379595232428112>"),
    TomatoRelish("Tomato Relish","<:OC_TomatoRelish:1035399585184624750>"),
    OnionSoup("Onion Soup","<:OC_OnionSoup:1035399544957063168>"),
    Pie("Pie","<:OC_IslefishPie:1035399552523567124>"),
    CornFlakes("Corn Flakes","<:OC_CornFlakes:1035379569131262003>"),
    PickledRadish("Pickled Radish","<:OC_PickledRadish:1035399551605018674>"),
    IronAxe("Iron Axe","<:OC_IronAxe:1035379594217406525>"),
    QuartzRing("Quartz Ring","<:OC_QuartzRing:1035399556126490644>"),
    PorcelainVase("Porcelain Vase","<:OC_PorcelainVase:1035399589303435325>"),
    VegetableJuice("Vegetable Juice","<:OC_VegetableJuice:1035399590599479386>"),
    PumpkinPudding("Pumpkin Pudding","<:OC_PumpkinPudding:1035399554864005140>"),
    SheepfluffRug("Sheepfluff Rug","<:OC_SheepfluffRug:1035399561285488700>"),
    GardenScythe("Garden Scythe","<:OC_GardenScythe:1035399566972964894>"),
    Bed("Bed","<:OC_Bed:1035379553197113415>"),
    ScaleFingers("Scale Fingers","<:OC_ScaleFingers:1035399565655953509>"),
    Crook("Crook","<:OC_Crook:1035379570284703825>"),
    CoralSword("Coral Sword","<:OC_CoralSword:1062408240165564457>"),
    CoconutJuice("Coconut Juice","<:OC_CoconutJuice:1062407775336013826>"),
    Honey("Honey","<:OC_Honey:1062407933381578883>"),
    SeashineOpal("Seashine Opal","<:OC_SeashineOpal:1062408088864423976>"),
    DriedFlowers("Dried Flowers","<:OC_DriedFlowers:1062408379424833627>"),
    PowderedPaprika("Powdered Paprika","<:OC_PowderedPaprika:1062408479622582362>"),
    CawlCennin("Cawl Cennin","<:OC_CawlCennin:1062408571255525467>"),
    Isloaf("Isloaf","<:OC_Isloaf:1062408660329975818>"),
    PopotoSalad("Popoto Salad","<:OC_PopotoSalad:1062408751186980915>"),
    Dressing("Dressing","<:OC_Dressing:1062408845265207366>"),
    Stove ("Stove", "<:OC_Stove:1110495905741799544>"),
    Lantern ("Lantern","<:OC_Lantern:1110495841342476320>"),
    Natron ("Natron","<:OC_Natron:1110495854709719050>"),
    Bouillabaisse("Bouillabaisse","<:OC_Bouillabaisse:1110495781414256677>"),
    FossilDisplay("Fossil Display","<:OC_FossilDisplay:1110495810237509745>"),
    Bathtub ("Bathtub","<:OC_Bathtub:1110495742847619123>"),
    Spectacles("Spectacles","<:OC_Spectacles:1110495893444112394>"),
    CoolingGlass("Cooling Glass","<:OC_CoolingGlass:1110495797017059389>"),
    RunnerBeanSaute("Runner Bean Saute","<:OC_RunnerBeanSaute:1110495881402253392>"),
    BeetSoup("Beet Soup","<:OC_BeetSoup:1110495769246572654>"),
    ImamBayildi("Imam Bayildi","<:OC_ImamBayildi:1110495825127292958>"),
    PickledZucchini("Pickled Zucchini","<:OC_PickledZucchini:1110495869024874509>"),
    ServingDish("Serving Dish", "<:OC_BrassServingDish:1158630308988596264>"),
    GrindingWheel("Grinding Wheel", "<:OC_GrindingWheel:1158630384049864857>"),
    Tathlums("Tathlums", "<:OC_DuriumThathlums:1158630360578539520>"),
    GoldHairpin("Gold Hairpin", "<:OC_SeafarerCowrie:1109399604203626536>"),
    MammetAward("Mammet Award", "<:OC_SeafarerCowrie:1109399604203626536>"),
    FruitPunch("Fruit Punch", "<:OC_SeafarerCowrie:1109399604203626536>"),
    SweetPopotoPie("Sweet Popoto Pie", "<:OC_SeafarerCowrie:1109399604203626536>"),
    Peperoncino("Peperoncino", "<:OC_SeafarerCowrie:1109399604203626536>"),
    BuffaloBeanSalad("Buffalo Bean Salad", "<:OC_SeafarerCowrie:1109399604203626536>");
    
    private String displayName;
    private String emoji;
    private Item(String display, String emoji)
    {
        displayName = display;
        this.emoji = emoji;
    }

    public String getDisplayName() { return displayName; }

    public String getDisplayNameWithTime() { return displayName + " ("+Solver.getHoursForItem(this)+"h)";}
    public String getDisplayNameWithEmoji() { return emoji + " " + displayName; }
    public String getDisplayWithEmojiAndTime() { return emoji + " " + displayName + " ("+Solver.getHoursForItem(this)+"h)"; }
    public String getEmoji() { return emoji; }
}
