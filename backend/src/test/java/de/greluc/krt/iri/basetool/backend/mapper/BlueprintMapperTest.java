package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementGroup;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementModifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** Unit tests for the MapStruct {@link BlueprintMapper}. */
class BlueprintMapperTest {

  private final BlueprintMapper mapper = Mappers.getMapper(BlueprintMapper.class);

  @Test
  void toDto_mapsHeaderGroupsModifiersAndIngredientSnapshotName() {
    Blueprint bp = new Blueprint();
    bp.setScwikiUuid(UUID.randomUUID());
    bp.setScwikiKey("BP_CRAFT_AMRS_LaserCannon_S1");
    bp.setOutputName("Omnisky III Cannon");
    bp.setIsAvailableByDefault(false);
    bp.setCraftTimeSeconds(540);

    BlueprintRequirementGroup group = new BlueprintRequirementGroup();
    group.setOrderIndex(0);
    group.setName("Emitter");
    group.setGroupKey("EMITTER");
    BlueprintRequirementModifier modifier = new BlueprintRequirementModifier();
    modifier.setOrderIndex(0);
    modifier.setPropertyKey("weapon_damage");
    modifier.setLabel("Impact Force");
    modifier.setBetterWhen("higher");
    modifier.setModifierAtMinQuality(0.95);
    modifier.setModifierAtMaxQuality(1.05);
    group.addModifier(modifier);
    bp.addRequirementGroup(group);

    BlueprintIngredient ingredient = new BlueprintIngredient();
    ingredient.setOrderIndex(0);
    ingredient.setKind(BlueprintIngredientKind.ITEM);
    ingredient.setWikiNameSnapshot("Hadanite");
    ingredient.setQuantityUnits(7);
    ingredient.setRequirementGroup(group);
    bp.addIngredient(ingredient);

    BlueprintDto dto = mapper.toDto(bp);

    assertEquals("Omnisky III Cannon", dto.outputName());
    assertEquals(540, dto.craftTimeSeconds());
    assertEquals(1, dto.requirementGroups().size());
    assertEquals("Emitter", dto.requirementGroups().get(0).name());
    assertEquals(1, dto.requirementGroups().get(0).modifiers().size());
    assertEquals("Impact Force", dto.requirementGroups().get(0).modifiers().get(0).label());
    assertEquals(1.05, dto.requirementGroups().get(0).modifiers().get(0).modifierAtMaxQuality());
    // The flat ingredient list takes its display name from the Wiki snapshot and the kind enum
    // name.
    assertEquals(1, dto.ingredients().size());
    assertEquals("Hadanite", dto.ingredients().get(0).name());
    assertEquals("ITEM", dto.ingredients().get(0).kind());
    assertEquals(7, dto.ingredients().get(0).quantityUnits());
  }
}
