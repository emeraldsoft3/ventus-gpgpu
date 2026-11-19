package L2cache

import chisel3._
import chisel3.util._
import chiseltest._
import freechips.rocketchip.tilelink.TLMessages._
import org.scalatest.freespec.AnyFreeSpec

class SourceDWritebackTest extends AnyFreeSpec with ChiselScalatestTester {
  private val testParams = InclusiveCacheParameters_lite(
    cache = CacheParameters(
      level = 2,
      ways = 2,
      sets = 2,
      l2cs = 1,
      blockBytes = 16,
      beatBytes = 16
    ),
    micro = InclusiveCacheMicroParameters(
      writeBytes = 1,
      memCycles = 2,
      portFactor = 2,
      num_warp = 2,
      num_sm = 1,
      num_sm_in_cluster = 1,
      num_cluster = 1,
      NMshrEntry = 2,
      NSets = 2
    ),
    control = false,
    mmu = false
  )

  "SourceD asserts mshr_wait on write miss no allocate (fixing && typo)" in {
    test(new SourceD(testParams)).withAnnotations(Seq()) { dut =>
      // Initialize inputs
      dut.io.req.valid.poke(false.B)
      dut.io.d.ready.poke(false.B)
      dut.io.pb_pop.ready.poke(false.B)
      dut.io.pb_beat.data.poke(0.U)
      dut.io.pb_beat.mask.poke(0.U)
      dut.io.bs_radr.ready.poke(true.B)
      dut.io.bs_rdat.data.poke(0.U)
      dut.io.bs_wadr.ready.poke(true.B)
      dut.io.a.ready.poke(false.B)

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(1)

      assert(!dut.io.mshr_wait.peek().litToBoolean, "mshr_wait must start low at idle")

      // Present a write miss no allocate (PutFullData, hit=false, not dirty)
      dut.io.req.valid.poke(true.B)
      dut.io.req.bits.opcode.poke(PutFullData)
      dut.io.req.bits.hit.poke(false.B)
      dut.io.req.bits.dirty.poke(false.B)
      dut.io.req.bits.from_mem.poke(false.B)
      dut.io.req.bits.mask.poke("h000f".U)
      dut.clock.step(1)
      dut.io.req.valid.poke(false.B)

      // In the next cycle, SourceD processes the write miss and sets mshr_wait_reg
      dut.clock.step(1)
      assert(dut.io.mshr_wait.peek().litToBoolean,
        "mshr_wait must be asserted on write-miss no allocate to serialize DRAM write-through")
      println("[PASS] SourceD mshr_wait asserted on PutFullData write miss")
    }
  }

  "SourceD overrides mask to all-ones for dirty victim writeback on channel A" in {
    test(new SourceD(testParams)).withAnnotations(Seq()) { dut =>
      // Initialize inputs
      dut.io.req.valid.poke(false.B)
      dut.io.d.ready.poke(false.B)
      dut.io.pb_pop.ready.poke(false.B)
      dut.io.pb_beat.data.poke(0.U)
      dut.io.pb_beat.mask.poke(0.U)
      dut.io.bs_radr.ready.poke(true.B)
      dut.io.bs_rdat.data.poke("hdeadbeefdeadbeefdeadbeefdeadbeef".U)
      dut.io.bs_wadr.ready.poke(true.B)
      dut.io.a.ready.poke(false.B)

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(1)

      // Present a read miss with dirty victim (Get, hit=false, dirty=true)
      // Partial dummy mask provided with Get
      dut.io.req.valid.poke(true.B)
      dut.io.req.bits.opcode.poke(Get)
      dut.io.req.bits.hit.poke(false.B)
      dut.io.req.bits.dirty.poke(true.B)
      dut.io.req.bits.from_mem.poke(false.B)
      dut.io.req.bits.mask.poke("h0001".U)
      dut.clock.step(1)
      dut.io.req.valid.poke(false.B)

      // Step to stage_3 where io.a.valid is asserted for victim eviction writeback
      dut.clock.step(1)
      assert(dut.io.a.valid.peek().litToBoolean, "io.a must become valid for dirty victim writeback")
      dut.io.a.bits.opcode.expect(PutFullData)

      // Verify that io.a.bits.mask is expanded to all-ones (0xffff for 16-byte lines)
      val expectedFullMask = (BigInt(1) << testParams.mask_bits) - 1
      assert(dut.io.a.bits.mask.peek().litValue == expectedFullMask,
        s"io.a.bits.mask must be all-1s ($expectedFullMask), got ${dut.io.a.bits.mask.peek().litValue}")

      println("[PASS] SourceD dirty victim writeback mask correctly expanded to full cacheline mask")
    }
  }
}
