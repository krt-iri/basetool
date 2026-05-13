package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import java.util.UUID;
import org.mapstruct.*;

/** MapStruct mapper between Job Type entities and DTOs. */
@Mapper(componentModel = "spring")
public interface JobTypeMapper {

  /**
   * Maps a {@link JobType} entity to its DTO, flattening {@code parent.id} into {@code parentId}.
   */
  @Mapping(target = "parentId", source = "parent.id")
  @Mapping(target = "isLeadershipRole", source = "leadershipRole")
  JobTypeDto toDto(JobType entity);

  /**
   * Builds a new {@link JobType} entity from the DTO. The {@code parent} association is resolved
   * separately in {@link #setParentAfterMapping}; timestamps stay owned by the persistence
   * provider.
   */
  @Mapping(target = "parent", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "leadershipRole", source = "isLeadershipRole")
  JobType toEntity(JobTypeDto dto);

  /**
   * After-mapping callback that rewires the {@code parent} association from {@code source.parentId}
   * - a JPA stub with only the id is enough because the persistence provider resolves it on
   * persist.
   */
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
