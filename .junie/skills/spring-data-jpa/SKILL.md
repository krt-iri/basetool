---
name: spring-data-jpa-skill
description: Implement the persistence layer using Spring Data JPA in Spring Boot applications.
---

Follow the below principles when using Spring Data JPA:

1. Disable the Open Session in View (OSIV) filter:
   spring.jpa.open-in-view=false
2. Disable in-memory pagination:
   spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true

3. Avoid the N+1 SELECT problem: use JOIN FETCH to load associated child collections in a single query.
4. Avoid in-memory pagination: when loading a paginated list of parent entities with child collections:
  * First, load only the parent IDs using pagination
  * Then, load the full entities with their child collections using JOIN FETCH for those IDs
  * Assemble the final Page from the paginated IDs and the loaded entities


## Pagination with child collections example:
PostRepository.java
```java
public interface PostRepository extends JpaRepository<Post, Long> {

@Query("select p.id from Post p order by p.id")
Page<Long> findPostIds(Pageable pageable);

@Query("select distinct p from Post p left join fetch p.comments where p.id in :ids")
List<Post> findAllByIdInWithComments(@Param("ids") Collection<Long> ids);
}
```

PostService.java
```java
@Service
public class PostService {
private final PostRepository postRepository;

public PostService(PostRepository postRepository) {
this.postRepository = postRepository;
}

@Transactional(readOnly = true)
public Page<Post> findPosts(Pageable pageable) {
Page<Long> idsPage = postRepository.findPostIds(pageable);
if (idsPage.isEmpty()) {
return Page.empty(pageable);
}
List<Post> posts = postRepository.findAllByIdInWithComments(idsPage.getContent());
return new PageImpl<>(posts, pageable, idsPage.getTotalElements());
}
}
```