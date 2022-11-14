package com.overseascasuals.recsbot.mysql;

import org.springframework.data.repository.CrudRepository;

public interface PopularityRepository extends CrudRepository<Popularity, Integer> {
    Popularity findByWeek(int week);
}
