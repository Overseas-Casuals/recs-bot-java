package com.overseascasuals.recsbot.mysql;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;


public interface CraftRepository extends CrudRepository<CraftPeaks, PeakID>
{
    @Query("SELECT c FROM CycleCraft c WHERE c.craftID.week = ?1 and c.craftID.day = ?2")
    CycleCraft findCraftsByDay(int week, int day);
}
