# Backend Documentation

## Overview

The backend is a modular, event-driven system built with **Kotlin** and **Spring Boot**. It leverages **Coroutines** for asynchronous processing and **Kafka** for inter-service communication. The system is designed to handle assembly transport orders, manage communication between subsystems, and simulate transport logistics.

## Architecture

The backend is structured into several Gradle modules:

-   **`http`**: Contains the REST API controllers and SSE endpoints.
-   **`services`**: Contains the core business logic, state machines, and service integrations.
-   **`domain`**: Defines the data models, entities, and value objects.
-   **`repositories`**: Handles data persistence and database access.

### Key Components

1.  **Assembly Service**: Manages the lifecycle of assembly orders. It handles order creation, queueing, state transitions, and coordination with the transport subsystem.
2.  **Communication Service**: Facilitates reliable messaging between subsystems using the **Outbox Pattern**. It processes messages through stages: Receive, Connect, Send, and Notify.
3.  **Transport Service**: Simulates the transport subsystem. It listens for transport orders and executes them using a state machine.

## Data Flow

### 1. Order Creation Flow

1.  **API Request**: A client sends a `POST /assembly/transport-order` request.
2.  **AssemblyService**:
    *   The service picks a delivery location (load balancing based on pending orders).
    *   The order is placed in an internal `Channel` (queue).
    *   The API responds immediately with the created order details.
3.  **Processing (Background Coroutine)**:
    *   Orders are dequeued and processed one by one.
    *   An `AssemblyStateMachine` is initialized for the order.
    *   A `TRANSPORT_ORDER` message is sent to the Transport subsystem via the Communication Service.
4.  **State Transitions**: The order goes through various states (e.g., `IDLE`, `ASSEMBLING`) based on events (confirmation, arrival, validation).

### 2. Communication Flow

The communication system ensures reliable message delivery:

1.  **Receive Stage**: Accepts a message, persists it, and publishes to the `inbound` Kafka topic.
2.  **Connect Stage**: Consumes from `inbound`, enriches the message, and publishes to `connected`.
3.  **Send Stage**:
    *   Consumes from `connected`.
    *   Saves the message to an **Outbox** table (database).
    *   Publishes to the `outbound` Kafka topic.
4.  **Delivery**: Messages are consumed from `outbound` by the target subsystem (e.g., Transport Service).

### 3. Transport Flow

1.  **Listener**: The `TransportService` listens to the `outbound` Kafka topic.
2.  **Filter**: It filters for `TRANSPORT_ORDER` messages destined for the `transport` subsystem.
3.  **Execution**:
    *   A `TransportStateMachine` is launched for the order.
    *   It simulates the transport process (moving, arrived, etc.).
    *   Events are emitted back to the system via the Event Bus.

## API Reference

### Assembly Endpoints (`/assembly`)

-   `POST /transport-order`: Create a single transport order.
    -   Params: `demo` (boolean).
-   `POST /transport-order/bulk`: Create multiple orders.
    -   Params: `n` (count), `demo` (boolean), `testRunId`.
-   `GET /queue-size`: Get the current number of enqueued orders.
-   `GET /system-state`: Get the overall system state (e.g., `IDLE`, `ASSEMBLING`).
-   `PUT /confirm-order`: Manually confirm an order.
-   `PUT /signal-transport-arrived`: Manually signal transport arrival.
-   `PUT /validate-assembly`: Manually validate an assembly.
-   `GET /events`: **SSE** stream of assembly events (status, logs, state changes).

### Communication Endpoints (`/communication`)

-   `POST /messages`: Send a generic message between subsystems.
-   `GET /events`: **SSE** stream of communication events.

### Transport Endpoints (`/transport`)

-   `GET /events`: **SSE** stream of transport events.

## Key Features

-   **Asynchronous Processing**: Extensive use of Kotlin Coroutines for non-blocking I/O and concurrent task execution.
-   **Event-Driven**: Decoupled components communicate via Kafka topics (`inbound`, `connected`, `outbound`, `notifications`).
-   **Reliability**: Implements the **Outbox Pattern** to ensure data consistency between the database and Kafka.
-   **Real-Time Updates**: Server-Sent Events (SSE) provide real-time feedback to clients (e.g., dashboards).
-   **Observability**: Comprehensive logging and metrics recording for every stage of the order lifecycle.
-   **Simulation Mode**: "Demo" mode allows for autopilot execution of orders for testing and demonstration purposes.
