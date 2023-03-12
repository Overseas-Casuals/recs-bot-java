package com.overseascasuals.recsbot.mysql;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface CraftRepository extends CrudRepository<CycleCraft, PeakID>
{
    @Query("SELECT c FROM CycleCraft c WHERE c.craftID.week = ?1 and c.craftID.day = ?2 and c.craftID.rank =?3")
    CycleCraft findCraftsByDay(int week, int day, int rank);
}
