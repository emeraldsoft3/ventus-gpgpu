package L2cache

import chisel3._
import chiseltest._
import freechips.rocketchip.tilelink.TLMessages.{Hint, HintAck, PutFullData}
import org.scalatest.freespec.AnyFreeSpec

class L2MaintenanceDrainTest extends AnyFreeSpec with ChiselScalatestTester {
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

  private def initialize(dut: SourceD): Unit = {
    dut.io.req.valid.poke(false.B)
    dut.io.req.bits.opcode.poke(0.U)
    dut.io.req.bits.param.poke(0.U)
    dut.io.req.bits.size.poke(0.U)
    dut.io.req.bits.source.poke(0.U)
    dut.io.req.bits.tag.poke(0.U)
    dut.io.req.bits.set.poke(0.U)
    dut.io.req.bits.offset.poke(0.U)
    dut.io.req.bits.put.poke(0.U)
    dut.io.req.bits.data.poke(0.U)
    dut.io.req.bits.mask.poke(0.U)
    dut.io.req.bits.hit.poke(false.B)
    dut.io.req.bits.dirty.poke(false.B)
    dut.io.req.bits.from_mem.poke(false.B)
    dut.io.req.bits.last_flush.poke(false.B)
    dut.io.req.bits.flush.poke(false.B)
    dut.io.req.bits.l2cidx.poke(0.U)

    dut.io.d.ready.poke(true.B)
    dut.io.pb_pop.ready.poke(true.B)
    dut.io.pb_beat.data.poke(0.U)
    dut.io.pb_beat.mask.poke(0.U)
    dut.io.bs_radr.ready.poke(true.B)
    dut.io.bs_rdat.data.poke(0.U)
    dut.io.bs_wadr.ready.poke(true.B)
    dut.io.a.ready.poke(true.B)
    dut.io.maintenance_writes_drained.poke(true.B)
  }

  "a final maintenance response waits for all generated writes to drain" in {
    test(new SourceD(testParams)) { dut =>
      initialize(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.req.bits.opcode.poke(Hint)
      dut.io.req.bits.source.poke(1.U)
      dut.io.req.bits.hit.poke(true.B)
      dut.io.req.bits.dirty.poke(false.B)
      dut.io.req.bits.last_flush.poke(true.B)
      dut.io.maintenance_writes_drained.poke(false.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      // d.valid and finish_issue must remain deasserted while writes are pending
      dut.io.d.valid.expect(false.B)
      dut.io.finish_issue.expect(false.B)
      dut.clock.step(2)
      dut.io.d.valid.expect(false.B)
      dut.io.finish_issue.expect(false.B)

      // Once writes have drained, release final response
      dut.io.maintenance_writes_drained.poke(true.B)
      dut.io.d.valid.expect(true.B)
      dut.io.d.bits.opcode.expect(HintAck)
      dut.io.finish_issue.expect(true.B)
      dut.clock.step()
      dut.io.d.valid.expect(false.B)
      dut.io.finish_issue.expect(false.B)
    }
  }
}

