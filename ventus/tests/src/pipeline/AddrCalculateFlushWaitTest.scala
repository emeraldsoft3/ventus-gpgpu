package pipeline

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import top.parameters._

class AddrCalculateFlushWaitTest extends AnyFreeSpec with ChiselScalatestTester {
  "AddrCalculate must enter s_flush_wait and gate new requests until flush_dcache_done" in {
    test(new AddrCalculate(4096.U)).withAnnotations(Seq()) { dut =>
      dut.io.from_fifo.valid.poke(false.B)
      dut.io.csr_pds.poke(0.U)
      dut.io.csr_numw.poke(0.U)
      dut.io.csr_tid.poke(0.U)
      dut.io.idx_entry.poke(0.U)
      dut.io.to_mshr.ready.poke(true.B)
      dut.io.to_shared.ready.poke(true.B)
      dut.io.to_dcache.ready.poke(false.B)
      dut.io.flush_dcache.valid.poke(false.B)
      dut.io.flush_dcache.bits.poke(false.B)
      dut.io.flush_dcache_done.poke(false.B)

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      // In s_idle, ready should be true
      dut.io.from_fifo.ready.expect(true.B)
      dut.io.flush_dcache.ready.expect(true.B)

      // Issue flush
      dut.io.flush_dcache.valid.poke(true.B)
      dut.io.flush_dcache.bits.poke(true.B)
      dut.clock.step(1)
      dut.io.flush_dcache.valid.poke(false.B)

      // Now state is s_dcache, to_dcache.valid should be true for the flush request
      dut.io.to_dcache.valid.expect(true.B)
      dut.io.from_fifo.ready.expect(false.B)
      dut.io.flush_dcache.ready.expect(false.B)

      // Fire to_dcache while flush_dcache_done is false -> must enter s_flush_wait
      dut.io.to_dcache.ready.poke(true.B)
      dut.clock.step(1)
      dut.io.to_dcache.ready.poke(false.B)

      // In s_flush_wait: to_dcache.valid should be false, and new requests must remain gated
      dut.io.to_dcache.valid.expect(false.B)
      dut.io.from_fifo.ready.expect(false.B)
      dut.io.flush_dcache.ready.expect(false.B)

      // Advance several cycles: must stay in s_flush_wait
      dut.clock.step(5)
      dut.io.from_fifo.ready.expect(false.B)
      dut.io.flush_dcache.ready.expect(false.B)

      // DCache signals flush completion -> should transition back to s_idle
      dut.io.flush_dcache_done.poke(true.B)
      dut.clock.step(1)
      dut.io.flush_dcache_done.poke(false.B)

      dut.io.from_fifo.ready.expect(true.B)
      dut.io.flush_dcache.ready.expect(true.B)
    }
  }

  "AddrCalculate directly returns to s_idle if flush_dcache_done is already asserted on fire" in {
    test(new AddrCalculate(4096.U)).withAnnotations(Seq()) { dut =>
      dut.io.from_fifo.valid.poke(false.B)
      dut.io.csr_pds.poke(0.U)
      dut.io.csr_numw.poke(0.U)
      dut.io.csr_tid.poke(0.U)
      dut.io.idx_entry.poke(0.U)
      dut.io.to_mshr.ready.poke(true.B)
      dut.io.to_shared.ready.poke(true.B)
      dut.io.to_dcache.ready.poke(true.B)
      dut.io.flush_dcache.valid.poke(false.B)
      dut.io.flush_dcache.bits.poke(false.B)
      dut.io.flush_dcache_done.poke(false.B)

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      // Issue flush
      dut.io.flush_dcache.valid.poke(true.B)
      dut.io.flush_dcache.bits.poke(true.B)
      dut.clock.step(1)
      dut.io.flush_dcache.valid.poke(false.B)

      // to_dcache fires with flush_dcache_done already true
      dut.io.flush_dcache_done.poke(true.B)
      dut.clock.step(1)
      dut.io.flush_dcache_done.poke(false.B)

      // Should be directly back in s_idle
      dut.io.from_fifo.ready.expect(true.B)
      dut.io.flush_dcache.ready.expect(true.B)
    }
  }
}

