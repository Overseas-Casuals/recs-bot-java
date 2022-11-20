package com.overseascasuals.recsbot.json;

import com.overseascasuals.recsbot.mysql.CraftPeaks;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RestService {

    private final RestTemplate restTemplate;

    public RestService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public String getURLResponse(String url) {
        return this.restTemplate.getForObject(url, String.class);
    }

    public String postPeaks(int week, int day, List<CraftPeaks> peaks)
    {
        StringBuilder peaksb = new StringBuilder();
        for(var peak : peaks)
        {
            peaksb.append(peak.getPeak()).append(',');
        }
        peaksb.setLength(peaksb.length()-1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("week",String.valueOf(week));
        map.add("day",String.valueOf(day));
        map.add("peaks",peaksb.toString());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        return restTemplate.postForObject("http://island.ws:1483/peaks", entity, String.class);
    }

    public String postPopularity(int week, int pop, int nextPop)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("week",String.valueOf(week));
        map.add("pop",String.valueOf(pop));
        map.add("nextPop",String.valueOf(nextPop));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        return restTemplate.postForObject("http://island.ws:1483/pop", entity, String.class);
    }
}