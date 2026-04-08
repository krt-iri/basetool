-- =====================================================================
-- V1 – Initial Schema
-- Erstellt das komplette Datenbankschema basierend auf den JPA-Entities.
-- =====================================================================

-- -------------------------------------------------------
-- Standalone / Lookup Tables (keine FK-Abhaengigkeiten)
-- -------------------------------------------------------

CREATE TABLE announcement (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    content         TEXT
);

CREATE TABLE faction (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_faction          INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    wiki                VARCHAR(255),
    is_piracy           BOOLEAN,
    is_bounty_hunting   BOOLEAN
);

CREATE TABLE jurisdiction (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_jurisdiction     INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    nickname            VARCHAR(255),
    wiki                VARCHAR(255),
    faction_name        VARCHAR(255)
);

CREATE TABLE star_system (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_system           INTEGER UNIQUE,
    name                VARCHAR(255) NOT NULL UNIQUE,
    description         TEXT,
    is_available_live   BOOLEAN,
    wiki                TEXT,
    jurisdiction_name   VARCHAR(255),
    faction_name        VARCHAR(255)
);

CREATE TABLE orbit (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_orbit            INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    star_system_name    VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE planet (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_planet           INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    star_system_name    VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE moon (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_moon             INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE city (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_city             INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    moon_name           VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE outpost (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_outpost          INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    nickname            VARCHAR(255),
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    moon_name           VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE space_station (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_space_station    INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    nickname            VARCHAR(255),
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    moon_name           VARCHAR(255),
    city_name           VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE poi (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_poi              INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    nickname            VARCHAR(255),
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    moon_name           VARCHAR(255),
    space_station_name  VARCHAR(255),
    outpost_name        VARCHAR(255),
    city_name           VARCHAR(255),
    faction_name        VARCHAR(255),
    jurisdiction_name   VARCHAR(255)
);

CREATE TABLE terminal (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    id_terminal         INTEGER UNIQUE,
    name                VARCHAR(255),
    code                VARCHAR(255),
    is_available_live   BOOLEAN,
    nickname            VARCHAR(255),
    star_system_name    VARCHAR(255),
    planet_name         VARCHAR(255),
    orbit_name          VARCHAR(255),
    moon_name           VARCHAR(255),
    space_station_name  VARCHAR(255),
    outpost_name        VARCHAR(255),
    city_name           VARCHAR(255),
    faction_name        VARCHAR(255),
    company_name        VARCHAR(255)
);

CREATE TABLE manufacturer (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    abbreviation    VARCHAR(255) NOT NULL UNIQUE,
    nickname        VARCHAR(255),
    wiki            VARCHAR(255),
    description     TEXT
);

CREATE TABLE material (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    id_commodity    INTEGER UNIQUE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    type            VARCHAR(255) NOT NULL,
    description     TEXT
);

CREATE TABLE squadron (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    shorthand       VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT
);

CREATE TABLE refining_method (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT
);

CREATE TABLE mission_lead_type (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT
);

-- -------------------------------------------------------
-- Role (PK = BIGINT IDENTITY)
-- -------------------------------------------------------

CREATE TABLE role (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT
);

CREATE TABLE role_permissions (
    role_id         BIGINT NOT NULL REFERENCES role(id),
    permission      VARCHAR(255)
);

-- -------------------------------------------------------
-- User (app_user, da "user" ein reserviertes Wort ist)
-- -------------------------------------------------------

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    username        VARCHAR(255),
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    email           VARCHAR(255),
    user_rank       INTEGER,
    description     TEXT
);

CREATE TABLE user_roles (
    user_id         UUID NOT NULL REFERENCES app_user(id),
    role_id         BIGINT NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, role_id)
);

-- -------------------------------------------------------
-- Job Type (self-referencing)
-- -------------------------------------------------------

CREATE TABLE job_type (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    archetype       VARCHAR(255) NOT NULL,
    parent_id       UUID REFERENCES job_type(id)
);

-- -------------------------------------------------------
-- Location (FK -> star_system)
-- -------------------------------------------------------

CREATE TABLE location (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    id_terminal     INTEGER UNIQUE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    star_system_id  UUID REFERENCES star_system(id)
);

-- -------------------------------------------------------
-- Ship Type (FK -> manufacturer)
-- -------------------------------------------------------

CREATE TABLE ship_type (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    manufacturer_id UUID REFERENCES manufacturer(id)
);

-- -------------------------------------------------------
-- Ship (FK -> ship_type, location, app_user)
-- -------------------------------------------------------

CREATE TABLE ship (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    name            VARCHAR(255),
    ship_type_id    UUID NOT NULL REFERENCES ship_type(id),
    insurance       VARCHAR(255),
    location_id     UUID REFERENCES location(id),
    fitted          BOOLEAN NOT NULL DEFAULT FALSE,
    owner_id        UUID NOT NULL REFERENCES app_user(id)
);

-- -------------------------------------------------------
-- Material Price (FK -> material, location)
-- -------------------------------------------------------

CREATE TABLE material_price (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    material_id     UUID NOT NULL REFERENCES material(id),
    location_id     UUID NOT NULL REFERENCES location(id),
    price_buy       NUMERIC,
    price_sell      NUMERIC,
    scu_buy         INTEGER,
    scu_sell        INTEGER,
    scu_sell_stock  INTEGER,
    status_buy      BOOLEAN,
    status_sell     BOOLEAN,
    date_modified   TIMESTAMP WITH TIME ZONE,
    UNIQUE (material_id, location_id)
);

-- -------------------------------------------------------
-- Mission (self-referencing parent)
-- -------------------------------------------------------

CREATE TABLE mission (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    name                VARCHAR(255),
    description         TEXT,
    status              VARCHAR(255),
    meeting_time        TIMESTAMP WITH TIME ZONE,
    planned_start_time  TIMESTAMP WITH TIME ZONE,
    actual_start_time   TIMESTAMP WITH TIME ZONE,
    planned_end_time    TIMESTAMP WITH TIME ZONE,
    actual_end_time     TIMESTAMP WITH TIME ZONE,
    parent_mission_id   UUID REFERENCES mission(id)
);

-- -------------------------------------------------------
-- Mission Participant
-- -------------------------------------------------------

CREATE TABLE mission_participant (
    id                              UUID PRIMARY KEY,
    version                         BIGINT,
    created_at                      TIMESTAMP WITH TIME ZONE,
    updated_at                      TIMESTAMP WITH TIME ZONE,
    mission_id                      UUID NOT NULL REFERENCES mission(id),
    user_id                         UUID REFERENCES app_user(id),
    guest_name                      VARCHAR(255),
    squadron_id                     UUID REFERENCES squadron(id),
    desired_mission_job_type_id     UUID REFERENCES job_type(id),
    planned_task_job_type_id        UUID REFERENCES job_type(id),
    mission_lead_id                 UUID REFERENCES mission_lead_type(id),
    comment                         TEXT,
    start_time                      TIMESTAMP WITH TIME ZONE,
    end_time                        TIMESTAMP WITH TIME ZONE
);

-- -------------------------------------------------------
-- Mission Finance Entry
-- -------------------------------------------------------

CREATE TABLE mission_finance_entry (
    id                      UUID PRIMARY KEY,
    version                 BIGINT,
    created_at              TIMESTAMP WITH TIME ZONE,
    updated_at              TIMESTAMP WITH TIME ZONE,
    mission_participant_id  UUID NOT NULL REFERENCES mission_participant(id),
    description             VARCHAR(255),
    type                    VARCHAR(255),
    amount                  NUMERIC
);

-- -------------------------------------------------------
-- Mission Unit (FK -> mission, ship_type, ship)
-- -------------------------------------------------------

CREATE TABLE mission_unit (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    mission_id      UUID NOT NULL REFERENCES mission(id),
    ship_type_id    UUID REFERENCES ship_type(id),
    ship_id         UUID REFERENCES ship(id),
    frequency       DOUBLE PRECISION,
    high_value_unit BOOLEAN NOT NULL DEFAULT FALSE,
    name            VARCHAR(255) NOT NULL
);

-- -------------------------------------------------------
-- Mission Crew (FK -> mission_unit, mission_participant)
-- -------------------------------------------------------

CREATE TABLE mission_crew (
    id                      UUID PRIMARY KEY,
    version                 BIGINT,
    created_at              TIMESTAMP WITH TIME ZONE,
    updated_at              TIMESTAMP WITH TIME ZONE,
    mission_ship_id         UUID NOT NULL REFERENCES mission_unit(id),
    mission_participant_id  UUID NOT NULL REFERENCES mission_participant(id)
);

CREATE TABLE mission_crew_job_types (
    mission_crew_id UUID NOT NULL REFERENCES mission_crew(id),
    job_type_id     UUID NOT NULL REFERENCES job_type(id),
    PRIMARY KEY (mission_crew_id, job_type_id)
);

-- -------------------------------------------------------
-- Refinery Order (FK -> app_user, location, mission, refining_method)
-- -------------------------------------------------------

CREATE TABLE refinery_order (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    owner_id            UUID NOT NULL REFERENCES app_user(id),
    location_id         UUID NOT NULL REFERENCES location(id),
    mission_id          UUID REFERENCES mission(id),
    started_at          TIMESTAMP WITH TIME ZONE,
    duration_minutes    BIGINT,
    refining_method_id  UUID REFERENCES refining_method(id),
    expenses            DOUBLE PRECISION
);

-- -------------------------------------------------------
-- Refinery Good (FK -> material, refinery_order)
-- -------------------------------------------------------

CREATE TABLE refinery_good (
    id                  UUID PRIMARY KEY,
    version             BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    material_id         UUID NOT NULL REFERENCES material(id),
    quantity            INTEGER NOT NULL,
    refinery_order_id   UUID NOT NULL REFERENCES refinery_order(id)
);
