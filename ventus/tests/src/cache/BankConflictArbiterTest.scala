package cache

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import config.config.Config
import L1Cache.MyConfig
import L1Cache.ShareMem.BankConflictArbiter
import top.parameters._

class BankConflictArbiterTest extends AnyFreeSpec with ChiselScalatestTester {

  "BankConflictArbiter non-conflict parallel access" in {
    implicit val p: config.config.Parameters = (new MyConfig).toInstance

    test(new BankConflictArbiter).withAnnotations(Seq()) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.grantReady.poke(true.B)
      dut.io.coreReqArb.enable.poke(true.B)
      dut.io.coreReqArb.isWrite.poke(false.B)

      // Lane i accesses bank i (no conflict)
      val nLanes = p(L1Cache.RVGParamsKey).NLanes
      for (i <- 0 until nLanes) {
        dut.io.coreReqArb.perLaneAddr(i).activeMask.poke(true.B)
        dut.io.coreReqArb.perLaneAddr(i).blockOffset.poke(i.U)
        dut.io.coreReqArb.perLaneAddr(i).wordOffset1H.poke(1.U)
      }

      dut.clock.step(1)

      assert(!dut.io.bankConflict.peek().litToBoolean, "There should be NO bank conflict when each lane accesses a distinct bank")
      assert(!dut.io.busy.peek().litToBoolean, "Arbiter should NOT be busy on non-conflicting access")

      for (i <- 0 until nLanes) {
        assert(dut.io.activeLane(i).peek().litToBoolean, s"Lane $i must be active")
      }
      println("[PASS] BankConflictArbiter non-conflict parallel access verified")
    }
  }

  "BankConflictArbiter two-lane conflict serialization" in {
    implicit val p: config.config.Parameters = (new MyConfig).toInstance

    test(new BankConflictArbiter).withAnnotations(Seq()) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.grantReady.poke(true.B)
      dut.io.coreReqArb.enable.poke(true.B)
      dut.io.coreReqArb.isWrite.poke(false.B)

      val nLanes = p(L1Cache.RVGParamsKey).NLanes

      // Lane 0 and Lane 1 both access Bank 2 (bank conflict)
      dut.io.coreReqArb.perLaneAddr(0).activeMask.poke(true.B)
      dut.io.coreReqArb.perLaneAddr(0).blockOffset.poke(2.U)
      dut.io.coreReqArb.perLaneAddr(0).wordOffset1H.poke(1.U)

      dut.io.coreReqArb.perLaneAddr(1).activeMask.poke(true.B)
      dut.io.coreReqArb.perLaneAddr(1).blockOffset.poke(2.U)
      dut.io.coreReqArb.perLaneAddr(1).wordOffset1H.poke(1.U)

      for (i <- 2 until nLanes) {
        dut.io.coreReqArb.perLaneAddr(i).activeMask.poke(false.B)
        dut.io.coreReqArb.perLaneAddr(i).blockOffset.poke(0.U)
        dut.io.coreReqArb.perLaneAddr(i).wordOffset1H.poke(0.U)
      }

      // In first cycle, conflict detected
      assert(dut.io.bankConflict.peek().litToBoolean, "Bank conflict must be detected when two lanes target Bank 2")
      assert(dut.io.activeLane(0).peek().litToBoolean, "PriorityEncoder must grant Lane 0 first")
      assert(!dut.io.activeLane(1).peek().litToBoolean, "Lane 1 must be deferred")

      dut.clock.step(1)
      dut.io.coreReqArb.enable.poke(false.B)

      // In second cycle, Lane 1 is serviced from pending register
      assert(dut.io.busy.peek().litToBoolean, "Arbiter must remain busy servicing the conflict request")
      assert(!dut.io.activeLane(0).peek().litToBoolean, "Lane 0 already completed")
      assert(dut.io.activeLane(1).peek().litToBoolean, "Lane 1 must now be serviced")

      dut.clock.step(1)
      assert(!dut.io.busy.peek().litToBoolean, "Arbiter must return to idle once all conflicts are resolved")
      println("[PASS] BankConflictArbiter two-lane conflict serialized cleanly")
    }
  }
}
