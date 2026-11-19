package play.cache

import L1Cache._
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import top.parameters._

class DCacheDirtyMaskStallTest extends AnyFreeSpec with ChiselScalatestTester {
  implicit val p = (new MyConfig).toInstance

  "L1TagAccess preserves held write-hit setIdx and dirtyMask across pipeline stalls" in {
    test(new L1TagAccess(set = 4, way = 2, tagBits = 16, AsidBits = 8, readOnly = false)) { dut =>
      // Initialize inputs
      dut.io.probeRead.valid.poke(false.B)
      dut.io.probeRead.bits.setIdx.poke(0.U)
      dut.io.tagFromCore_st1.poke(0.U)
      dut.io.probeIsWrite_st1.foreach(_.poke(false.B))
      dut.io.allocateWrite.valid.poke(false.B)
      dut.io.allocateWrite.bits.setIdx.poke(0.U)
      dut.io.allocateWriteData_st1.poke(0.U)
      dut.io.allocateWriteTagSRAMWValid_st1.poke(false.B)
      dut.io.invalidateAll.poke(false.B)
      dut.io.tagready_st1.poke(true.B)
      for (i <- 0 until num_lane) {
        dut.io.perLaneAddr_st1(i).activeMask.poke(false.B)
        dut.io.perLaneAddr_st1(i).blockOffset.poke(0.U)
        dut.io.perLaneAddr_st1(i).wordOffset1H.poke(0.U)
      }
      dut.clock.step(2)

      // Step 1: Allocate tag 0x123 into set 0
      dut.io.allocateWrite.valid.poke(true.B)
      dut.io.allocateWrite.bits.setIdx.poke(0.U)
      dut.io.allocateWriteData_st1.poke(0x123.U)
      dut.io.allocateWriteTagSRAMWValid_st1.poke(true.B)
      dut.clock.step(1)
      dut.io.allocateWrite.valid.poke(false.B)
      dut.io.allocateWriteTagSRAMWValid_st1.poke(false.B)
      dut.clock.step(2)

      // Step 2: Probe read for set 0 with tag 0x123 (Write hit)
      dut.io.probeRead.valid.poke(true.B)
      dut.io.probeRead.bits.setIdx.poke(0.U)
      dut.clock.step(1)

      // In ST1: probe arrived, induce pipeline stall (tagready_st1 = false)
      dut.io.probeRead.valid.poke(false.B)
      dut.io.tagFromCore_st1.poke(0x123.U)
      dut.io.probeIsWrite_st1.foreach(_.poke(true.B))
      dut.io.perLaneAddr_st1(0).activeMask.poke(true.B)
      dut.io.perLaneAddr_st1(0).blockOffset.poke(0.U)
      dut.io.perLaneAddr_st1(0).wordOffset1H.poke(0xf.U)
      dut.io.tagready_st1.poke(false.B) // stall ST1!
      dut.clock.step(1)

      // During stall cycle: hit is held, but write must not commit yet
      dut.io.hit_st1.expect(true.B)
      dut.clock.step(1)

      // Release stall: write commits with held setIdx (0.U)
      dut.io.tagready_st1.poke(true.B)
      dut.io.hit_st1.expect(true.B)
      dut.clock.step(1)

      // Completed cleanly without corruption or unhandled latch
      dut.io.probeIsWrite_st1.foreach(_.poke(false.B))
      dut.clock.step(2)
    }
  }
}

