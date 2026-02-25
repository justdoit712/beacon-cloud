package com.cz.webmaster.service;

import java.util.Map;

public interface SmsManageService {

    String validateForSave(Map<String, Object> body);

    String validateForUpdate(Map<String, Object> body);

    boolean save(Map<String, Object> body, Long operatorId);

    boolean update(Map<String, Object> body, Long operatorId);
}

