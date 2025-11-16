package com.group9.asaa.communication.service.state

import com.group9.asaa.classes.communication.state.CommunicationStateMachine
import com.group9.asaa.communication.model.CommunicationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommunicationStateMachineTest {
    @Test
    fun `full happy path transitions`() {
        val receive = CommunicationStateMachine.onReceive("SubsystemA", "payload", null)
        assertEquals(CommunicationState.RECEIVED, receive.updated.state)
        val connect = CommunicationStateMachine.connect(receive.updated, mapOf("x" to "y"))
        assertEquals(CommunicationState.CONNECTED, connect.updated.state)
        val sending = CommunicationStateMachine.sending(connect.updated)
        assertEquals(CommunicationState.SENDING, sending.updated.state)
        val sent = CommunicationStateMachine.sent(sending.updated)
        assertEquals(CommunicationState.SENT, sent.updated.state)
        val notified = CommunicationStateMachine.notified(sent.updated)
        assertEquals(CommunicationState.NOTIFIED, notified.updated.state)
    }

    @Test
    fun `illegal transition throws`() {
        val receive = CommunicationStateMachine.onReceive("SubsystemA", "payload", null)
        // Attempt to send directly should throw
        try {
            CommunicationStateMachine.sending(receive.updated)
            throw AssertionError("Expected exception not thrown")
        } catch (_: IllegalStateException) { }
    }
}

