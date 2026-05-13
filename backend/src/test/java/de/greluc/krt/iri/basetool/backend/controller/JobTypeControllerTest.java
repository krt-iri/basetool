package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.JobTypeMapper;
import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.JobTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pure-method unit tests for {@link JobTypeController}. */
@ExtendWith(MockitoExtension.class)
class JobTypeControllerTest {

    @Mock private JobTypeService service;
    @Mock private JobTypeMapper mapper;

    @InjectMocks private JobTypeController controller;

    @Test
    void getAll_forwardsArchetypeFilterAndIncludeInactive() {
        when(service.getJobTypes(eq(JobTypeArchetype.CREW), any(Pageable.class), eq(true)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAllJobTypes(JobTypeArchetype.CREW, null, null, null, true);

        verify(service).getJobTypes(eq(JobTypeArchetype.CREW), any(Pageable.class), eq(true));
    }

    @Test
    void getAll_nullArchetype_andIncludeInactiveFalse_isForwarded() {
        when(service.getJobTypes(eq(null), any(Pageable.class), eq(false)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getAllJobTypes(null, null, null, null, false);

        verify(service).getJobTypes(eq(null), any(Pageable.class), eq(false));
    }

    @Test
    void getAll_wrapsServicePageIntoPageResponse() {
        JobType entity = new JobType();
        JobTypeDto dto = new JobTypeDto(UUID.randomUUID(), "Pilot", "desc", JobTypeArchetype.CREW, null, true, false, 1L);
        when(service.getJobTypes(any(), any(Pageable.class), eq(false))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toDto(entity)).thenReturn(dto);

        PageResponse<JobTypeDto> resp = controller.getAllJobTypes(null, 0, 50, null, false);

        assertEquals(1, resp.totalElements());
        assertSame(dto, resp.content().getFirst());
    }

    @Test
    void create_mapsDtoToEntityViaMapperAndDelegates() {
        JobTypeDto request = new JobTypeDto(null, "Pilot", null, JobTypeArchetype.CREW, null, true, false, null);
        JobType entity = new JobType();
        JobType persisted = new JobType();
        JobTypeDto response = new JobTypeDto(UUID.randomUUID(), "Pilot", null, JobTypeArchetype.CREW, null, true, false, 1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(service.createJobType(entity)).thenReturn(persisted);
        when(mapper.toDto(persisted)).thenReturn(response);

        JobTypeDto result = controller.createJobType(request);

        assertSame(response, result);
        verify(service).createJobType(entity);
    }

    @Test
    void update_passesIdAndDtoDirectly_noMapperOnInput() {
        // The service's update method accepts the DTO directly so it can pick
        // which fields are mutable. The controller MUST forward the raw DTO.
        UUID id = UUID.randomUUID();
        JobTypeDto request = new JobTypeDto(id, "Renamed", null, JobTypeArchetype.CREW, null, true, true, 4L);
        JobType updated = new JobType();
        JobTypeDto response = new JobTypeDto(id, "Renamed", null, JobTypeArchetype.CREW, null, true, true, 5L);

        when(service.updateJobType(id, request)).thenReturn(updated);
        when(mapper.toDto(updated)).thenReturn(response);

        JobTypeDto result = controller.updateJobType(id, request);

        assertSame(response, result);
        verify(service).updateJobType(id, request);
        verify(mapper, never()).toEntity(any(JobTypeDto.class));
    }

    @Test
    void delete_delegatesIdToService() {
        UUID id = UUID.randomUUID();

        controller.deleteJobType(id);

        verify(service).deleteJobType(id);
    }

    @Test
    void activate_delegatesIdToService() {
        UUID id = UUID.randomUUID();

        controller.activateJobType(id);

        verify(service).activateJobType(id);
    }
}
