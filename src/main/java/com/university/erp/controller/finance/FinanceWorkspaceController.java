package com.university.erp.controller.finance;
import com.university.erp.service.finance.FinanceWorkspaceService;
import com.university.erp.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/finance/workspace") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FinanceWorkspaceController {
 private final FinanceWorkspaceService service;
 @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping("/profile") public Map<String,Object> profile(@AuthenticationPrincipal User actor){return service.profile(actor);}
 @PutMapping("/profile") public Map<String,Object> profile(@AuthenticationPrincipal User actor,@Valid @RequestBody FinanceWorkspaceService.Profile input){return service.saveProfile(actor,input);}
 @GetMapping("/approvals") public List<Map<String,Object>> approvals(Principal actor){return service.approvals(actor.getName());}
 @GetMapping("/health") public Map<String,Object> health(){return service.health();}
 @GetMapping("/history/{kind}/{id}") public List<Map<String,Object>> history(@PathVariable String kind,@PathVariable UUID id){return service.history(kind,id);}
 @PutMapping("/entries/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void entry(@PathVariable UUID id,@Valid @RequestBody FinanceWorkspaceService.EntryEdit input,Principal actor){service.editEntry(id,input,actor.getName());}
 @PostMapping("/entries/{id}/actions") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void action(@PathVariable UUID id,@Valid @RequestBody FinanceWorkspaceService.Action input,Principal actor){service.entryAction(id,input,actor.getName());}
 @PutMapping("/budgets/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void budget(@PathVariable UUID id,@Valid @RequestBody FinanceWorkspaceService.BudgetEdit input,Principal actor){service.editBudget(id,input,actor.getName());}
 @PutMapping("/vendors/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void vendor(@PathVariable UUID id,@Valid @RequestBody FinanceWorkspaceService.VendorEdit input,Principal actor){service.editVendor(id,input,actor.getName());}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST) public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid finance workspace request");}
}
