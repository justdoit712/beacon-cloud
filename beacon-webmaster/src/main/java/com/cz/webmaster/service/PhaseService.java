package com.cz.webmaster.service;

import java.util.List;
import java.util.Map;

public interface PhaseService {

    class PageResult {
        private final long total;
        private final List<Map<String, Object>> rows;

        public PageResult(long total, List<Map<String, Object>> rows) {
            this.total = total;
            this.rows = rows;
        }

        public long getTotal() {
            return total;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }
    }

    PageResult list(String keyword, int offset, int limit);

    Map<String, Object> info(Long id);

    String validateForSave(Map<String, Object> body);

    String validateForUpdate(Map<String, Object> body);

    boolean save(Map<String, Object> body, Long operatorId);

    boolean update(Map<String, Object> body, Long operatorId);

    boolean deleteBatch(List<Long> ids);

    List<Map<String, Object>> allProvinces();

    List<Map<String, Object>> allCities(Long provId);
}

