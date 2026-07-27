package com.effiya.dih.service.helper;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolrApiServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(SolrApiServiceHelper.class);
    private final SolrClient solrClient;

    public SolrApiServiceHelper(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public void clearCore(String coreName) {
        try {
            solrClient.deleteByQuery(coreName, "*:*");
            solrClient.commit(coreName);
            logger.info("Cleared Solr core: {}", coreName);
        } catch (Exception e) {
            logger.error("Failed to clear Solr core: {}", coreName, e);
            throw new RuntimeException("Solr clear core failed", e);
        }
    }

    public void addDocuments(String coreName, List<SolrInputDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            solrClient.add(coreName, documents);
            solrClient.commit(coreName);
            logger.info("Successfully added {} documents to core: {}", documents.size(), coreName);
        } catch (Exception e) {
            logger.error("Failed to add documents to core: {}", coreName, e);
            throw new RuntimeException("Solr add documents failed", e);
        }
    }

    public void deleteDocumentsByIds(String coreName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            org.apache.solr.client.solrj.response.UpdateResponse deleteResponse = solrClient.deleteById(coreName, ids);
            org.apache.solr.client.solrj.response.UpdateResponse commitResponse = solrClient.commit(coreName);
            logger.info("Successfully deleted {} documents from core: {}. Delete status: {}, Commit status: {}", 
                        ids.size(), coreName, deleteResponse.getStatus(), commitResponse.getStatus());
        } catch (Exception e) {
            logger.error("Failed to delete documents from core: {}", coreName, e);
            throw new RuntimeException("Solr delete documents failed", e);
        }
    }
}
