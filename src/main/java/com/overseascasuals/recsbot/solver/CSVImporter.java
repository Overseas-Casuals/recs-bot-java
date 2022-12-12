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
                    itemStr = itemStr.replace("Islefish", "");
                    itemStr = itemStr.replace("Isleberry", "");
                    
                    if(!itemStr.isBlank())
                        items.add(Item.valueOf(itemStr));
                    
                }
                allEfficientChains.add(items);
            }
            LOG.info("Imported "+allEfficientChains.size()+" efficient chains from CSV");
        }
        catch(Exception e)
        {
            System.out.println("Error importing csv: "+e.getMessage());
        }
    }
}