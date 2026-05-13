package de.greluc.krt.iri.basetool.backend;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class JobTypeFilteringTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private JobTypeRepository jobTypeRepository;

  @Autowired
  private de.greluc.krt.iri.basetool.backend.repository.MissionRepository missionRepository;

  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void setup() {
    if (cacheManager != null) {
      cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @WithMockUser
  void testFilterJobTypesByArchetype() throws Exception {
    missionRepository.deleteAll();
    jobTypeRepository.deleteAll();

    JobType missionJob = new JobType();
    missionJob.setName("Pilot");
    missionJob.setArchetype(JobTypeArchetype.MISSION);
    jobTypeRepository.save(missionJob);

    JobType crewJob = new JobType();
    crewJob.setName("Gunner");
    crewJob.setArchetype(JobTypeArchetype.CREW);
    jobTypeRepository.save(crewJob);
    jobTypeRepository.flush();

    // Test filtering MISSION
    mockMvc
        .perform(get("/api/v1/job-types?archetype=MISSION"))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Pilot"));

    // Test filtering CREW
    mockMvc
        .perform(get("/api/v1/job-types?archetype=CREW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Gunner"));

    // Test no filter
    mockMvc
        .perform(get("/api/v1/job-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }
}
