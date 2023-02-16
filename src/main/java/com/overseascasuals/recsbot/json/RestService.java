package com.overseascasuals.recsbot.json;

import com.overseascasuals.recsbot.mysql.CraftPeaks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RestService {
    @Value("${peakDB.url}")
    private String peakDbURL;
    private final RestTemplate restTemplate;

    public RestService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String getURLResponse(String url) throws RestClientException {
        return this.restTemplate.getForObject(url, String.class);
    }

    public String postPeaks(int week, int day, List<CraftPeaks> peaks) throws RestClientException
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

        return restTemplate.postForObject(peakDbURL+"/peaks", entity, String.class);
    }

    public String postPopularity(int week, int pop, int nextPop) throws RestClientException
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("week",String.valueOf(week));
        map.add("pop",String.valueOf(pop));
        map.add("nextPop",String.valueOf(nextPop));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        return restTemplate.postForObject(peakDbURL+"/pop", entity, String.class);
    }

    public String postRestart() throws RestClientException
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("key","Zac4@^dOUlJGeKLnR%&EcPdD");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        return restTemplate.postForObject(peakDbURL+"/tryHandle", entity, String.class);
    }
}