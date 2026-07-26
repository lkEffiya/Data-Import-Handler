package com.effiya.dih.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolrConfig {

    @Value("${app.solr.url}")
    private String solrUrl;

    @Bean
    public SolrClient solrClient() {
        return new HttpJdkSolrClient.Builder(solrUrl).useHttp1_1(true).build();
    }
}
