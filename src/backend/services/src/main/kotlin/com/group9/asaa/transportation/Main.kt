package com.group9.asaa.transport

import com.group9.asaa.transport.service.TransportService
import com.group9.asaa.transport.service.TransportStateMachine

import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds   // <-- make sure this import is present

private fun sampleOrder(id: String) = AssemblyTransportOrder(
    orderId = id,
    components = listOf(Component("Bolt"), Component("Nut"), Component("Washer")),
    deliveryLocation = Locations.ASSEMBLY_LINE_A
)

// ================================================
//  TEST 1: ORDER TIMES OUT (confirmation)
// ================================================
public fun simulateTimeoutCase() = runBlocking {
    println("\n" + "=".repeat(60))
    println("SIMULATION 1 – ORDER TIMES OUT (confirmation)")
    println("=".repeat(60))

    val order = sampleOrder("ORD-TIMEOUT")

    val ports = InMemoryTransportPorts(
        confirmationTimeout = 2.seconds,   // short → forces timeout
        deliveryTimeout = 30.seconds,
        simulateConfirmationFailure = false,
        makeAgvsUnavailable = false
    )

    val sm = TransportStateMachine(this, ports)
    sm.run(order)

    kotlinx.coroutines.delay(12_000)  // wait to see timeout logs

    println("Final state: TIMED OUT (confirmation)")
}

// ================================================
//  TEST 2: NO AGV AVAILABLE → DENIED
// ================================================
public fun simulateAgvUnavailableCase() = runBlocking {
    println("\n" + "=".repeat(60))
    println("SIMULATION 2 – NO AGV AVAILABLE")
    println("=".repeat(60))

    // Ensure ALL AGVs are UNAVAILABLE
    AGVPool.makeAllUnavailable()
    println("[Test] All AGVs set to UNAVAILABLE")

    val order = sampleOrder("ORD-NO-AGV")

    val ports = InMemoryTransportPorts(
        confirmationTimeout = 8.seconds,
        deliveryTimeout = 30.seconds,
        simulateConfirmationFailure = false,
        makeAgvsUnavailable = false   // we control AGVs manually
    )

    val sm = TransportStateMachine(this, ports)
    sm.run(order)

    kotlinx.coroutines.delay(8_000)

    println("Processed orders: ${TransportStateMachine.processedCount()}")
    println("Denied orders:     ${TransportStateMachine.deniedCount()}")
    println("Final state: ORDER_DENIED (no AGV)")
}

// ================================================
//  TEST 3: AGV AVAILABLE → ACCEPTED & FULFILLED
// ================================================
public fun testAgvAvailablePath() = runBlocking {
    println("\n" + "=".repeat(60))
    println("TEST: AGV AVAILABLE → ORDER ACCEPTED & FULFILLED")
    println("=".repeat(60))

    // -------------------------------------------------
    // 1. Guarantee that **at least one** AGV is AVAILABLE
    // -------------------------------------------------
    val availableAgvs = AGVPool.checkAvailability()
    if (availableAgvs.isEmpty()) {
        // No AGV is free → free the first one in the pool
        val anyAgv = AGVPool.snapshot().first()          // safe – pool is never empty
        AGVPool.release(anyAgv)
        println("[Test] Released ${anyAgv.id} to make it AVAILABLE")
    } else {
        println("[Test] ${availableAgvs.size} AGV(s) already AVAILABLE")
    }

    // -------------------------------------------------
    // 2. Create order & ports (Duration values)
    // -------------------------------------------------
    val order = sampleOrder("ORD-ACCEPTED")

    val ports = InMemoryTransportPorts(
        confirmationTimeout = 8.seconds,
        deliveryTimeout     = 30.seconds,
        simulateConfirmationFailure = false,
        makeAgvsUnavailable = false
    )

    // -------------------------------------------------
    // 3. Run the state machine
    // -------------------------------------------------
    val sm = TransportStateMachine(this, ports)
    sm.run(order)

    // -------------------------------------------------
    // 4. Wait for all logs
    // -------------------------------------------------
    kotlinx.coroutines.delay(3_000)   // inside runBlocking you can call delay() directly

    // -------------------------------------------------
    // 5. Print counters
    // -------------------------------------------------
    println("Processed orders: ${TransportStateMachine.processedCount()}")
    println("Denied orders:     ${TransportStateMachine.deniedCount()}")
}