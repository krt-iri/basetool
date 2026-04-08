package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface JobTypeMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "isLeadershipRole", source = "leadershipRole")
    JobTypeDto toDto(JobType entity);

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "leadershipRole", source = "isLeadershipRole")
    JobType toEntity(JobTypeDto dto);

    @AfterMapping
    default void setParentAfterMapping(@MappingTarget JobType target, JobTypeDto source) {
        UUID parentId = source.parentId();
        if (parentId != null) {
            JobType parent = new JobType();
            parent.setId(parentId);
            target.setParent(parent);
        } else {
            target.setParent(null);
        }
    }
}
