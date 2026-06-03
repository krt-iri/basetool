package de.greluc.krt.iri.basetool.frontend.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the frontend↔backend contract for the org chart: the page controller decodes {@code GET
 * /api/v1/org-chart} directly into the nested {@link OrgChartDto} record tree via the WebClient's
 * Jackson. The controller test mocks the client, so this test exercises the actual record (and
 * nested-record-list) deserialization with the same Jackson 3 {@link JsonMapper} the {@code
 * BackendApiClient} uses — guarding against a runtime decode failure that unit mocks would hide.
 */
class OrgChartDtoDeserializationTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private static final String SAMPLE_JSON =
      """
      {
        "areaLeadership": {
          "lead": {"positionId":"00000000-0000-0000-0000-0000000000a1","positionType":"AREA_LEAD",
                   "userId":"00000000-0000-0000-0000-0000000000b1","userName":"Boss","sortIndex":0,"version":3},
          "commanders": [],
          "coordinators": [
            {"positionId":"00000000-0000-0000-0000-0000000000a2","positionType":"AREA_COORDINATOR",
             "userId":"00000000-0000-0000-0000-0000000000b2","userName":"Coordinator","sortIndex":0,"version":0}
          ],
          "operators": []
        },
        "squadrons": [
          {
            "orgUnitId":"00000000-0000-0000-0000-000000000001","name":"IRIDIUM","shorthand":"IRI",
            "lead":{"positionId":"00000000-0000-0000-0000-0000000000a3","positionType":"SQUADRON_LEAD",
                    "userId":"00000000-0000-0000-0000-0000000000b3","userName":"Lead","sortIndex":0,"version":1},
            "commands":[
              {
                "positionId":"00000000-0000-0000-0000-0000000000a4","name":"Alpha","version":2,"sortIndex":0,
                "leaderUserId":"00000000-0000-0000-0000-0000000000b4","leaderUserName":"Cmd",
                "deputy":null,
                "ensigns":[
                  {"positionId":"00000000-0000-0000-0000-0000000000a5","positionType":"ENSIGN",
                   "userId":"00000000-0000-0000-0000-0000000000b5","userName":"Ensign","sortIndex":0,"version":0}
                ]
              },
              {
                "positionId":"00000000-0000-0000-0000-0000000000a7","name":null,"version":0,"sortIndex":1,
                "leaderUserId":null,"leaderUserName":null,"deputy":null,"ensigns":[]
              }
            ],
            "directEnsigns":[],
            "canAddCommand":true,
            "canAddEnsign":false
          }
        ],
        "specialCommands":[
          {
            "orgUnitId":"00000000-0000-0000-0000-000000000002","name":"Alpha SK","shorthand":"ASK",
            "commanders":[
              {"positionId":"00000000-0000-0000-0000-0000000000a6","positionType":"SK_COMMANDER",
               "userId":"00000000-0000-0000-0000-0000000000b6","userName":"SkLead","sortIndex":0,"version":0}
            ],
            "canAddCommander":true
          }
        ]
      }
      """;

  @Test
  void deserializesNestedRecordTree() {
    OrgChartDto chart = MAPPER.readValue(SAMPLE_JSON, OrgChartDto.class);

    assertNotNull(chart.areaLeadership().lead());
    assertEquals("AREA_LEAD", chart.areaLeadership().lead().positionType());
    assertEquals(3L, chart.areaLeadership().lead().version());
    assertEquals(1, chart.areaLeadership().coordinators().size());
    assertTrue(chart.areaLeadership().commanders().isEmpty());

    assertEquals(1, chart.squadrons().size());
    SquadronChartDto squadron = chart.squadrons().getFirst();
    assertEquals("IRIDIUM", squadron.name());
    assertEquals("SQUADRON_LEAD", squadron.lead().positionType());
    assertTrue(squadron.canAddCommand());
    assertFalse(squadron.canAddEnsign());

    assertEquals(2, squadron.commands().size());
    CommandChartDto command = squadron.commands().getFirst();
    assertEquals("Alpha", command.name());
    assertEquals("Cmd", command.leaderUserName());
    assertNull(command.deputy());
    assertEquals(1, command.ensigns().size());
    assertEquals("ENSIGN", command.ensigns().getFirst().positionType());
    CommandChartDto leaderless = squadron.commands().get(1);
    assertNull(leaderless.name(), "an unnamed Kommando decodes a null name");
    assertNull(leaderless.leaderUserId(), "a leaderless Kommando decodes a null holder");

    assertEquals(1, chart.specialCommands().size());
    assertEquals(
        "SK_COMMANDER", chart.specialCommands().getFirst().commanders().getFirst().positionType());
    assertTrue(chart.specialCommands().getFirst().canAddCommander());
  }
}
