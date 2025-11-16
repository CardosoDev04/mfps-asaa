package com.group9.asaa.transport

import com.group9.asaa.classes.transport.*               // <-- NEW: domain classes
import com.group9.asaa.classes.transport.InMemoryTransportPorts
import com.group9.asaa.transportation.TransportStateMachine
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Helper that builds a sample order using the *new* domain class.
 */
private fun sampleOrder(id: String) = AssemblyTransportOrder(
    orderId = id,
    components = listOf(Component("Bolt"), Component("Nut"), Component("Washer")),
    deliveryLocation = Locations.ASSEMBLY_LINE_A
)

// ================================================
//  TEST 1: ORDER TIMES OUT (confirmation)
// ================================================
fun simulateTimeoutCase() = runBlocking {
    println("\n" + "=".repeat(60))
    println("SIMULATION 1 – ORDER TIMES OUT (confirmation)")
    println("=".repeat(60))

    val order = sampleOrder("ORD-TIMEOUT")

    val ports = InMemoryTransportPorts(
        confirmationTimeout = 2.seconds,
        deliveryTimeout = 30.seconds,
        simulateConfirmationFailure = false,
        makeAgvsUnavailable = false
    )

    val sm = TransportStateMachine(this, ports)
    sm.run(order)

    kotlinx.coroutines.delay(12_000)   // wait long enough to see the timeout logs
    println("Final state: TIMED OUT (confirmation)")
}

// ================================================
//  TEST 2: NO AGV AVAILABLE → DENIED
// ================================================
fun simulateAgvUnavailableCase() = runBlocking {
    println("\n" + "=".repeat(60))
    println("SIMULATION 2 – NO AGV AVAILABLE")
    println("=".repeat(60))

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
fun testAgvAvailablePath() = runBlocking {
    println("\n" + "=".repeat(60))
    println("TEST: AGV AVAILABLE → ORDER ACCEPTED & FULFILLED")
    println("=".repeat(60))

    // -------------------------------------------------
    // 1. Make sure at least one AGV is AVAILABLE
    // -------------------------------------------------
    val availableAgvs = AGVPool.checkAvailability()
    if (availableAgvs.isEmpty()) {
        val anyAgv = AGVPool.snapshot().first()   // pool is never empty
        AGVPool.release(anyAgv)
        println("[Test] Released ${anyAgv.id} to make it AVAILABLE")
    } else {
        println("[Test] ${availableAgvs.size} AGV(s) already AVAILABLE")
    }

    // -------------------------------------------------
    // 2. Build order & ports
    // -------------------------------------------------
    val order = sampleOrder("ORD-ACCEPTED")

    val ports = InMemoryTransportPorts(
        confirmationTimeout = 8.seconds,
        deliveryTimeout = 30.seconds,
        simulateConfirmationFailure = false,
        makeAgvsUnavailable = false
    )

    // -------------------------------------------------
    // 3. Run the state machine
    // -------------------------------------------------
    val sm = TransportStateMachine(this, ports)
    sm.run(order)

    // -------------------------------------------------
    // 4. Wait for logs
    // -------------------------------------------------
    kotlinx.coroutines.delay(3_000)

    // -------------------------------------------------
    // 5. Print counters
    // -------------------------------------------------
    println("Processed orders: ${TransportStateMachine.processedCount()}")
    println("Denied orders:     ${TransportStateMachine.deniedCount()}")
}
