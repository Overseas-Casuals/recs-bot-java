package com.overseascasuals.recsbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
@ActiveProfiles({"test","live"})
class RecsBotApplicationTests {

	@Test
	void contextLoads() {
	}

}
