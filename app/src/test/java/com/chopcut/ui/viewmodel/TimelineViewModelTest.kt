package com.chopcut.ui.viewmodel

import app.cash.turbine.test
import com.chopcut.ui.components.TrimRangeData
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TimelineViewModelTest {

    private lateinit var viewModel: TimelineViewModel

    @Before
    fun setup() {
        viewModel = TimelineViewModel()
    }

    // ============================================================================
    // TESTES: startRange (iniciar definição de range)
    // ============================================================================

    @Test
    fun `startRange should begin defining mode at current position`() = runTest {
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isDefining)
            assertNotNull(state.activeRangeId)
            assertEquals(1, state.ranges.size)

            val range = state.ranges.first()
            assertEquals(5000L, range.startMs)
            assertTrue(range.isDefining)
            assertFalse(range.isConfirmed)
        }
    }

    @Test
    fun `startRange should reject if already defining`() {
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()

        val firstRangeId = viewModel.uiState.value.activeRangeId

        // Tentar iniciar outro range enquanto já está definindo
        viewModel.setPlayheadPosition(8000L)
        viewModel.startRange()

        // Deve manter o mesmo range ativo
        assertEquals(firstRangeId, viewModel.uiState.value.activeRangeId)
        assertEquals(1, viewModel.uiState.value.ranges.size)
    }

    // ============================================================================
    // TESTES: endRange (finalizar definição de range)
    // ============================================================================

    @Test
    fun `endRange should complete range definition`() = runTest {
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()

        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isDefining)
            assertNull(state.activeRangeId)

            val range = state.ranges.first()
            assertEquals(5000L, range.startMs)
            assertEquals(8000L, range.endMs)
            assertFalse(range.isDefining)
            assertTrue(range.isConfirmed)
        }
    }

    @Test
    fun `endRange should reject if not in defining mode`() {
        val initialState = viewModel.uiState.value

        viewModel.endRange()

        // Estado não deve mudar
        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun `endRange should reject if range would overlap`() {
        // Criar primeiro range confirmado
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        // Tentar criar range sobreposto
        viewModel.setPlayheadPosition(7000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(10000L)
        viewModel.endRange()

        // Range não deve ser confirmado (ainda está definindo ou foi rejeitado)
        assertEquals(1, viewModel.uiState.value.ranges.filter { it.isConfirmed }.size)
    }

    @Test
    fun `endRange should allow endMs to be greater than startMs`() = runTest {
        // Testa o caso normal onde usuário arrasta para frente (start < end)
        viewModel.setPlayheadPosition(3000L)
        viewModel.startRange()

        viewModel.setPlayheadPosition(7000L)
        viewModel.endRange()

        viewModel.uiState.test {
            val state = awaitItem()
            val range = state.ranges.first()

            // Verifica que endMs > startMs (direção normal)
            assertTrue(range.endMs > range.startMs)
            assertEquals(3000L, range.startMs)
            assertEquals(7000L, range.endMs)
            assertEquals(4000L, range.durationMs)
            assertTrue(range.isConfirmed)
        }
    }

    @Test
    fun `endRange should handle reverse selection when endMs is less than startMs`() = runTest {
        // Testa o caso onde usuário arrasta para trás (end < start)
        viewModel.setPlayheadPosition(7000L)
        viewModel.startRange()

        viewModel.setPlayheadPosition(3000L)
        viewModel.endRange()

        viewModel.uiState.test {
            val state = awaitItem()
            val range = state.ranges.first()

            // O modelo deve aceitar e manter os valores como definidos
            // (a lógica de sobreposição e duração deve lidar com isso)
            assertEquals(7000L, range.startMs)
            assertEquals(3000L, range.endMs)
            assertTrue(range.isConfirmed)

            // Duração deve ser calculada corretamente (valor absoluto)
            assertEquals(4000L, range.durationMs)
        }
    }

    // ============================================================================
    // TESTES: removeRange (remover range)
    // ============================================================================

    @Test
    fun `removeRange should delete existing range`() = runTest {
        // Adicionar range
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        val rangeId = viewModel.uiState.value.ranges.first().id

        viewModel.removeRange(rangeId)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.ranges.size)
        }
    }

    @Test
    fun `removeRange with invalid id should do nothing`() {
        // Adicionar range
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        // Tentar remover ID inexistente
        viewModel.removeRange("invalid-id")

        assertEquals(1, viewModel.uiState.value.ranges.size)
    }

    // ============================================================================
    // TESTES: validateScrollPosition (validação de scroll)
    // ============================================================================

    @Test
    fun `validateScrollPosition should return true in free zone`() {
        // Adicionar range confirmado
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        // Posição longe do range (mais de 100ms de distância)
        assertTrue(viewModel.validateScrollPosition(1000L)) // 4000ms antes do range
        assertTrue(viewModel.validateScrollPosition(9000L)) // 1000ms depois do range
    }

    @Test
    fun `validateScrollPosition should return false when within 100ms of existing range`() {
        // Adicionar range confirmado (5000-8000)
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()
        viewModel.setPlayheadPosition(8000L)
        viewModel.endRange()

        // Dentro de 100ms do início do range
        assertFalse(viewModel.validateScrollPosition(4900L))
        assertFalse(viewModel.validateScrollPosition(4950L))

        // Dentro de 100ms do fim do range
        assertFalse(viewModel.validateScrollPosition(8050L))
        assertFalse(viewModel.validateScrollPosition(8100L))

        // Exatamente nos limites (deve bloquear)
        assertFalse(viewModel.validateScrollPosition(4900L)) // 100ms antes
        assertFalse(viewModel.validateScrollPosition(8100L)) // 100ms depois
    }

    @Test
    fun `validateScrollPosition should return true when defining and in free zone`() {
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange() // Iniciar definição

        // Durante definição, deve bloquear apenas proximidade do range ativo
        assertTrue(viewModel.validateScrollPosition(2000L)) // Longe
    }

    // ============================================================================
    // TESTES: updateRangeEnd (atualizar fim durante definição)
    // ============================================================================

    @Test
    fun `updateRangeEnd should update end position during defining`() = runTest {
        viewModel.setPlayheadPosition(5000L)
        viewModel.startRange()

        viewModel.updateRangeEnd(7500L)

        viewModel.uiState.test {
            val state = awaitItem()
            val range = state.ranges.first()
            assertEquals(5000L, range.startMs)
            assertEquals(7500L, range.endMs)
            assertTrue(range.isDefining)
        }
    }
}
