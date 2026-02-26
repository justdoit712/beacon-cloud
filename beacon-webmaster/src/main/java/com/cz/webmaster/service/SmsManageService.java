package com.cz.webmaster.service;

import com.cz.webmaster.dto.SmsSendForm;
import com.cz.webmaster.vo.SmsBatchSendVO;

public interface SmsManageService {

    String validateForSave(SmsSendForm form);

    String validateForUpdate(SmsSendForm form);

    SmsBatchSendVO save(SmsSendForm form, Long operatorId);

    SmsBatchSendVO update(SmsSendForm form, Long operatorId);
}
