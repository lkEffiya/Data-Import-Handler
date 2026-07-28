package com.effiya.dih.service.helper;

import com.effiya.dih.config.DbConfigProperties;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SolrDocumentServiceHelper {

    public List<SolrInputDocument> convertToSolrDocuments(List<Map<String, Object>> batch, DbConfigProperties.TableConfig tableConfig) {
        List<SolrInputDocument> docs = new ArrayList<>();
        
        if (tableConfig.getColumns() == null || tableConfig.getFields() == null) {
            throw new IllegalArgumentException("Table columns or Solr fields are not defined for table: " + tableConfig.getName());
        }

        String[] dbColumns = tableConfig.getColumns().split(",");
        String[] solrFields = tableConfig.getFields().split(",");

        if (dbColumns.length != solrFields.length) {
            if (!"*".equals(tableConfig.getColumns().trim())) {
                throw new IllegalArgumentException("Mismatch in configured columns and solr fields length for table: " + tableConfig.getName());
            }
        }

        for (Map<String, Object> row : batch) {
            SolrInputDocument doc = new SolrInputDocument();

            if ("*".equals(tableConfig.getColumns().trim())) {
                // If wildcard, map key directly to solr field with same name
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    doc.addField(entry.getKey(), convertValue(entry.getValue()));
                }
            } else {
                for (int i = 0; i < dbColumns.length; i++) {
                    String dbCol = dbColumns[i].trim();
                    String solrField = solrFields[i].trim();
                    Object value = row.get(dbCol);
                    if (value != null) {
                        doc.addField(solrField, convertValue(value));
                    }
                }
            }
            docs.add(doc);
        }

        return docs;
    }

    private Object convertValue(Object value) {
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).toPlainString();
        }
        return value;
    }
}
