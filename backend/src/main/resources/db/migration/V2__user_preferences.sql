ALTER TABLE app_user
    ADD COLUMN default_domain VARCHAR(60) NOT NULL DEFAULT 'ideas',
    ADD CONSTRAINT ck_user_default_domain CHECK (
        default_domain IN ('engineering', 'ai', 'business', 'economics', 'ideas', 'culture', 'world', 'health', 'communication')
    );
