package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.iri.basetool.backend.service.JobTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobTypeCachingTest {

  @Autowired private JobTypeService jobTypeService;

  @Autowired private JobTypeRepository jobTypeRepository;

  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void initData() {
    jobTypeRepository.deleteAll();
    JobType a = new JobType();
    a.setName("Alpha");
    a.setArchetype(JobTypeArchetype.CREW);
    jobTypeRepository.save(a);

    JobType b = new JobType();
    b.setName("Beta");
    b.setArchetype(JobTypeArchetype.CREW);
    jobTypeRepository.save(b);

    // Clear cache entries before each test
    Cache cache = cacheManager.getCache("jobTypes");
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  void cacheShouldHit_OnSecondCall_WithSameParams() {
    Pageable pageable = PageRequest.of(0, 50, Sort.by("name"));

    // First call: expect miss
    jobTypeService.getJobTypes(null, pageable, false);
    // Second call: expect hit
    jobTypeService.getJobTypes(null, pageable, false);

    Cache cache = cacheManager.getCache("jobTypes");
    assert cache != null;
    Object cached = cache.get(new SimpleKey(null, pageable, false));
    org.junit.jupiter.api.Assertions.assertNotNull(
        cached, "Expected cache to contain entry after first call");
  }

  @Test
  void cacheShouldEvict_OnCreate() {
    Pageable pageable = PageRequest.of(0, 50, Sort.by("name"));

    // Prime cache (one miss)
    jobTypeService.getJobTypes(null, pageable, false);

    // Create new JobType -> should evict all entries
    JobType c = new JobType();
    c.setName("Gamma");
    c.setArchetype(JobTypeArchetype.CREW);
    jobTypeService.createJobType(c);

    Cache cache = cacheManager.getCache("jobTypes");
    assert cache != null;
    Object cachedAfterEvict = cache.get(new SimpleKey(null, pageable, false));
    org.junit.jupiter.api.Assertions.assertNull(
        cachedAfterEvict, "Expected cache to be evicted after create operation");

    // After eviction, next call should repopulate the cache
    jobTypeService.getJobTypes(null, pageable, false);
    Object cachedAfterRepopulate = cache.get(new SimpleKey(null, pageable, false));
    org.junit.jupiter.api.Assertions.assertNotNull(
        cachedAfterRepopulate, "Expected cache to be repopulated after subsequent call");
  }
}
