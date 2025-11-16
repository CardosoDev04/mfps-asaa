-- Table Definition
CREATE TABLE "public"."orders" (
    "order_id" text NOT NULL,
    "delivery_location" text NOT NULL,
    "state" text NOT NULL,
    "system_state" text,
    "created_at" timestamptz NOT NULL DEFAULT now(),
    "updated_at" timestamptz NOT NULL DEFAULT now(),
    "sent_at" timestamptz,
    "confirmation_at" timestamptz,
    "confirmation_latency_ms" int8,
    "accepted_at" timestamptz,
    "assembling_started_at" timestamptz,
    "accepted_to_assembling_ms" int8,
    "test_run_id" text,
    PRIMARY KEY ("order_id")
);