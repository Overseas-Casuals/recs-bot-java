package com.overseascasuals.recsbot.mysql;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface PeakRepository extends CrudRepository<CraftPeaks, PeakID> {
    @Query("SELECT p FROM CraftPeaks p WHERE p.peakID.week = ?1 and p.peakID.day = ?2 order by p.peakID.itemID ASC")
    List<CraftPeaks> findPeaksByDay(int week, int day);
}
