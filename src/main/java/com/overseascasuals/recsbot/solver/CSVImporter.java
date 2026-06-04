package com.overseascasuals.recsbot.solver;


import com.overseascasuals.recsbot.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class CSVImporter
{
    Logger LOG = LoggerFactory.getLogger(CSVImporter.class);
    public List<List<Item>> allEfficientChains;
    public Integer[][] popularityRatios;

    public CSVImporter() throws IOException
    {
        allEfficientChains = new ArrayList<>();
        InputStream resource = new ClassPathResource("miennaChains.csv").getInputStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource)))
        {
            String line;
            while ((line = br.readLine()) != null) 
            {
                String[] values = line.split(",");
                List<Item> items = new ArrayList<>();
                for(String itemStr : values)
                {
                    itemStr = itemStr.replace(" ", "");
                    itemStr = itemStr.replace("'", "");
                    
                    if(!itemStr.isBlank())
                        items.add(Item.getEnum(itemStr));
                    
                }
                allEfficientChains.add(items);
            }
            LOG.info("Imported "+allEfficientChains.size()+" efficient chains from CSV");
        }
        catch(Exception e)
        {
            System.out.println("Error importing chain csv: "+e.getMessage());
        }
        popularityRatios = new Integer[100][Solver.items.length];
        int[] ratios = {0, 140, 120, 100, 80};

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource("popularity.csv").getInputStream())))
        {
            int index = 0;
            String line;
            while ((line = br.readLine()) != null)
            {

                String[] values = line.split(",");
                for(int item = 0; item < Solver.items.length; item++)
                {
                    popularityRatios[index][item] = ratios[Integer.parseInt(values[item])];
                }
                index++;
            }
        }
        catch(Exception e)
        {
            System.out.println("Error importing popularity csv: "+e.getMessage());
        }

    }
}