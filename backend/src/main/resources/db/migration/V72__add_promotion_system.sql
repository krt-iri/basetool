-- V72: Promotion System
-- Tables: promotion_topic, promotion_category, promotion_level_content, rank_requirement, member_evaluation

CREATE TABLE promotion_topic (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    sort_order  INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_promotion_topic_sort ON promotion_topic (sort_order);

CREATE TABLE promotion_category (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    topic_id    UUID        NOT NULL REFERENCES promotion_topic(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    sort_order  INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_promotion_category_topic ON promotion_category (topic_id);
CREATE INDEX idx_promotion_category_sort  ON promotion_category (topic_id, sort_order);

CREATE TABLE promotion_level_content (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    category_id UUID        NOT NULL REFERENCES promotion_category(id) ON DELETE CASCADE,
    level       VARCHAR(10) NOT NULL CHECK (level IN ('LEVEL_A','LEVEL_B','LEVEL_C')),
    description VARCHAR(4000) NOT NULL,
    UNIQUE (category_id, level)
);

CREATE INDEX idx_promotion_level_content_category ON promotion_level_content (category_id);

CREATE TABLE rank_requirement (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    from_rank     INT         NOT NULL,
    to_rank       INT         NOT NULL,
    topic_id      UUID        REFERENCES promotion_topic(id) ON DELETE SET NULL,
    category_id   UUID        REFERENCES promotion_category(id) ON DELETE SET NULL,
    minimum_level VARCHAR(10) NOT NULL CHECK (minimum_level IN ('LEVEL_A','LEVEL_B','LEVEL_C')),
    required_count INT        NOT NULL DEFAULT 1,
    description   VARCHAR(2000)
);

CREATE INDEX idx_rank_requirement_ranks ON rank_requirement (from_rank, to_rank);

CREATE TABLE member_evaluation (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id        VARCHAR(64) NOT NULL,
    category_id    UUID        NOT NULL REFERENCES promotion_category(id) ON DELETE CASCADE,
    assigned_level VARCHAR(10) CHECK (assigned_level IN ('LEVEL_A','LEVEL_B','LEVEL_C')),
    UNIQUE (user_id, category_id)
);

CREATE INDEX idx_member_evaluation_user     ON member_evaluation (user_id);
CREATE INDEX idx_member_evaluation_category ON member_evaluation (category_id);
