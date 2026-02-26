package com.cz.webmaster.service.impl;

import com.cz.common.constant.WebMasterConstants;
import com.cz.webmaster.client.ApiSmsClient;
import com.cz.webmaster.dto.ApiInternalSingleSendForm;
import com.cz.webmaster.dto.SmsSendForm;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsManageService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.ApiSmsSendResultVO;
import com.cz.webmaster.vo.SmsBatchSendVO;
import com.cz.webmaster.vo.SmsSendItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SmsManageServiceImpl implements SmsManageService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final int MAX_BATCH_SIZE = 500;
    private static final String DEFAULT_REAL_IP = "127.0.0.1";

    private final ApiSmsClient apiSmsClient;
    private final ClientBusinessService clientBusinessService;
    private final SmsRoleService roleService;

    @Value("${sms.internal.token:}")
    private String internalToken;

    public SmsManageServiceImpl(ApiSmsClient apiSmsClient,
                                ClientBusinessService clientBusinessService,
                                SmsRoleService roleService) {
        this.apiSmsClient = apiSmsClient;
        this.clientBusinessService = clientBusinessService;
        this.roleService = roleService;
    }

    @Override
    public String validateForSave(SmsSendForm form) {
        if (form == null) {
            return "request body is required";
        }
        if (form.getClientId() == null) {
            return "clientId is required";
        }
        if (!StringUtils.hasText(form.getMobile())) {
            return "mobile is required";
        }
        if (!StringUtils.hasText(form.getContent())) {
            return "content is required";
        }
        if (form.getState() != null && (form.getState() < 0 || form.getState() > 2)) {
            return "state must be between 0 and 2";
        }

        List<String> mobiles = parseMobileList(form.getMobile());
        if (mobiles.isEmpty()) {
            return "at least one mobile is required";
        }
        if (mobiles.size() > MAX_BATCH_SIZE) {
            return "mobile count exceeds limit: " + MAX_BATCH_SIZE;
        }
        for (String mobile : mobiles) {
            if (!MOBILE_PATTERN.matcher(mobile).matches()) {
                return "invalid mobile: " + mobile;
            }
        }
        return null;
    }

    @Override
    public String validateForUpdate(SmsSendForm form) {
        return validateForSave(form);
    }

    @Override
    public SmsBatchSendVO save(SmsSendForm form, Long operatorId) {
        return doSend(form, operatorId);
    }

    @Override
    public SmsBatchSendVO update(SmsSendForm form, Long operatorId) {
        return doSend(form, operatorId);
    }

    private SmsBatchSendVO doSend(SmsSendForm form, Long operatorId) {
        List<String> mobiles = parseMobileList(form.getMobile());

        SmsBatchSendVO summary = new SmsBatchSendVO();
        summary.setClientId(form.getClientId());
        summary.setState(normalizeState(form.getState()));
        summary.setTotal(mobiles.size());

        if (mobiles.isEmpty()) {
            summary.setMessage("no valid mobiles");
            return summary;
        }

        ClientBusiness clientBusiness = resolveAuthorizedClient(form.getClientId(), operatorId);
        if (clientBusiness == null) {
            markAllFailed(summary, mobiles, "client not found or no permission");
            return summary;
        }
        summary.setClientName(clientBusiness.getCorpname());

        if (!StringUtils.hasText(clientBusiness.getApikey())) {
            markAllFailed(summary, mobiles, "client apikey is empty");
            return summary;
        }

        String realIp = resolveRealIp(clientBusiness);
        String text = form.getContent().trim();

        int index = 0;
        for (String mobile : mobiles) {
            SmsSendItemVO item = new SmsSendItemVO();
            item.setMobile(mobile);

            ApiInternalSingleSendForm request = new ApiInternalSingleSendForm();
            request.setApikey(clientBusiness.getApikey());
            request.setMobile(mobile);
            request.setText(text);
            request.setUid(buildUid(operatorId, mobile, index));
            request.setState(summary.getState());
            request.setRealIp(realIp);

            try {
                ApiSmsSendResultVO result = apiSmsClient.singleSend(internalToken, request);
                if (result == null) {
                    item.setCode(-1);
                    item.setMsg("empty response from beacon-api");
                    increaseFailed(summary);
                } else {
                    item.setCode(result.getCode());
                    item.setMsg(result.getMsg());
                    item.setSid(result.getSid());
                    if (Integer.valueOf(0).equals(result.getCode())) {
                        increaseSuccess(summary);
                    } else {
                        increaseFailed(summary);
                    }
                }
            } catch (Exception ex) {
                log.warn("call beacon-api internal send failed, mobile={}, message={}", mobile, ex.getMessage());
                item.setCode(-1);
                item.setMsg(safeError(ex));
                increaseFailed(summary);
            }

            summary.getItems().add(item);
            index++;
        }

        summary.setFailed(summary.getTotal() - summary.getSuccess());
        if (summary.getSuccess().equals(summary.getTotal())) {
            summary.setMessage("send success");
        } else if (summary.getSuccess() > 0) {
            summary.setMessage("partial success");
        } else {
            summary.setMessage("send failed");
        }
        return summary;
    }

    private ClientBusiness resolveAuthorizedClient(Long clientId, Long operatorId) {
        if (clientId == null || operatorId == null) {
            return null;
        }

        ClientBusiness target = clientBusinessService.findById(clientId);
        if (target == null) {
            return null;
        }

        Integer operator = toInteger(operatorId);
        if (operator == null) {
            return null;
        }

        if (isRoot(operator)) {
            return target;
        }

        List<ClientBusiness> scope = clientBusinessService.findByUserId(operator);
        if (scope == null) {
            return null;
        }
        for (ClientBusiness item : scope) {
            if (clientId.equals(item.getId())) {
                return target;
            }
        }
        return null;
    }

    private boolean isRoot(Integer operatorId) {
        Set<String> roleNames = roleService.getRoleName(operatorId);
        return roleNames != null && roleNames.contains(WebMasterConstants.ROOT);
    }

    private Integer toInteger(Long value) {
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private int normalizeState(Integer state) {
        return state == null ? 1 : state;
    }

    private List<String> parseMobileList(String rawMobiles) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(rawMobiles)) {
            return result;
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String[] tokens = rawMobiles.replace('\r', '\n').split("[,;\\s]+");
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                unique.add(token.trim());
            }
        }
        result.addAll(unique);
        return result;
    }

    private String resolveRealIp(ClientBusiness clientBusiness) {
        if (clientBusiness == null || !StringUtils.hasText(clientBusiness.getIpAddress())) {
            return DEFAULT_REAL_IP;
        }
        String[] parts = clientBusiness.getIpAddress().split("[,;\\s]+");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                return part.trim();
            }
        }
        return DEFAULT_REAL_IP;
    }

    private String buildUid(Long operatorId, String mobile, int index) {
        String prefix = operatorId == null ? "wm" : String.valueOf(operatorId);
        return prefix + "_" + System.currentTimeMillis() + "_" + index + "_" + mobile;
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "remote call failed";
        }
        String cleaned = message.replaceAll("[\\r\\n]+", " ").trim();
        if (cleaned.length() > 200) {
            return cleaned.substring(0, 200);
        }
        return cleaned;
    }

    private void markAllFailed(SmsBatchSendVO summary, List<String> mobiles, String message) {
        summary.setSuccess(0);
        summary.setFailed(mobiles.size());
        summary.setMessage(message);
        for (String mobile : mobiles) {
            SmsSendItemVO item = new SmsSendItemVO();
            item.setMobile(mobile);
            item.setCode(-1);
            item.setMsg(message);
            summary.getItems().add(item);
        }
    }

    private void increaseSuccess(SmsBatchSendVO summary) {
        summary.setSuccess(summary.getSuccess() + 1);
    }

    private void increaseFailed(SmsBatchSendVO summary) {
        summary.setFailed(summary.getFailed() + 1);
    }
}
