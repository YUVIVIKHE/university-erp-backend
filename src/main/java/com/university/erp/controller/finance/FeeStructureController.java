package com.university.erp.controller.finance;
import com.university.erp.service.finance.FeeStructureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import java.security.Principal;

@RestController @RequestMapping("/api/finance/fee-structures") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FeeStructureController {
    private final FeeStructureService service;
    private final com.university.erp.service.finance.FeeStructureOptionsService options;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/options") public com.university.erp.service.finance.FeeStructureOptionsService.Options options(@RequestParam(required=false) @Size(max=200) java.util.List<@Positive Long> courseIds){return options.read(courseIds);}

    @GetMapping public Page<FeeStructureService.Row> list(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page){return service.list(search,status,page);}
    @GetMapping("/courses") public Page<FeeStructureService.CourseOption> courses(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="0") @Min(0) int page){return service.courses(search,page);}
    @GetMapping("/{id}") public FeeStructureService.Detail detail(@PathVariable long id){return service.detail(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public FeeStructureService.Detail create(@Valid @RequestBody FeeStructureService.Input input,Principal actor){return service.create(input,actor.getName());}
    @PutMapping("/{id}") public FeeStructureService.Detail update(@PathVariable long id,@RequestParam @Min(0) long version,@Valid @RequestBody FeeStructureService.Input input,Principal actor){return service.update(id,version,input,actor.getName());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id,@RequestParam @Min(0) long version){service.delete(id,version);}
    @PostMapping("/{id}/status") public FeeStructureService.Detail transition(@PathVariable long id,@Valid @RequestBody FeeStructureService.Transition input){return service.transition(id,input);}
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid search or page parameter");}
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT) public com.university.erp.dto.ApiError stale(){return com.university.erp.dto.ApiError.of(409,"Structure changed. Refresh and retry.");}
}
