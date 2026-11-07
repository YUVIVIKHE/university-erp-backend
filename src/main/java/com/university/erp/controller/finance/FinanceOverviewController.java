package com.university.erp.controller.finance;

import com.university.erp.dto.FeeResponse;
import com.university.erp.service.FeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceOverviewController {
    private final FeeService feeService;
    private final com.university.erp.service.finance.FinanceDashboardService dashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public com.university.erp.dto.finance.FinanceDashboard dashboard(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return dashboardService.dashboard();
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public FeeResponse.Stats overview() {
        return feeService.stats();
    }
}
