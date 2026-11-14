package com.university.erp.security;

import com.university.erp.config.SecurityConfig;
import com.university.erp.controller.AuthController;
import com.university.erp.controller.finance.FinanceOverviewController;
import com.university.erp.service.AuthService;
import com.university.erp.service.FeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({com.university.erp.controller.finance.FinanceWorkspaceController.class,com.university.erp.controller.finance.AwardReversalController.class,FinanceOverviewController.class, AuthController.class, com.university.erp.controller.finance.AccountantFeeController.class,com.university.erp.controller.finance.FeeStructureController.class,com.university.erp.controller.finance.BulkFeeAssignmentController.class,com.university.erp.controller.finance.FeeCollectionController.class,com.university.erp.controller.finance.FeeFollowupController.class,com.university.erp.controller.finance.ScholarshipController.class,com.university.erp.controller.finance.AwardApprovalController.class,com.university.erp.controller.finance.FeeRefundController.class,com.university.erp.controller.finance.OperatingFinanceController.class,com.university.erp.controller.finance.GeneralLedgerController.class,com.university.erp.controller.finance.BudgetController.class,com.university.erp.controller.finance.DisbursementController.class,com.university.erp.controller.finance.PayrollSetupController.class,com.university.erp.controller.finance.FinanceReportsController.class,com.university.erp.controller.finance.AccountantSettingsController.class,com.university.erp.controller.finance.FinanceActivityController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AccountantAccessTest {
    @MockBean com.university.erp.service.finance.FinanceWorkspaceService workspace;
    @org.junit.jupiter.api.Test void workspaceAndProfileRequireFinanceRole() throws Exception {
        for(String path:new String[]{"/api/finance/workspace/health","/api/finance/workspace/approvals","/api/finance/workspace/profile"}){
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(String role:new String[]{"STUDENT","FACULTY"})mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/finance/workspace/profile").with(user("other").roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @MockBean com.university.erp.service.finance.AwardReversalService reversals;
    @org.junit.jupiter.api.Test void reversalQueueRequiresFinanceRole() throws Exception {
        mvc.perform(get("/api/finance/award-reversals")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"})mvc.perform(get("/api/finance/award-reversals").with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/award-reversals").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isOk());
    }
    @MockBean com.university.erp.service.finance.FinanceReportDetailsService reportDetails;
    @MockBean com.university.erp.service.finance.FinanceReportsService reports;
    @Test void reportsAreFinanceOnlyAndReadOnly() throws Exception {
        String path="/api/finance/reports?from=2026-01-01&until=2026-12-31";
        for(String role:new String[]{"STUDENT","FACULTY"})mvc.perform(get("/api/finance/reports/details?section=balances&from=2026-01-01&until=2026-12-31").with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"})mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get(path).with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/finance/reports").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isMethodNotAllowed());
    }
    @MockBean com.university.erp.service.finance.AccountantSettingsService accountantSettings;
    @Test void settingsRequireFinanceRoleAndValidPreferences() throws Exception {
        mvc.perform(get("/api/finance/settings")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/settings").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/finance/settings").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/settings").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(put("/api/finance/settings").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/finance/settings").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"reportPeriod\":\"BAD\",\"reportSection\":\"collections\",\"version\":0}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FinanceActivityService activity;
    @Test void activityIsFinanceOnlyAndReadOnly() throws Exception {
        String path="/api/finance/activity?from=2026-01-01&until=2026-12-31";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"})mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get(path).with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(delete(path).with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/finance/activity").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/settings/password").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
    @Autowired MockMvc mvc;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean FeeService feeService;
    @MockBean com.university.erp.service.finance.PayrollSetupService payrollSetup;
    @Test void payrollSetupRequiresFinanceRoleAndValidatedInputs() throws Exception {
        mvc.perform(get("/api/finance/payroll/salaries")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            for(String path:new String[]{"/salaries","/profiles/1","/snapshots/00000000-0000-0000-0000-000000000001"})mvc.perform(get("/api/finance/payroll"+path).with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/payroll/profiles/1/generate").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/finance/payroll/profiles/1").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/payroll/salaries").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/payroll/salaries").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/payroll/salaries?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.DisbursementService disbursements;
    @Test void payrollVendorAccessIsFinanceOnly() throws Exception {
        mvc.perform(get("/api/finance/disbursements/staff")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            for(String path:new String[]{"/staff","/vendors","/PAYROLL/payments","/VENDOR/payments"})mvc.perform(get("/api/finance/disbursements"+path).with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/disbursements/PAYROLL/payments").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/disbursements/VENDOR/payments/00000000-0000-0000-0000-000000000001/settlement").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/disbursements/vendors").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/disbursements/PAYROLL/payments").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/disbursements/VENDOR/payments?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.BudgetService budgets;
    @Test void budgetsRequireFinanceRoleAndValidPlans() throws Exception {
        mvc.perform(get("/api/finance/budgets")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/budgets").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/budgets").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/budgets/00000000-0000-0000-0000-000000000001/review").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/budgets").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/budgets").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/budgets?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.GeneralLedgerService ledger;
    @Test void generalLedgerIsFinanceOnlyAndReadOnly() throws Exception {
        mvc.perform(get("/api/finance/ledger/journals")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"})for(String path:new String[]{"/journals","/trial-balance","/accounts","/accounts/1000"})mvc.perform(get("/api/finance/ledger"+path).with(user("other").roles(role))).andExpect(status().isForbidden());
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})for(String path:new String[]{"/journals","/trial-balance","/accounts","/accounts/1000"})mvc.perform(get("/api/finance/ledger"+path).with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/finance/ledger/journals?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/ledger/accounts/invalid").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/ledger/journals").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isMethodNotAllowed());
    }
    @MockBean com.university.erp.service.finance.OperatingFinanceService operations;
    @Test void operatingRegisterRequiresFinanceRoleAndVerifiedSettlement() throws Exception {
        mvc.perform(get("/api/finance/operations")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/operations").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/operations").with(user("other").roles(role))).andExpect(status().isForbidden());
            for(String suffix:new String[]{"/review","/settlement"})mvc.perform(post("/api/finance/operations/00000000-0000-0000-0000-000000000001"+suffix).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/operations").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/operations").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/operations?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/operations/00000000-0000-0000-0000-000000000001/settlement").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"00000000-0000-0000-0000-000000000002\",\"method\":\"CASH\",\"reference\":\"CASH-1\",\"paidDate\":\"2026-11-07\",\"verified\":false}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FeeRefundService refunds;
    @Test void refundsRequireFinanceRoleAndVerifiedPayout() throws Exception {
        mvc.perform(get("/api/finance/refunds")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/refunds").with(user("other").roles(role))).andExpect(status().isForbidden());
            for(String suffix:new String[]{"","/review","/payout"})mvc.perform(post("/api/finance/refunds/00000000-0000-0000-0000-000000000001"+suffix).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/refunds").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/refunds").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/refunds?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/refunds/00000000-0000-0000-0000-000000000001/payout").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"00000000-0000-0000-0000-000000000002\",\"method\":\"CASH\",\"reference\":\"CASH-1\",\"paidDate\":\"2026-11-07\",\"verified\":false}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.AwardApprovalService awards;
    @Test void financialAwardsAreFinanceOnly() throws Exception {
        mvc.perform(get("/api/finance/approvals/awards")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/approvals/awards").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/approvals/awards/00000000-0000-0000-0000-000000000001").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/approvals/awards").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/approvals/awards/00000000-0000-0000-0000-000000000001").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.ScholarshipService scholarships;
    @Test void scholarshipIntakeIsFinanceOnly() throws Exception {
        mvc.perform(get("/api/finance/scholarships")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/scholarships").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/scholarships").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/scholarships/00000000-0000-0000-0000-000000000001/review").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/scholarships").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/finance/scholarships?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/scholarships").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FeeFollowupService followups;
    @Test void followupWorklistAndNotesAreFinanceOnly() throws Exception {
        mvc.perform(get("/api/finance/defaulters")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}){
            mvc.perform(get("/api/finance/defaulters").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/defaulters/ADM-1/followups").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/finance/defaulters/ADM-1/reminder-draft").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"})mvc.perform(get("/api/finance/defaulters").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/defaulters/ADM-1/followups").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"PHONE\",\"outcome\":\"CONTACTED\",\"note\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/defaulters?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FeeCollectionService collections;
    @Test void collectionsRequireFinanceRoleAndVerifiedPayments() throws Exception {
        mvc.perform(get("/api/finance/collections")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}) {
            mvc.perform(get("/api/finance/collections").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/collections").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ADMIN","ACCOUNTANT"}) mvc.perform(get("/api/finance/collections").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/collections").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\"b7b5c090-4b86-4665-830c-9ff1ec9b379a\",\"admissionId\":\"ADM-1\",\"method\":\"CASH\",\"paidDate\":\"2026-11-07\",\"verified\":false,\"allocations\":[{\"feeId\":\"FEE-1\",\"amount\":100}]}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/collections?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.BulkFeeAssignmentService bulkAssignments;
    @Test void bulkAssignmentsRequireFinanceRoleAndValidSelection() throws Exception {
        mvc.perform(get("/api/finance/fee-assignments/programs")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}) {
            mvc.perform(get("/api/finance/fee-assignments/programs").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/fee-assignments/structures/1/preview").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        for(String role:new String[]{"ACCOUNTANT","ADMIN"}) mvc.perform(get("/api/finance/fee-assignments/programs").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/fee-assignments/structures/1/preview").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"batchYear\":1900}" )).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FeeStructureOptionsService structureOptions;
    @MockBean com.university.erp.service.finance.FeeStructureService structures;
    @Test void structureAccessAndValidation() throws Exception {
        mvc.perform(get("/api/finance/fee-structures")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"STUDENT","FACULTY"}) mvc.perform(get("/api/finance/fee-structures").with(user("other").roles(role))).andExpect(status().isForbidden());
        for(String role:new String[]{"ACCOUNTANT","ADMIN"}) mvc.perform(get("/api/finance/fee-structures").with(user("staff").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/finance/fee-structures").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test\",\"courseId\":1,\"academicYear\":2026,\"semester\":1,\"lines\":[]}")).andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.StudentFeeAccountService studentAccounts;
    @MockBean com.university.erp.service.finance.AccountantFeeService accountantFees;

    @Test void feeManagementIsRestrictedToFinanceStaff() throws Exception {
        for (String role : new String[]{"ACCOUNTANT", "ADMIN"}) {
            mvc.perform(get("/api/finance/fees").with(user("staff").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/finance/fees/admissions").with(user("staff").roles(role))).andExpect(status().isOk());
            mvc.perform(post("/api/finance/fees").with(user("staff").roles(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"admissionId\":\"ADM-1\",\"feeType\":\"Tuition\",\"amount\":100,\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isCreated());
        }
        for (String role : new String[]{"STUDENT", "FACULTY"}) {
            mvc.perform(get("/api/finance/fees").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/finance/fees").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/finance/fees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/fees?page=-1").with(user("staff").roles("ACCOUNTANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/fees").with(user("staff").roles("ACCOUNTANT")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"admissionId\":\"ADM-1\",\"feeType\":\"Tuition\",\"amount\":0,\"dueDate\":\"2026-12-01\"}"))
            .andExpect(status().isBadRequest());
    }
    @MockBean com.university.erp.service.finance.FinanceDashboardService dashboardService;
    @MockBean AuthService authService;

    @Test void financeRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/finance/fees/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void accountantAndAdminCanReadOverview() throws Exception {
        for (String role : new String[]{"ACCOUNTANT", "ADMIN"}) {
            mvc.perform(get("/api/finance/fees/accounts").with(user("staff").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/finance/fees/accounts/ADM-1").with(user("staff").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/finance/dashboard").with(user("staff").roles(role)))
                .andExpect(status().isOk());
            mvc.perform(get("/api/finance/overview").with(user("staff").roles(role)))
                .andExpect(status().isOk());
        }
    }

    @Test void otherRolesCannotReadFinance() throws Exception {
        for (String role : new String[]{"STUDENT", "FACULTY"}) {
            mvc.perform(get("/api/finance/fees/accounts").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/finance/fees/accounts/ADM-1").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/finance/dashboard").with(user("other").roles(role)))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/finance/overview").with(user("other").roles(role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test void accountantCannotUseAcademicOrLegacyWriteApis() throws Exception {
        for (String path : new String[]{"/api/students", "/api/admissions", "/api/courses", "/api/admin/users"}) {
            mvc.perform(get(path).with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/fees/FEE-1/pay").with(user("accountant").roles("ACCOUNTANT")))
            .andExpect(status().isForbidden());
    }

    @Test void staffProvisioningRequiresAdmin() throws Exception {
        for (String role : new String[]{"ACCOUNTANT", "ADMIN", "FACULTY"}) {
            String body = "{\"email\":\"staff@example.com\",\"password\":\"Example123!\",\"fullName\":\"Staff\",\"role\":\"" + role + "\"}";
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/register").with(user("accountant").roles("ACCOUNTANT"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/register").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        }
    }

    @Test void studentRegistrationRemainsAvailable() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"student@example.com\",\"password\":\"Example123!\",\"fullName\":\"Student\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isCreated());
    }
}
