package com.cz.webmaster.service;

import java.util.List;
import java.util.Map;

public interface LegacyCrudService {

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

    boolean supportsFamily(String family);

    String validateForSave(String family, Map<String, Object> body);

    String validateForUpdate(String family, Map<String, Object> body);

    PageResult list(String family, String keyword, int offset, int limit);

    Map<String, Object> info(String family, Long id);

    boolean save(String family, Map<String, Object> body, Long operatorId);

    boolean update(String family, Map<String, Object> body, Long operatorId);

    boolean deleteBatch(String family, List<Long> ids);
}
