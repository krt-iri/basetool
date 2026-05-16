package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.repository.CityRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link CityService}'s admin-override mutators. */
@ExtendWith(MockitoExtension.class)
class CityServiceTest {

  @Mock private CityRepository cityRepository;

  @InjectMocks private CityService service;

  @Test
  void setLoadingDockOverride_storesValueAndFlag() {
    UUID id = UUID.randomUUID();
    City city = new City();
    city.setId(id);
    when(cityRepository.findById(id)).thenReturn(Optional.of(city));
    when(cityRepository.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

    service.setLoadingDockOverride(id, true);

    ArgumentCaptor<City> cap = ArgumentCaptor.forClass(City.class);
    verify(cityRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock());
    assertTrue(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void clearLoadingDockOverride_flipsFlagButLeavesValueAlone() {
    UUID id = UUID.randomUUID();
    City city = new City();
    city.setId(id);
    city.setHasLoadingDock(true);
    city.setHasLoadingDockOverridden(true);
    when(cityRepository.findById(id)).thenReturn(Optional.of(city));
    when(cityRepository.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

    service.clearLoadingDockOverride(id);

    ArgumentCaptor<City> cap = ArgumentCaptor.forClass(City.class);
    verify(cityRepository).save(cap.capture());
    assertTrue(cap.getValue().getHasLoadingDock(), "value column must not be touched on clear");
    assertFalse(cap.getValue().getHasLoadingDockOverridden());
  }

  @Test
  void getCity_throwsNotFoundOnMissingId() {
    UUID id = UUID.randomUUID();
    when(cityRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.getCity(id));
  }
}
