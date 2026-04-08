package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.OperationMapper;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.service.OperationFinanceService;
import de.greluc.krt.iri.basetool.backend.service.OperationService;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationController {

    private final OperationService operationService;
    private final OperationMapper operationMapper;
    private final OperationFinanceService operationFinanceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<OperationDto> getAllOperations(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParams = sort.split(",");
        Sort sortObj = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Operation> operationPage = operationService.getAllOperations(pageable);
        return toPageResponse(operationPage.map(operationMapper::toDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public OperationDto getOperationById(@PathVariable UUID id) {
        return operationMapper.toDto(operationService.getOperationById(id));
    }

    @GetMapping("/{id}/finances")
    @PreAuthorize("isAuthenticated()")
    public OperationFinanceDto getOperationFinances(@PathVariable UUID id) {
        return operationFinanceService.getOperationFinances(id);
    }

    @GetMapping("/{id}/payouts")
    @PreAuthorize("isAuthenticated()")
    public java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto> getOperationPayouts(@PathVariable UUID id) {
        return operationService.getOperationPayouts(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public OperationDto createOperation(@Valid @RequestBody OperationCreateDto createDto) {
        Operation operation = operationMapper.toEntity(createDto);
        return operationMapper.toDto(operationService.createOperation(operation));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public OperationDto updateOperation(@PathVariable UUID id, @Valid @RequestBody OperationUpdateDto updateDto) {
        Operation operation = operationService.getOperationById(id);
        operationMapper.updateEntity(updateDto, operation);
        return operationMapper.toDto(operationService.updateOperation(id, operation));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOperation(@PathVariable UUID id) {
        operationService.deleteOperation(id);
        return ResponseEntity.noContent().build();
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSort().stream()
                        .map(order -> order.getProperty() + "," + order.getDirection().name())
                        .toList()
        );
    }
}