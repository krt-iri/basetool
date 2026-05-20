package de.greluc.krt.iri.basetool.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that mechanically enforce the architectural invariants from CLAUDE.md.
 *
 * <p>Each rule below corresponds to a bullet in the project guide:
 *
 * <ul>
 *   <li>"Authorization is centralized in {@code @PreAuthorize} annotations on services/controllers
 *       — keep checks out of business logic." → {@link
 *       #serviceLayerShouldNotReachIntoSecurityContext()}, {@link
 *       #controllerLayerShouldNotReachIntoSecurityContext()} and {@link
 *       #mapperLayerShouldNotReachIntoSecurityContext()} (no {@code SecurityContextHolder} outside
 *       the dedicated auth-helper services) plus {@link
 *       #everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation()}.
 *   <li>"DTOs only at boundaries. Never expose JPA entities at controller boundaries." → {@link
 *       #controllerMethodsShouldNotReturnJpaEntities()}.
 * </ul>
 *
 * <p>These rules are static checks against the imported bytecode under {@code
 * de.greluc.krt.iri.basetool.backend.*}; tests on the test classpath are excluded so the rules
 * describe the production-code contract only.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("de.greluc.krt.iri.basetool.backend");

  private static final String SECURITY_CONTEXT_HOLDER =
      "org.springframework.security.core.context.SecurityContextHolder";

  private static final String PRE_AUTHORIZE =
      "org.springframework.security.access.prepost.PreAuthorize";

  private static final String JPA_ENTITY = "jakarta.persistence.Entity";

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
  private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
  private static final String DELETE_MAPPING =
      "org.springframework.web.bind.annotation.DeleteMapping";
  private static final String PATCH_MAPPING =
      "org.springframework.web.bind.annotation.PatchMapping";

  private static final String REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";

  /**
   * DTOs that are response-only — they may be returned from {@code @GetMapping} methods or used as
   * {@code @PostMapping} return types, but MUST NOT be accepted as a {@code @RequestBody} on any
   * state-changing endpoint. They carry server-managed fields ({@code id}, {@code version}, {@code
   * owningSquadron}, {@code parent}, role-derived flags) which, if let through a write binding,
   * become a mass-assignment vector (audit finding C-3: the original {@code POST /api/v1/missions}
   * accepted the full {@code MissionDto} and let any authenticated caller overwrite a foreign
   * squadron's mission via {@code EntityManager.merge}).
   *
   * <p>Add to this list when a new response DTO ships with server-managed fields. The corresponding
   * write endpoints must then accept a dedicated {@code …Request} record from {@code dto/request/}
   * carrying only caller-controllable fields.
   */
  private static final Set<String> RESPONSE_ONLY_DTOS =
      Set.of("de.greluc.krt.iri.basetool.backend.model.dto.MissionDto");

  /**
   * Java-generic wrappers that controllers legitimately return (paging envelopes, optional results,
   * response wrappers). The Entity-Generic rule below scans the actual type arguments of these
   * wrappers to make sure a JPA {@code @Entity} never leaks through.
   */
  private static final Set<String> ENTITY_GENERIC_WRAPPERS =
      Set.of(
          "org.springframework.http.ResponseEntity",
          "org.springframework.data.domain.Page",
          "org.springframework.data.domain.Slice",
          "java.util.List",
          "java.util.Set",
          "java.util.Collection",
          "java.util.Optional",
          "java.lang.Iterable");

  /**
   * Method-name prefixes that the codebase uses for state-mutating service operations. Used by
   * {@link #mutatingServiceMethodsInReadOnlyClassesNeedExplicitTransactional()} to find methods
   * that must override a class-level {@code @Transactional(readOnly = true)} with their own
   * {@code @Transactional}. The list is conservative — anything that does NOT start with one of
   * these prefixes is treated as a read operation.
   */
  private static final Set<String> MUTATING_METHOD_PREFIXES =
      Set.of(
          "create",
          "update",
          "delete",
          "add",
          "remove",
          "save",
          "store",
          "book",
          "handover",
          "link",
          "unlink",
          "move",
          "reset",
          "patch",
          "complete",
          "approve",
          "reject",
          "publish",
          "cancel",
          "join",
          "leave",
          "register",
          "unregister",
          "set",
          "insert",
          "merge",
          "assign",
          "unassign",
          "increment",
          "decrement",
          "clear",
          "purge",
          "import",
          "sync");

  /**
   * Classes that are allowed to reach into {@link
   * org.springframework.security.core.context.SecurityContextHolder} despite being on the
   * service-layer package. By design the list contains exactly one entry — {@code
   * AuthHelperService} — so there is a single source of truth for "what is the current
   * authentication" across the codebase.
   */
  private static final java.util.Set<String> SECURITY_CONTEXT_HOLDER_EXCEPTIONS =
      java.util.Set.of(
          // The dedicated auth-helper service. Centralises the SecurityContextHolder
          // read so the rest of the codebase can consult the current authentication
          // through a constructor-injected dependency. THIS is the seam — every other
          // service/controller/mapper must depend on AuthHelperService instead of
          // touching SecurityContextHolder directly.
          "de.greluc.krt.iri.basetool.backend.service.AuthHelperService");

  @Test
  void serviceLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: business logic in the service layer must rely on the @PreAuthorize
    // boundary at the controller (or the service method itself, when the rule is
    // role-based) instead of pulling the JWT subject straight from
    // SecurityContextHolder. Otherwise the same service method behaves differently
    // depending on which thread invokes it (Spring scheduling, async, message
    // listeners) and the data-isolation rules become testable only through full
    // Spring context tests. The single allowed escape valve is AuthHelperService;
    // nothing else inside the business-service package should bypass that.
    noClasses()
        .that()
        .resideInAPackage("..backend.service..")
        .and()
        .haveNameNotMatching(allowedClassNamesRegex())
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Business services must not pull the authenticated principal directly; "
                + "use a controller-side @PreAuthorize check or inject AuthHelperService instead. "
                + "The allow-list lives at the top of this test file.")
        .check(CLASSES);
  }

  @Test
  void controllerLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: same rationale as serviceLayerShouldNotReachIntoSecurityContext —
    // controllers used to inline role-hierarchy checks via
    // `SecurityContextHolder.getContext().getAuthentication()` (see
    // JobOrderController#verifyAssigneeAccess, InventoryItemController#isLogisticianOrAbove
    // and RefineryOrderController#isLogisticianOrAbove before the refactor). The rule
    // now forbids that pattern across the controller package; controllers must
    // either accept the authentication as a method parameter (via @AuthenticationPrincipal
    // or `Authentication authentication`) or delegate the lookup to AuthHelperService.
    noClasses()
        .that()
        .resideInAPackage("..backend.controller..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Controllers must read the principal via @AuthenticationPrincipal / "
                + "Authentication parameters, or delegate to AuthHelperService — direct "
                + "SecurityContextHolder access splits the auth contract across the codebase.")
        .check(CLASSES);
  }

  @Test
  void mapperLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: MapStruct mappers are supposed to be pure transformers. Reaching
    // into SecurityContextHolder from a resolver method (as MissionMapper did before
    // the refactor) couples DTO shaping to the request-scoped security context and
    // makes the mapper untestable without a full Spring security setup. If a mapper
    // needs to know "is the caller authenticated / which roles do they have", it
    // must depend on AuthHelperService — never on SecurityContextHolder directly.
    noClasses()
        .that()
        .resideInAPackage("..backend.mapper..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Mappers must stay pure transformers; route any auth lookup through "
                + "AuthHelperService so the mapper does not depend on the request-scoped "
                + "SecurityContextHolder.")
        .check(CLASSES);
  }

  @Test
  void controllerMethodsShouldNotReturnJpaEntities() {
    // Reasoning: CLAUDE.md is explicit — "Never expose JPA entities at controller
    // boundaries." A leaked entity drags in Hibernate lazy-loading semantics across
    // the HTTP boundary (Jackson serialising a proxy triggers the famous
    // LazyInitializationException) AND can expose internal columns to the client.
    // The check is intentionally narrow: ArchUnit can only inspect the *raw* return
    // type, so `ResponseEntity<User>` would not be caught by `haveRawReturnType(...)`.
    // In this codebase entity-returning controller methods would still be visible
    // here because the convention is to return the entity directly, not wrap it.
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .should()
        .haveRawReturnType(annotatedWith(JPA_ENTITY))
        .because(
            "Controllers must return DTOs (or Page<Dto>/ResponseEntity<Dto>), never raw JPA"
                + " entities.")
        .check(CLASSES);
  }

  @Test
  void everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation() {
    // Reasoning: every @RestController must make at least one explicit authorisation
    // decision somewhere — either a class-level @PreAuthorize or at least one
    // method-level @PreAuthorize. The weaker form (class-level) is sufficient to
    // catch the worst regression case: a controller that ships with zero auth
    // annotations and silently falls through to SecurityConfig's catch-all.
    //
    // We deliberately do NOT require every handler method to be annotated, because
    // the current codebase mixes the two patterns ("controller-level @PreAuthorize
    // covers everything" vs. "method-level @PreAuthorize per endpoint") and many
    // public endpoints are gated by SecurityConfig's `requestMatchers(...).permitAll()`
    // instead. Tightening to per-method is a separate, larger follow-up.
    classes()
        .that()
        .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .should(haveAtLeastOnePreAuthorizeAnnotation())
        .because(
            "Every REST controller class must declare at least one @PreAuthorize annotation (either"
                + " on the class or on any method) so it cannot silently bypass authorisation."
                + " Public endpoints should use @PreAuthorize(\"permitAll()\").")
        .check(CLASSES);
  }

  @Test
  void controllerLayerShouldNotDependOnRepositoryLayer() {
    // Reasoning: CLAUDE.md prescribes a strict controller → service → repository layering.
    // A controller injecting a Spring Data repository directly skips the service layer where
    // multi-user data isolation, transactional boundaries and the @PreAuthorize logic live —
    // and once that shortcut exists, it tends to multiply. Forbidding the dependency at the
    // package level (controllers must not even see repositories) makes the layering breach
    // visible at compile time instead of in a long code review.
    noClasses()
        .that()
        .resideInAPackage("..backend.controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..backend.repository..")
        .because(
            "Controllers must go through the service layer — a controller depending on a "
                + "repository bypasses @Transactional boundaries, owner filtering and the "
                + "@PreAuthorize seam, all of which live in services.")
        .check(CLASSES);
  }

  @Test
  void controllerMethodsShouldNotExposeJpaEntitiesInGenericWrappers() {
    // Reasoning: complements `controllerMethodsShouldNotReturnJpaEntities()`. That sister rule
    // only inspects the *raw* return type, so a method that returns `ResponseEntity<User>` or
    // `Page<Mission>` slips through — even though the JPA entity still ends up serialised on
    // the wire with all the lazy-loading / column-leak risks CLAUDE.md warns about. This rule
    // walks the actual generic type arguments of the known wrapper types
    // ({@link #ENTITY_GENERIC_WRAPPERS}) and rejects any wrapper carrying a {@code @Entity}.
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .should(notReturnAnEntityInsideAGenericWrapper())
        .because(
            "Controllers must wrap DTOs, never JPA entities, even when the entity is "
                + "tucked inside ResponseEntity<…>/Page<…>/List<…>/Optional<…>/etc.")
        .check(CLASSES);
  }

  @Test
  void mutatingServiceMethodsInReadOnlyClassesNeedExplicitTransactional() {
    // Reasoning: many services declare a class-level @Transactional(readOnly = true) so that
    // their query methods inherit a read-only transaction by default. Mutating methods on such
    // a class MUST override that with their own @Transactional, otherwise the JPA writes
    // either fail (Postgres refuses INSERT/UPDATE under SET TRANSACTION READ ONLY) or, worse,
    // silently no-op because the persistence context is never flushed. Forgetting this
    // override is a subtle bug that does not surface in `application-dev.yml` (some drivers
    // tolerate it) but breaks in prod.
    //
    // The rule fires when:
    //   * the declaring class carries @Transactional(readOnly = true), AND
    //   * the method name starts with a mutating prefix (see MUTATING_METHOD_PREFIXES), AND
    //   * the method itself is not annotated with @Transactional.
    // It does NOT inspect the method body, so the heuristic relies on the project's naming
    // convention (createX/updateX/deleteX/addX/removeX/…). False positives can be silenced
    // by simply annotating the method with @Transactional(readOnly = true) explicitly.
    classes()
        .that()
        .resideInAPackage("..backend.service..")
        .should(declareTransactionalForMutatingMethodsWhenClassIsReadOnly())
        .because(
            "A class-level @Transactional(readOnly = true) silently propagates to every "
                + "method — mutating operations must explicitly override it with their own "
                + "@Transactional, otherwise the write happens in a read-only transaction.")
        .check(CLASSES);
  }

  @Test
  void writeEndpointsMustDeclareAnAuthorisationAnnotation() {
    // Reasoning: tightens `everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation`
    // from "the class declares *some* @PreAuthorize" to "every state-changing endpoint
    // (@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping) carries explicit authorisation",
    // either inline on the method or class-wide. The previous, weaker form let a brand-new
    // write endpoint slip through without an explicit decision as long as some other method
    // on the same controller had a @PreAuthorize — exactly the regression case that hides a
    // missing auth check behind an unrelated annotation.
    //
    // Public endpoints are allowed but must be EXPLICIT: annotate them with
    // @PreAuthorize("permitAll()") so the decision is visible at the method level instead of
    // hiding two folders away in SecurityConfig's requestMatchers list.
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .and(isAnnotatedWithAnyOf(POST_MAPPING, PUT_MAPPING, DELETE_MAPPING, PATCH_MAPPING))
        .should(haveMethodOrClassLevelPreAuthorize())
        .because(
            "Every state-changing HTTP endpoint must carry an explicit @PreAuthorize "
                + "(either method-level or class-level). For deliberately public endpoints "
                + "use @PreAuthorize(\"permitAll()\") so the auth contract stays visible "
                + "next to the handler instead of buried in SecurityConfig.")
        .check(CLASSES);
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  private static String allowedClassNamesRegex() {
    return SECURITY_CONTEXT_HOLDER_EXCEPTIONS.stream()
        .map(java.util.regex.Pattern::quote)
        .reduce((a, b) -> a + "|" + b)
        .orElseThrow();
  }

  private static ArchCondition<JavaClass> haveAtLeastOnePreAuthorizeAnnotation() {
    return new ArchCondition<JavaClass>(
        "declare @PreAuthorize on the class or on at least one method") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        if (clazz.isAnnotatedWith(PRE_AUTHORIZE)) {
          return;
        }
        for (JavaMethod method : clazz.getMethods()) {
          if (method.isAnnotatedWith(PRE_AUTHORIZE)) {
            return;
          }
        }
        events.add(
            SimpleConditionEvent.violated(
                clazz,
                clazz.getFullName()
                    + " is a @RestController but declares no @PreAuthorize "
                    + "annotation on the class or on any of its methods"));
      }
    };
  }

  private static DescribedPredicate<JavaClass> annotatedWith(String annotationFqn) {
    return new DescribedPredicate<JavaClass>("annotated with @" + annotationFqn) {
      @Override
      public boolean test(JavaClass clazz) {
        return clazz.isAnnotatedWith(annotationFqn);
      }
    };
  }

  private static DescribedPredicate<JavaMethod> isAnnotatedWithAnyOf(String... annotationFqns) {
    String description = "annotated with any of " + String.join(", ", annotationFqns);
    return new DescribedPredicate<JavaMethod>(description) {
      @Override
      public boolean test(JavaMethod method) {
        for (String fqn : annotationFqns) {
          if (method.isAnnotatedWith(fqn)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  private static ArchCondition<JavaMethod> notReturnAnEntityInsideAGenericWrapper() {
    return new ArchCondition<JavaMethod>("not return a JPA entity inside a known generic wrapper") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        JavaType returnType = method.getReturnType();
        if (!(returnType instanceof JavaParameterizedType parameterized)) {
          return;
        }
        JavaClass rawType = parameterized.toErasure();
        if (!ENTITY_GENERIC_WRAPPERS.contains(rawType.getFullName())) {
          return;
        }
        for (JavaType arg : parameterized.getActualTypeArguments()) {
          JavaClass argClass = arg.toErasure();
          if (argClass.isAnnotatedWith(JPA_ENTITY)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName()
                        + " returns "
                        + rawType.getSimpleName()
                        + "<"
                        + argClass.getSimpleName()
                        + "> — JPA entities must not "
                        + "be exposed through generic wrappers; map to a DTO first."));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass>
      declareTransactionalForMutatingMethodsWhenClassIsReadOnly() {
    return new ArchCondition<JavaClass>(
        "declare method-level @Transactional on mutating methods when the class is"
            + " @Transactional(readOnly = true)") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        if (!isClassReadOnlyTransactional(clazz)) {
          return;
        }
        for (JavaMethod method : clazz.getMethods()) {
          if (!method
              .getModifiers()
              .contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
            continue;
          }
          if (!hasMutatingNamePrefix(method.getName())) {
            continue;
          }
          if (method.isAnnotatedWith(TRANSACTIONAL)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " — declaring class is @Transactional(readOnly = true) "
                      + "but this mutating method has no @Transactional override; writes "
                      + "would happen in a read-only transaction. Annotate the method with "
                      + "@Transactional or rename it to a non-mutating prefix."));
        }
      }
    };
  }

  private static ArchCondition<JavaMethod> haveMethodOrClassLevelPreAuthorize() {
    return new ArchCondition<JavaMethod>(
        "declare @PreAuthorize on the method or on the declaring class") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (method.isAnnotatedWith(PRE_AUTHORIZE)) {
          return;
        }
        if (method.getOwner().isAnnotatedWith(PRE_AUTHORIZE)) {
          return;
        }
        events.add(
            SimpleConditionEvent.violated(
                method,
                method.getFullName()
                    + " — state-changing endpoint without @PreAuthorize. "
                    + "Add @PreAuthorize on the method (or class-level) — use "
                    + "@PreAuthorize(\"permitAll()\") if the endpoint is deliberately public."));
      }
    };
  }

  private static boolean isClassReadOnlyTransactional(JavaClass clazz) {
    if (!clazz.isAnnotatedWith(TRANSACTIONAL)) {
      return false;
    }
    JavaAnnotation<?> annotation = clazz.getAnnotationOfType(TRANSACTIONAL);
    return annotation
        .tryGetExplicitlyDeclaredProperty("readOnly")
        .map(value -> Boolean.TRUE.equals(value))
        .orElse(false);
  }

  private static boolean hasMutatingNamePrefix(String methodName) {
    String lower = methodName.toLowerCase(java.util.Locale.ROOT);
    for (String prefix : MUTATING_METHOD_PREFIXES) {
      if (lower.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code true} iff one of the method's request-mapping annotations declares a path that contains
   * the literal {@code "{id}"} placeholder. Used by {@link
   * #staffelScopedWriteEndpointsMustGateOnSquadronScopeService()} to scope the rule to endpoints
   * that target a primary-resource aggregate id (and skip create / bulk / cross-user-administrative
   * endpoints whose only {@code UUID} path variable is a related entity like {@code userId}).
   */
  private static boolean mappingPathContainsIdPlaceholder(JavaMethod method) {
    String[] candidateAnnotations = {POST_MAPPING, PUT_MAPPING, PATCH_MAPPING, DELETE_MAPPING};
    for (String fqn : candidateAnnotations) {
      if (!method.isAnnotatedWith(fqn)) {
        continue;
      }
      JavaAnnotation<?> ann = method.getAnnotationOfType(fqn);
      Object raw = ann.tryGetExplicitlyDeclaredProperty("value").orElse(null);
      if (raw == null) {
        continue;
      }
      if (raw instanceof String s) {
        if (s.contains("{id}")) {
          return true;
        }
      } else if (raw instanceof Object[] arr) {
        for (Object o : arr) {
          if (o instanceof String s && s.contains("{id}")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Staffel-scoped aggregate services MUST consult either {@code AuthHelperService} (for raw
   * principal / role lookups) or {@code SquadronScopeService} (for canSee/canEdit + active-context
   * resolution) - otherwise the data they emit might leak across squadrons. Phase 3 of
   * MULTI_SQUADRON_PLAN.md tracks this as a defensive ArchUnit guard against future drift.
   *
   * <p>{@code JobOrderService} and {@code JobOrderHandoverService} are intentionally excluded: Job
   * Orders are a cross-staffel workspace (MULTI_SQUADRON_PLAN.md section 1) so they legitimately
   * operate without a squadron filter. They do depend on {@code AuthHelperService} for the owner
   * stamp at create time, which keeps the test honest by still being satisfied for them via the
   * AuthHelperService route.
   */
  @Test
  void staffelScopedServicesMustWireSquadronOrAuthHelper() {
    // JobOrderService AND JobOrderHandoverService are intentionally excluded — Job Orders are a
    // cross-staffel workspace by design (MULTI_SQUADRON_PLAN.md section 1 + 4.6), so the squadron
    // filter does not apply. Both services do inject AuthHelperService anyway for the owner stamp
    // (JobOrderService) and the audit stamp on the handover record (JobOrderHandoverService) —
    // verified by their own unit tests rather than by this rule.
    Set<String> staffelScopedServiceNames =
        Set.of(
            "MissionService",
            "InventoryItemService",
            "RefineryOrderService",
            "HangarService",
            "OperationService");

    String authHelper = "de.greluc.krt.iri.basetool.backend.service.AuthHelperService";
    String squadronScope = "de.greluc.krt.iri.basetool.backend.service.SquadronScopeService";

    classes()
        .that(
            new DescribedPredicate<JavaClass>("is one of the staffel-scoped aggregate services") {
              @Override
              public boolean test(JavaClass javaClass) {
                return staffelScopedServiceNames.contains(javaClass.getSimpleName());
              }
            })
        .should(
            new ArchCondition<>("depend on AuthHelperService or SquadronScopeService") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasIt =
                    javaClass.getFields().stream()
                        .map(f -> f.getRawType().getFullName())
                        .anyMatch(t -> t.equals(authHelper) || t.equals(squadronScope));
                if (!hasIt) {
                  events.add(
                      SimpleConditionEvent.violated(
                          javaClass,
                          javaClass.getName()
                              + " is in the staffel-scoped service whitelist but injects neither"
                              + " AuthHelperService nor SquadronScopeService - that means it"
                              + " cannot enforce the multi-tenant filter / squadron stamp."));
                }
              }
            })
        .check(CLASSES);
  }

  /**
   * Plan-compliant ArchUnit guard #3 (MULTI_SQUADRON_PLAN.md section 4.6): write endpoints on
   * staffel-scoped aggregates MUST use a {@code @PreAuthorize} expression that calls into the
   * {@code SquadronScopeService} (canEdit* / canSee*). A bare
   * {@code @PreAuthorize("isAuthenticated()")} on POST / PUT / PATCH / DELETE for {@code
   * /api/v1/missions}, {@code /api/v1/operations}, {@code /api/v1/hangar}, {@code
   * /api/v1/inventory} or {@code /api/v1/refinery-orders} would silently allow cross-staffel writes
   * — exactly the regression class this rule prevents.
   *
   * <p>The rule inspects all write methods (POST/PUT/PATCH/DELETE) on the affected controllers but
   * only fires when the URL path carries a primary-resource id placeholder (i.e. {@code /{id}}).
   * POSTs that do not target a specific resource (top-level create, bulk operations,
   * cross-user-administrative endpoints like {@code /users/{userId}/...}) are skipped — the service
   * layer enforces ownership there and a per-id squadron gate has nothing to bind to.
   *
   * <ul>
   *   <li>Read endpoints stay free of the rule — list endpoints lean on service-layer filtering
   *       rather than per-row {@code @PreAuthorize}.
   *   <li>{@code /api/v1/orders} (job orders) and {@code /api/v1/admin/**} are excluded — job
   *       orders are a cross-staffel workspace by design, admin endpoints already require {@code
   *       hasRole('ADMIN')} which carries no squadron component.
   *   <li>Endpoints that use a role-only check ({@code hasRole('LOGISTICIAN')} etc.) without
   *       additionally calling the squadron-scope service still violate — the rule looks for the
   *       literal {@code squadronScopeService} reference in the SpEL expression.
   * </ul>
   */
  @Test
  void staffelScopedWriteEndpointsMustGateOnSquadronScopeService() {
    Set<String> staffelScopedControllerSimpleNames =
        Set.of(
            "MissionController",
            "OperationController",
            "HangarController",
            "InventoryItemController",
            "RefineryOrderController");

    noMethods()
        .that()
        .areDeclaredInClassesThat(
            new DescribedPredicate<JavaClass>("are staffel-scoped aggregate REST controllers") {
              @Override
              public boolean test(JavaClass javaClass) {
                return staffelScopedControllerSimpleNames.contains(javaClass.getSimpleName());
              }
            })
        .and()
        .areAnnotatedWith(
            new DescribedPredicate<JavaAnnotation<?>>(
                "are a modify-mapping annotation (POST/PUT/PATCH/DELETE)") {
              @Override
              public boolean test(JavaAnnotation<?> annotation) {
                // POST is included alongside PUT/PATCH/DELETE because POST /{id}/<action> can
                // mutate a specific resource (e.g. /inventory/{id}/book-out,
                // /refinery-orders/{id}/store, /missions/{id}/join) — without a squadron gate a
                // Logistician of squadron A could trigger the action on a squadron-B resource.
                // The inner check filters out POSTs whose path does not target a primary resource
                // id so create / bulk / administrative endpoints are not falsely flagged.
                String fqcn = annotation.getRawType().getFullName();
                return POST_MAPPING.equals(fqcn)
                    || PUT_MAPPING.equals(fqcn)
                    || PATCH_MAPPING.equals(fqcn)
                    || DELETE_MAPPING.equals(fqcn);
              }
            })
        .and()
        .areAnnotatedWith(PRE_AUTHORIZE)
        .should(
            new ArchCondition<JavaMethod>(
                "gate on @squadronScopeService in the @PreAuthorize SpEL expression") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                // Skip endpoints that do not target a specific resource id in their path. The
                // condition is two-fold:
                //   * the method must accept a UUID @PathVariable (a primary-resource id), AND
                //   * the request mapping path must literally contain "{id}" (the canonical
                //     placeholder for the aggregate root's id; avoids false positives on
                //     administrative endpoints like POST /users/{userId}/ships where the path
                //     variable is a related user, not the aggregate id being mutated).
                boolean takesResourceIdPathVariable =
                    method.getParameters().stream()
                        .anyMatch(
                            p ->
                                p.isAnnotatedWith(
                                        "org.springframework.web.bind.annotation.PathVariable")
                                    && p.getRawType().getFullName().equals("java.util.UUID"));
                if (!takesResourceIdPathVariable) {
                  return;
                }
                if (!mappingPathContainsIdPlaceholder(method)) {
                  return;
                }

                JavaAnnotation<?> ann = method.getAnnotationOfType(PRE_AUTHORIZE);
                String value =
                    ann.tryGetExplicitlyDeclaredProperty("value").map(Object::toString).orElse("");
                // Accepted gate references:
                //   - @squadronScopeService.canSee*/canEdit* — the direct squadron-scope check;
                //   - @missionSecurityService.canManage*/canAccessParticipant/canChangeOwner —
                //     mission-aggregate gate that itself folds in canEditMission() for elevated
                //     authorities (see MissionSecurityService — squadron-scope-aware as of the
                //     Phase 6 follow-up);
                //   - hasRole('ADMIN') alone — admin always passes the squadron filter, no extra
                //     scope check needed (MULTI_SQUADRON_PLAN.md section 1).
                boolean hasSquadronScope = value.contains("squadronScopeService");
                boolean hasMissionSecurity = value.contains("missionSecurityService");
                boolean hasAdminOnly =
                    value.contains("hasRole('ADMIN')") && !value.contains("hasAnyRole(");
                if (!hasSquadronScope && !hasMissionSecurity && !hasAdminOnly) {
                  events.add(
                      SimpleConditionEvent.violated(
                          method,
                          method.getFullName()
                              + " is a write endpoint on a staffel-scoped aggregate but its"
                              + " @PreAuthorize expression does not gate on @squadronScopeService"
                              + " (or @missionSecurityService / hasRole('ADMIN')) - that means"
                              + " cross-staffel writes are not blocked. Add `and"
                              + " @squadronScopeService.canEdit*(#id)` to the SpEL"
                              + " (MULTI_SQUADRON_PLAN.md section 4.6)."));
                }
              }
            })
        .check(CLASSES);
  }

  /**
   * Audit finding C-1 guard (2026-05-20 security audit): mission endpoints gated only by
   * {@code @PreAuthorize("@squadronScopeService.canSeeMission(#id)")} (without an additional {@code
   * isAuthenticated()} / {@code hasRole(...)} / {@code hasAuthority(...)} clause) are reachable by
   * anonymous callers for non-internal missions — {@link
   * de.greluc.krt.iri.basetool.backend.config.SecurityConfig} declares the matching paths as {@code
   * permitAll}. Any such endpoint that returns a {@link
   * de.greluc.krt.iri.basetool.backend.model.dto.MissionDto}, a {@link
   * de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto} or a generic collection of
   * either MUST invoke one of the guest-redaction helpers ({@code cleanupMissionForGuest} / {@code
   * cleanupParticipantForGuest}) somewhere in its body; otherwise full participant PII (email, real
   * name, roles, permissions) is shipped to guests.
   *
   * <p>The rule fired on the original C-1 regression in {@code
   * MissionController.addParticipantPublic} and {@code MissionController.addParticipantSlim}, both
   * of which had the {@code canSeeMission} gate but skipped the redaction pass that {@code
   * getMissionById} / {@code getNextMission} already applied. Without this guard a future endpoint
   * added with the same gate would silently re-introduce the same leak.
   *
   * <p>The check is structural: it asserts the helper is referenced in the bytecode, NOT that the
   * call is conditional on {@code jwt == null}. The conditional branching is verified by the
   * per-endpoint unit tests. The intent of the ArchUnit rule is to catch the "I forgot the
   * redaction entirely" regression, which is the actual C-1 root cause.
   */
  @Test
  void anonymousReadableMissionEndpointsMustRedactGuestPii() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .and(hasGuestVisibleCanSeeMissionPreAuthorize())
        .and(returnsMissionDtoOrMissionParticipantDtoOrCollection())
        .should(callOneOfTheGuestRedactionHelpers())
        .because(
            "Mission endpoints reachable by anonymous callers must apply cleanupMissionForGuest "
                + "or cleanupParticipantForGuest before returning — audit finding C-1: "
                + "addParticipantPublic / addParticipantSlim previously leaked full participant "
                + "emails and real names to anonymous callers because the redaction pass that "
                + "getMissionById / getNextMission already used was skipped on the write paths.")
        .check(CLASSES);
  }

  /**
   * Mission DTOs whose participant nesting carries PII (email, first/last name, roles). Used by
   * {@link #anonymousReadableMissionEndpointsMustRedactGuestPii} to recognise return shapes that
   * must go through guest-redaction before reaching an anonymous caller. {@code
   * MissionFinanceEntryDto} is included because it embeds {@link
   * de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto} directly — the audit found
   * this transitive leak (C-2) in {@code MissionFinanceEntryController.createFinanceEntry}.
   */
  private static final Set<String> MISSION_PII_CARRYING_DTOS =
      Set.of(
          "de.greluc.krt.iri.basetool.backend.model.dto.MissionDto",
          "de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto",
          "de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto");

  /**
   * Naming convention for helper methods that strip participant PII for anonymous callers: {@code
   * cleanup<EntityName>ForGuest}. Examples in the codebase: {@code
   * MissionController#cleanupMissionForGuest}, {@code …#cleanupParticipantForGuest}, {@code
   * MissionFinanceEntryController#cleanupFinanceEntryForGuest}. The ArchUnit rule recognises any
   * call to a method matching this pattern as a valid redaction call — so adding a new
   * guest-reachable controller with its own entity-specific redactor (named accordingly) does not
   * require updating this test.
   *
   * @param name candidate method name
   * @return {@code true} iff {@code name} matches the {@code cleanup…ForGuest} convention
   */
  private static boolean isGuestRedactionHelperName(String name) {
    return name.startsWith("cleanup") && name.endsWith("ForGuest");
  }

  private static DescribedPredicate<JavaMethod> hasGuestVisibleCanSeeMissionPreAuthorize() {
    return new DescribedPredicate<JavaMethod>(
        "annotated with @PreAuthorize that gates on canSeeMission or canAccessParticipant"
            + " without an isAuthenticated/hasRole/hasAuthority clause") {
      @Override
      public boolean test(JavaMethod method) {
        if (!method.isAnnotatedWith(PRE_AUTHORIZE)) {
          return false;
        }
        JavaAnnotation<?> ann = method.getAnnotationOfType(PRE_AUTHORIZE);
        String value =
            ann.tryGetExplicitlyDeclaredProperty("value").map(Object::toString).orElse("");
        // {@code canAccessParticipant} returns true for any guest participant ({@code
        // p.getUser() == null}) — so an anonymous caller can reach the endpoint when the target
        // is a guest. The legacy participant endpoints (PUT /participants/{id}, check-in / -out,
        // payout-preference, DELETE /participants/{id}) all carry this gate and have shipped the
        // full MissionDto without redaction before the 2026-05-20 audit fix.
        if (!value.contains("canSeeMission") && !value.contains("canAccessParticipant")) {
          return false;
        }
        // Any of the following would force the caller to be authenticated, making jwt non-null
        // at runtime and the guest-redaction pass moot.
        return !value.contains("isAuthenticated()")
            && !value.contains("hasRole(")
            && !value.contains("hasAnyRole(")
            && !value.contains("hasAuthority(")
            && !value.contains("hasAnyAuthority(");
      }
    };
  }

  private static DescribedPredicate<JavaMethod>
      returnsMissionDtoOrMissionParticipantDtoOrCollection() {
    return new DescribedPredicate<JavaMethod>(
        "returns MissionDto / MissionParticipantDto, or a known generic wrapper of either") {
      @Override
      public boolean test(JavaMethod method) {
        JavaClass rawReturnType = method.getRawReturnType();
        if (MISSION_PII_CARRYING_DTOS.contains(rawReturnType.getFullName())) {
          return true;
        }
        JavaType returnType = method.getReturnType();
        if (!(returnType instanceof JavaParameterizedType parameterized)) {
          return false;
        }
        if (!ENTITY_GENERIC_WRAPPERS.contains(parameterized.toErasure().getFullName())) {
          return false;
        }
        for (JavaType arg : parameterized.getActualTypeArguments()) {
          if (MISSION_PII_CARRYING_DTOS.contains(arg.toErasure().getFullName())) {
            return true;
          }
        }
        return false;
      }
    };
  }

  /**
   * Audit finding C-3 guard (2026-05-20 security audit): write endpoints on REST controllers must
   * not accept a response-only DTO as {@code @RequestBody}. Response DTOs carry server-managed
   * fields ({@code id}, {@code version}, {@code owningSquadron}, …) which, if let through a JSON
   * binding into a fresh entity, become a mass-assignment vector — the original {@code POST
   * /api/v1/missions} accepted a full {@code MissionDto} and let any authenticated caller overwrite
   * a foreign squadron's mission row via {@code EntityManager.merge}. The fix migrated those
   * endpoints to dedicated {@code CreateMissionRequest} / {@code UpdateMissionRequest} records that
   * physically lack the dangerous fields.
   *
   * <p>This rule keeps the migration one-way: any future {@code @PostMapping} / {@code @PutMapping}
   * / {@code @PatchMapping} that tries to take a listed response-only DTO as its request body fails
   * the build. The {@code RESPONSE_ONLY_DTOS} allowlist at the top of this test file is the
   * explicit registry — extend it when a new response DTO ships with server-managed fields (every
   * staffel-scoped aggregate's main DTO is a candidate).
   */
  @Test
  void responseOnlyDtosMustNotBeAcceptedAsRequestBodyOnWriteEndpoints() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .and(isAnnotatedWithAnyOf(POST_MAPPING, PUT_MAPPING, PATCH_MAPPING))
        .should(notAcceptResponseOnlyDtoAsRequestBody())
        .because(
            "Write endpoints must accept a dedicated request DTO (e.g. CreateMissionRequest, "
                + "UpdateMissionRequest) that structurally excludes server-managed fields — "
                + "binding the full response DTO opens a mass-assignment vector. See audit "
                + "finding C-3 in CHANGELOG / MissionMapper#toEntity removal.")
        .check(CLASSES);
  }

  private static ArchCondition<JavaMethod> notAcceptResponseOnlyDtoAsRequestBody() {
    return new ArchCondition<JavaMethod>(
        "not declare a @RequestBody parameter of a response-only DTO type") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        method.getParameters().stream()
            .filter(p -> p.isAnnotatedWith(REQUEST_BODY))
            .filter(p -> RESPONSE_ONLY_DTOS.contains(p.getRawType().getFullName()))
            .forEach(
                p ->
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            method.getFullName()
                                + " — @RequestBody parameter of type "
                                + p.getRawType().getSimpleName()
                                + " is a response-only DTO; binding it on a write endpoint enables"
                                + " mass-assignment of server-managed fields (id, version,"
                                + " owningSquadron, …). Switch to a dedicated *Request record"
                                + " from backend/.../dto/request/. See audit finding C-3.")));
      }
    };
  }

  private static ArchCondition<JavaMethod> callOneOfTheGuestRedactionHelpers() {
    return new ArchCondition<JavaMethod>(
        "call a cleanup…ForGuest redaction helper from its own body") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean callsHelper =
            method.getMethodCallsFromSelf().stream()
                .map(call -> call.getTarget().getName())
                .anyMatch(ArchitectureTest::isGuestRedactionHelperName);
        if (callsHelper) {
          return;
        }
        // Method references (e.g. `stream.map(this::cleanupParticipantForGuest)`) are compiled
        // into a synthetic invokedynamic call site whose target is reachable via the bootstrap.
        // ArchUnit exposes that as a separate access kind — fall back to the broader call set so
        // the rule does not false-positive on the slim endpoint's stream pattern.
        boolean referencesHelper =
            method.getAccessesFromSelf().stream()
                .map(access -> access.getTarget().getName())
                .anyMatch(ArchitectureTest::isGuestRedactionHelperName);
        if (referencesHelper) {
          return;
        }
        events.add(
            SimpleConditionEvent.violated(
                method,
                method.getFullName()
                    + " — anonymous callers reach this endpoint (PreAuthorize gates on"
                    + " canSeeMission without forcing authentication) and the return type carries"
                    + " participant PII, but the method body does not invoke any cleanup…ForGuest"
                    + " redaction helper. Full participant emails / real names / roles will leak to"
                    + " guests — see audit findings C-1 / C-2."));
      }
    };
  }

  /**
   * Audit finding C-4 guard (2026-05-20 security audit): the unconditional server-side stamping of
   * {@code owningSquadron} / {@code owner} / {@code parent} in {@link
   * de.greluc.krt.iri.basetool.backend.service.MissionService#createMission} and {@link
   * de.greluc.krt.iri.basetool.backend.service.MissionService#addSubMission} relies on the
   * corresponding columns NEVER being present on the request DTOs. The C-3 refactor enforces this
   * structurally by giving the records only safe components, but a future maintainer could ship a
   * "small convenience" patch like adding {@code UUID owningSquadronId} to {@code
   * CreateMissionRequest} and re-wiring the service to honour it — that single step re-opens the
   * squadron-stamp-forgery vector (an authenticated SQUADRON_MEMBER of squadron A creates a mission
   * stamped as squadron B's, optionally with {@code isInternal=true} so it is hidden from A's
   * roster).
   *
   * <p>This rule locks down the shape: {@code CreateMissionRequest} and {@code
   * UpdateMissionRequest} must not declare any record component whose name matches a server-
   * managed concern. Adding a new column to {@link
   * de.greluc.krt.iri.basetool.backend.model.dto.MissionDto} response side is fine; adding {@code
   * owningSquadronId} / {@code parentId} / {@code ownerId} / {@code id} / etc. to the write-side
   * records is what this guard prevents.
   */
  @Test
  void missionWriteRequestDtosMustNotCarryServerManagedFields() {
    classes()
        .that()
        .haveFullyQualifiedName(
            "de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest")
        .or()
        .haveFullyQualifiedName(
            "de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionRequest")
        .should(notDeclareServerManagedRecordComponents())
        .because(
            "MissionService.createMission / addSubMission stamp owner / owningSquadron / parent"
                + " from the authenticated principal and the path-resolved parent — never from the"
                + " request body. The request records must not grow components for those concerns"
                + " or the squadron-stamp-forgery vector returns. See audit finding C-4.")
        .check(CLASSES);
  }

  /**
   * Record component names that are forbidden on Mission write DTOs (audit finding C-4): server-
   * managed concerns that the service stamps unconditionally and which a client-supplied value
   * would silently override. {@code version} is allowed on {@code UpdateMissionRequest} because it
   * is the optimistic-lock token — the check below carves it out for the update DTO only.
   */
  private static final Set<String> FORBIDDEN_MISSION_REQUEST_COMPONENTS =
      Set.of(
          // Identity / global version — set by the persistence layer.
          "id",
          "version",
          "coreVersion",
          "scheduleVersion",
          "flagsVersion",
          // Owner — stamped from the authenticated principal in createMission.
          "owner",
          "ownerId",
          // Managers — managed via dedicated /missions/{id}/managers endpoints.
          "managers",
          // Owning squadron — derived from owner.squadron / scope (createMission) or parent
          // (addSubMission); never the body.
          "owningSquadron",
          "owningSquadronId",
          "squadronId",
          "squadron",
          // Parent — for sub-missions, taken from the path variable; never the body.
          "parent",
          "parentId",
          // Sub-aggregate collections have their own write endpoints.
          "participants",
          "assignedUnits",
          "frequencies",
          "subMissions",
          "inventoryEntries",
          "refineryOrders",
          // Computed-on-response projections.
          "canEdit",
          "canManageManagers",
          "checkedInParticipants",
          "registeredParticipants");

  private static ArchCondition<JavaClass> notDeclareServerManagedRecordComponents() {
    return new ArchCondition<JavaClass>(
        "not declare any server-managed record component (owningSquadron, owner, parent, …)") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        boolean isUpdateDto = clazz.getSimpleName().equals("UpdateMissionRequest");
        clazz.getFields().stream()
            .filter(
                f ->
                    !f.getModifiers()
                        .contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
            .map(f -> f.getName())
            .filter(FORBIDDEN_MISSION_REQUEST_COMPONENTS::contains)
            // `version` is the optimistic-lock token on UpdateMissionRequest — legitimate there.
            .filter(name -> !(isUpdateDto && "version".equals(name)))
            .forEach(
                name ->
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            clazz.getFullName()
                                + " declares record component `"
                                + name
                                + "` — that field is server-managed (audit finding C-4). Stamp it"
                                + " inside MissionService, not from the request body. If this is"
                                + " legitimately client-supplied, justify in a code comment and"
                                + " carve it out of FORBIDDEN_MISSION_REQUEST_COMPONENTS.")));
      }
    };
  }
}
