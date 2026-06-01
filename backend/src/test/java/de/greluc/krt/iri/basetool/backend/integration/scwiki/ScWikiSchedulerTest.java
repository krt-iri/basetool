package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.service.SyncCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ScWikiScheduler} — master-switch gating and cross-scheduler exclusion. */
@ExtendWith(MockitoExtension.class)
class ScWikiSchedulerTest {

  @Mock private ScWikiClient scWikiClient;
  @Mock private ScWikiProperties properties;
  @Mock private ScWikiCommoditySyncService commoditySyncService;
  @Mock private ScWikiBlueprintSyncService blueprintSyncService;
  @Mock private ScWikiItemSyncService itemSyncService;
  @Mock private ScWikiVehicleSyncService vehicleSyncService;
  @Mock private ScWikiManufacturerSyncService manufacturerSyncService;

  // A real coordinator (spied so it can be told the gate is busy); its default runs the sweep.
  @Spy private SyncCoordinator syncCoordinator = new SyncCoordinator(3_600_000);

  @InjectMocks private ScWikiScheduler scheduler;

  @Test
  void schedule_isNoOp_whenMasterSwitchOff() {
    when(properties.getSchedulerEnabled()).thenReturn(false);

    scheduler.scheduleScWikiSync();

    verifyNoInteractions(
        commoditySyncService,
        vehicleSyncService,
        itemSyncService,
        blueprintSyncService,
        manufacturerSyncService);
  }

  @Test
  void schedule_runsEverySyncInDependencyOrder_whenEnabledAndGateFree() {
    when(properties.getSchedulerEnabled()).thenReturn(true);

    scheduler.scheduleScWikiSync();

    verify(commoditySyncService).syncCommodities();
    verify(vehicleSyncService).syncVehicles();
    verify(itemSyncService).syncItems();
    verify(blueprintSyncService).syncBlueprints();
    verify(manufacturerSyncService).syncManufacturers();
  }

  @Test
  void schedule_skipsEntireSweep_whenAnotherSyncIsAlreadyRunning() {
    when(properties.getSchedulerEnabled()).thenReturn(true);
    // The shared gate denies entry (a UEX or SC Wiki sync is already in flight) → no step runs.
    doReturn(false).when(syncCoordinator).runExclusively(eq("SC Wiki"), any());

    scheduler.scheduleScWikiSync();

    verifyNoInteractions(
        commoditySyncService,
        vehicleSyncService,
        itemSyncService,
        blueprintSyncService,
        manufacturerSyncService);
  }
}
