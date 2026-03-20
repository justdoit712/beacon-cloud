package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.service.AcountService;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.ClientBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AcountServiceImpl implements AcountService {

    private final ConcurrentMap<Long, Map<String, Object>> dataStore = new ConcurrentHashMap<>();

    @Autowired
    private ClientBusinessService clientBusinessService;
    @Autowired
    private BalanceCommandService balanceCommandService;

    @Override
    public PageResult list(String keyword, int offset, int limit) {
        List<Map<String, Object>> all = new ArrayList<>(dataStore.values());
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (matches(row, normalizedKeyword)) {
                filtered.add(copy(row));
            }
        }
        filtered.sort(Comparator.comparingLong(this::idOf).reversed());

        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.max(limit, 0);
        int fromIndex = Math.min(safeOffset, filtered.size());
        int toIndex = Math.min(fromIndex + safeLimit, filtered.size());

        List<Map<String, Object>> rows = safeLimit == 0 ? new ArrayList<>() : filtered.subList(fromIndex, toIndex);
        return new PageResult(filtered.size(), rows);
    }

    @Override
    public Map<String, Object> info(Long id) {
        if (id == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> row = dataStore.get(id);
        return row == null ? new LinkedHashMap<>() : copy(row);
    }

    @Override
    public String validateForSave(Map<String, Object> body) {
        if (body == null) {
            return "request body is required";
        }
        Long clientId = toLong(body.get("clientid"));
        Long paidValue = toLong(body.get("paidvalue"));
        if (clientId == null) {
            return "clientid is required";
        }
        if (paidValue == null || paidValue <= 0) {
            return "paidvalue must be greater than 0";
        }
        return null;
    }

    @Override
    public String validateForUpdate(Map<String, Object> body) {
        String validateResult = validateForSave(body);
        if (validateResult != null) {
            return validateResult;
        }
        Long id = toLong(body.get("id"));
        if (id == null) {
            return "id is required";
        }
        return null;
    }

    @Override
    public boolean save(Map<String, Object> body, Long operatorId) {
        Long clientId = toLong(body.get("clientid"));
        Long paidValue = toLong(body.get("paidvalue"));
        if (clientId == null || paidValue == null || paidValue <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long id = toLong(body.get("id"));
        if (id == null) {
            id = IdUtil.getSnowflakeNextId();
        }

        Map<String, Object> row = new LinkedHashMap<>(body);
        row.put("id", id);
        row.put("clientid", clientId);
        row.put("paidvalue", paidValue);
        row.put("orderid", valueOrDefault(row.get("orderid"), "ORD" + id));
        row.put("createtime", valueOrDefault(row.get("createtime"), now));
        row.put("paytime", valueOrDefault(row.get("paytime"), now));
        row.put("paymentid", valueOrDefault(row.get("paymentid"), "2"));
        row.put("paymentorder", valueOrDefault(row.get("paymentorder"), "PAY" + id));
        row.put("paymentinfo", valueOrDefault(row.get("paymentinfo"), "manual recharge"));
        row.put("created", valueOrDefault(row.get("created"), now));
        row.put("updated", now);
        if (operatorId != null) {
            row.put("createId", valueOrDefault(row.get("createId"), operatorId));
            row.put("updateId", operatorId);
        }

        ClientBusiness clientBusiness = clientBusinessService.findById(clientId);
        if (clientBusiness != null) {
            row.put("corpname", clientBusiness.getCorpname());
            if (!increaseClientMoney(clientBusiness, paidValue, operatorId)) {
                return false;
            }
        } else {
            row.put("corpname", valueOrDefault(row.get("corpname"), ""));
        }

        dataStore.put(id, row);
        return true;
    }

    @Override
    public boolean update(Map<String, Object> body, Long operatorId) {
        Long id = toLong(body.get("id"));
        if (id == null) {
            return false;
        }
        Map<String, Object> current = dataStore.get(id);
        if (current == null) {
            return false;
        }
        Long clientId = toLong(body.get("clientid"));
        Long paidValue = toLong(body.get("paidvalue"));
        if (clientId == null || paidValue == null || paidValue <= 0) {
            return false;
        }

        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(body);
        merged.put("id", id);
        merged.put("clientid", clientId);
        merged.put("paidvalue", paidValue);
        merged.put("updated", System.currentTimeMillis());
        if (operatorId != null) {
            merged.put("updateId", operatorId);
        }

        ClientBusiness clientBusiness = clientBusinessService.findById(clientId);
        if (clientBusiness != null) {
            merged.put("corpname", clientBusiness.getCorpname());
        }

        dataStore.put(id, merged);
        return true;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (Long id : ids) {
            if (id != null && dataStore.remove(id) != null) {
                removed = true;
            }
        }
        return removed;
    }

    private boolean increaseClientMoney(ClientBusiness clientBusiness, Long paidValue, Long operatorId) {
        if (clientBusiness == null || clientBusiness.getId() == null || paidValue == null || paidValue <= 0) {
            return false;
        }
        ClientBalanceRechargeCommand command = new ClientBalanceRechargeCommand();
        command.setClientId(clientBusiness.getId());
        command.setAmount(paidValue);
        command.setOperatorId(operatorId);
        command.setRequestId(null);
        BalanceCommandResult result = balanceCommandService.rechargeAndSync(command);
        return result.isSuccess();
    }

    private boolean matches(Map<String, Object> row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        for (Object value : row.values()) {
            if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private long idOf(Map<String, Object> row) {
        Long id = toLong(row.get("id"));
        return id == null ? 0L : id;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignore) {
            return null;
        }
    }

    private Object valueOrDefault(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }
}
