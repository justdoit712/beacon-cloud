package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.MobileArea;
import com.cz.webmaster.mapper.MobileAreaMapper;
import com.cz.webmaster.service.PhaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PhaseServiceImpl implements PhaseService {

    private final MobileAreaMapper mobileAreaMapper;

    public PhaseServiceImpl(MobileAreaMapper mobileAreaMapper) {
        this.mobileAreaMapper = mobileAreaMapper;
    }

    @Override
    public PageResult list(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        long total = mobileAreaMapper.countByKeyword(query);
        List<MobileArea> list = mobileAreaMapper.findListByPage(query, offset, limit);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (list != null) {
            for (MobileArea item : list) {
                rows.add(rowToMap(item));
            }
        }
        return new PageResult(total, rows);
    }

    @Override
    public Map<String, Object> info(Long id) {
        MobileArea mobileArea = id == null ? null : mobileAreaMapper.findById(id);
        return mobileArea == null ? new LinkedHashMap<>() : rowToMap(mobileArea);
    }

    @Override
    public String validateForSave(Map<String, Object> body) {
        if (body == null) {
            return "request body is required";
        }
        if (!StringUtils.hasText(toStr(body.get("phase")))) {
            return "phase is required";
        }
        String provId = toTrimmedText(body.get("provId"));
        String cityId = toTrimmedText(body.get("cityId"));
        if (!StringUtils.hasText(provId)) {
            return "provId is required";
        }
        if (!StringUtils.hasText(cityId)) {
            return "cityId is required";
        }
        if (mobileAreaMapper.findCitySample(provId, cityId) == null) {
            return "cityId not found";
        }
        return null;
    }

    @Override
    public String validateForUpdate(Map<String, Object> body) {
        String validateResult = validateForSave(body);
        if (validateResult != null) {
            return validateResult;
        }
        if (toLong(body.get("id")) == null) {
            return "id is required";
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Map<String, Object> body, Long operatorId) {
        MobileArea entity = buildEntity(body, null, operatorId, true);
        if (entity == null) {
            return false;
        }
        return mobileAreaMapper.insertSelective(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Map<String, Object> body, Long operatorId) {
        Long id = toLong(body == null ? null : body.get("id"));
        if (id == null) {
            return false;
        }
        MobileArea current = mobileAreaMapper.findById(id);
        if (current == null) {
            return false;
        }
        MobileArea entity = buildEntity(body, current, operatorId, false);
        if (entity == null) {
            return false;
        }
        entity.setId(id);
        return mobileAreaMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return mobileAreaMapper.deleteBatch(ids, new Date(), operatorId) > 0;
    }

    @Override
    public List<Map<String, Object>> allProvinces() {
        List<Map<String, Object>> rows = mobileAreaMapper.allProvinces();
        return rows == null ? new ArrayList<>() : rows;
    }

    @Override
    public List<Map<String, Object>> allCities(String provId) {
        if (!StringUtils.hasText(provId)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = mobileAreaMapper.allCities(provId.trim());
        return rows == null ? new ArrayList<>() : rows;
    }

    private MobileArea buildEntity(Map<String, Object> body, MobileArea current, Long operatorId, boolean create) {
        String mobileNumber = toTrimmedText(body.get("phase"));
        String provId = toTrimmedText(body.get("provId"));
        String cityId = toTrimmedText(body.get("cityId"));
        if (!StringUtils.hasText(mobileNumber)
                || !StringUtils.hasText(provId)
                || !StringUtils.hasText(cityId)) {
            return null;
        }

        MobileArea sample = mobileAreaMapper.findCitySample(provId, cityId);
        if (sample == null) {
            return null;
        }

        MobileArea entity = new MobileArea();
        entity.setId(create ? resolveId(body.get("id")) : current.getId());
        entity.setMobileNumber(mobileNumber);
        entity.setProvinceCode(valueOrDefault(sample.getProvinceCode(), current == null ? null : current.getProvinceCode()));
        entity.setMobileArea(cityId);
        entity.setMobileType(valueOrDefault(toTrimmedText(body.get("mobileType")),
                valueOrDefault(sample.getMobileType(), current == null ? null : current.getMobileType())));
        entity.setAreaCode(valueOrDefault(toTrimmedText(body.get("areaCode")),
                valueOrDefault(sample.getAreaCode(), current == null ? null : current.getAreaCode())));
        entity.setPostCode(valueOrDefault(toTrimmedText(body.get("postCode")),
                valueOrDefault(sample.getPostCode(), current == null ? null : current.getPostCode())));
        entity.setUpdated(new Date());
        if (operatorId != null) {
            entity.setUpdateId(operatorId);
        }

        if (create) {
            entity.setCreated(entity.getUpdated());
            entity.setCreateId(operatorId);
            entity.setIsDelete((byte) 0);
        }
        return entity;
    }

    private Map<String, Object> rowToMap(MobileArea row) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (row == null) {
            return map;
        }
        String provinceName = provinceName(row.getMobileArea());
        String cityName = cityName(row.getMobileArea());
        map.put("id", row.getId());
        map.put("phase", row.getMobileNumber());
        map.put("provId", provinceName);
        map.put("cityId", row.getMobileArea());
        map.put("provName", provinceName);
        map.put("cityName", cityName);
        map.put("mobileArea", row.getMobileArea());
        map.put("mobileType", row.getMobileType());
        map.put("areaCode", row.getAreaCode());
        map.put("postCode", row.getPostCode());
        map.put("provinceCode", row.getProvinceCode());
        return map;
    }

    private Long resolveId(Object value) {
        Long id = toLong(value);
        return id == null ? IdUtil.getSnowflakeNextId() : id;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String text = value.toString().trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }

    private String toTrimmedText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String provinceName(String mobileArea) {
        if (!StringUtils.hasText(mobileArea)) {
            return null;
        }
        String[] parts = mobileArea.trim().split("\\s+", 2);
        return parts.length == 0 ? mobileArea.trim() : parts[0];
    }

    private String cityName(String mobileArea) {
        if (!StringUtils.hasText(mobileArea)) {
            return null;
        }
        String[] parts = mobileArea.trim().split("\\s+", 2);
        return parts.length < 2 ? mobileArea.trim() : parts[1];
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
