package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** System Setting JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "system_setting")
public class SystemSetting extends AbstractEntity<String> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @Column(name = "setting_key", nullable = false, unique = true)
  private String id;

  @Column(name = "setting_value", nullable = false)
  private String value;
}
