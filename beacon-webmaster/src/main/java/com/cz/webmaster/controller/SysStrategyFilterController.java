package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.vo.StrategyFilterVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping({"/sys/strategy-filter", "/sys/stragetyfilter"})
public class SysStrategyFilterController {

    private static final Set<String> SUPPORTED_FILTERS = new LinkedHashSet<>(Arrays.asList(
            "black",
            "blackGlobal",
            "blackClient",
            "dirtyword",
            "dfaDirtyWord",
            "hutoolDFADirtyWord",
            "route",
            "phase",
            "transfer",
            "fee",
            "limitOneHour"
    ));

    private final ClientBusinessService clientBusinessService;

    public SysStrategyFilterController(ClientBusinessService clientBusinessService) {
        this.clientBusinessService = clientBusinessService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "10") int limit,
                                @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = limit <= 0 ? 10 : limit;
        List<ClientBusiness> clients = clientBusinessService.findByKeyword(null);
        List<StrategyFilterVO> filtered = new ArrayList<>();
        if (clients != null) {
            for (ClientBusiness client : clients) {
                StrategyFilterVO vo = toVO(client);
                if (matches(vo, keyword)) {
                    filtered.add(vo);
                }
            }
        }

        int fromIndex = Math.min(safeOffset, filtered.size());
        int toIndex = Math.min(fromIndex + safeLimit, filtered.size());
        return Result.ok((long) filtered.size(), filtered.subList(fromIndex, toIndex));
    }

    @GetMapping("/info/{id}")
    public ResultVO<StrategyFilterVO> info(@PathVariable("id") Long id) {
        return Result.ok(toVO(clientBusinessService.findById(id)));
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody StrategyFilterVO form) {
        if (form == null || form.getId() == null) {
            return Result.error("客户id不能为空");
        }
        String normalizedFilters;
        try {
            normalizedFilters = normalizeFilters(form.getFilters());
        } catch (IllegalArgumentException ex) {
            return Result.error(ex.getMessage());
        }
        if (!StringUtils.hasText(normalizedFilters)) {
            return Result.error("过滤器列表不能为空");
        }

        ClientBusiness current = clientBusinessService.findById(form.getId());
        if (current == null || current.getIsDelete() == null || current.getIsDelete() != 0) {
            return Result.error("客户不存在");
        }

        ClientBusiness update = new ClientBusiness();
        update.setId(form.getId());
        update.setClientFilters(normalizedFilters);
        update.setUpdateId(OperatorContextUtils.currentOperatorId());
        return clientBusinessService.update(update) ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody StrategyFilterVO form) {
        return update(form);
    }

    @PostMapping("/del")
    public ResultVO<?> delete() {
        return Result.error("客户策略链不支持删除");
    }

    @GetMapping("/filters/all")
    public ResultVO<List<String>> allFilters() {
        return Result.ok(new ArrayList<>(SUPPORTED_FILTERS));
    }

    private StrategyFilterVO toVO(ClientBusiness client) {
        StrategyFilterVO vo = new StrategyFilterVO();
        if (client == null) {
            return vo;
        }
        vo.setId(client.getId());
        vo.setCorpname(client.getCorpname());
        vo.setUsercode(client.getApikey());
        vo.setFilters(client.getClientFilters());
        return vo;
    }

    private boolean matches(StrategyFilterVO vo, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(vo.getCorpname(), normalizedKeyword)
                || contains(vo.getUsercode(), normalizedKeyword)
                || contains(vo.getFilters(), normalizedKeyword)
                || contains(vo.getId() == null ? null : String.valueOf(vo.getId()), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeFilters(String filters) {
        if (!StringUtils.hasText(filters)) {
            return null;
        }
        String[] parts = filters.split(",");
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String filter = part.trim();
            if (!SUPPORTED_FILTERS.contains(filter)) {
                throw new IllegalArgumentException("unsupported strategy filter: " + filter);
            }
            if (seen.add(filter)) {
                normalized.add(filter);
            }
        }
        return String.join(",", normalized);
    }
}
