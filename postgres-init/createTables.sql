-- Sequence and defined type
CREATE SEQUENCE IF NOT EXISTS agv_usage_id_seq;

-- Table Definition
CREATE TABLE "public"."agv_usage" (
    "id" int8 NOT NULL DEFAULT nextval('agv_usage_id_seq'::regclass),
    "agv_id" text NOT NULL,
    "order_id" text NOT NULL,
    "acquired_at" timestamptz NOT NULL,
    "released_at" timestamptz,
    "busy_ms" int8,
    PRIMARY KEY ("id")
);

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
    "is_demo" bool,
    "chosen_at" timestamptz,
    "initial_queue_size" int4,
    "pending_on_line_at_choice" int4,
    "transport_fulfilled_at" timestamptz,
    "transport_latency_ms" int8,
    "completed_at" timestamptz,
    "total_lead_time_ms" int8,
    "final_system_state" text,
    "final_order_state" text,
    "assembly_duration_ms" int8,
    PRIMARY KEY ("order_id")
);

-- Sequence and defined type
CREATE SEQUENCE IF NOT EXISTS queue_events_id_seq;

-- Table Definition
CREATE TABLE "public"."queue_events" (
    "id" int8 NOT NULL DEFAULT nextval('queue_events_id_seq'::regclass),
    "order_id" text NOT NULL,
    "line" text NOT NULL,
    "event_type" text NOT NULL,
    "queue_size" int4 NOT NULL,
    "pending_on_line" int4 NOT NULL,
    "ts" timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY ("id")
);

-- Sequence and defined type
CREATE SEQUENCE IF NOT EXISTS state_transitions_id_seq;

-- Table Definition
CREATE TABLE "public"."state_transitions" (
    "id" int8 NOT NULL DEFAULT nextval('state_transitions_id_seq'::regclass),
    "order_id" text NOT NULL,
    "subsystem" text NOT NULL,
    "from_state" text,
    "to_state" text NOT NULL,
    "ts" timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY ("id")
);