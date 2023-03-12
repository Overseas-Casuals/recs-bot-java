package com.overseascasuals.recsbot.mysql;

import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface PopularityRepository extends CrudRepository<Popularity, Integer> {
    Popularity findByWeek(int week);
}
