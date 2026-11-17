package pipeline

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import top.parameters._

class WarpSchedulerFlushMultiWgHarness extends Module {
  val io = IO(new Bundle {
    val warpReqValid = Input(Bool())
    val warpReqWid = Input(UInt(depth_warp.W))
    val warpReqWgId = Input(UInt(log2Ceil(CTA_SCHE_CONFIG.WG.NUM_WG_DIM_MAX + 1).W))

    val warpCtrlValid = Input(Bool())
    val warpCtrlWid = Input(UInt(depth_warp.W))
    val warpCtrlEndprg = Input(Bool())
    val warpCtrlWgId = Input(UInt(TAG_WIDTH.W))

    val flushReady = Input(Bool())
    val flushValid = Output(Bool())
  })

  val scheduler = Module(new warp_scheduler)
  scheduler.io.pc_reset := false.B

  scheduler.io.warpReq.valid := io.warpReqValid
  scheduler.io.warpReq.bits := 0.U.asTypeOf(scheduler.io.warpReq.bits)
  scheduler.io.warpReq.bits.wid := io.warpReqWid
  val WF_ID_WIDTH = log2Ceil(num_warp_in_a_block)
  scheduler.io.warpReq.bits.CTAdata.dispatch2cu_wf_tag_dispatch := (io.warpReqWgId << WF_ID_WIDTH).asUInt
  scheduler.io.warpReq.bits.CTAdata.dispatch2cu_wg_wf_count := 1.U

  scheduler.io.warp_control.valid := io.warpCtrlValid
  scheduler.io.warp_control.bits := 0.U.asTypeOf(scheduler.io.warp_control.bits)
  scheduler.io.warp_control.bits.ctrl.simt_stack_op := io.warpCtrlEndprg
  scheduler.io.warp_control.bits.ctrl.wid := io.warpCtrlWid
  scheduler.io.wg_id_tag := (io.warpCtrlWgId << WF_ID_WIDTH).asUInt

  scheduler.io.warpRsp.ready := true.B
  scheduler.io.pc_req.ready := true.B
  scheduler.io.pc_rsp.valid := false.B
  scheduler.io.pc_rsp.bits := 0.U.asTypeOf(scheduler.io.pc_rsp.bits)
  scheduler.io.branch.valid := false.B
  scheduler.io.branch.bits := 0.U.asTypeOf(scheduler.io.branch.bits)
  scheduler.io.issued_warp.valid := false.B
  scheduler.io.issued_warp.bits := 0.U
  scheduler.io.scoreboard_busy := 0.U
  scheduler.io.exe_busy := 0.U
  scheduler.io.pc_ibuffer_ready.foreach(_ := 0.U)
  scheduler.io.flushDCache.ready := io.flushReady

  io.flushValid := scheduler.io.flushDCache.valid
}

class WarpSchedulerFlushMultiWgTest extends AnyFreeSpec with ChiselScalatestTester {

  "warp_scheduler retires multiple concurrently finishing workgroups without deadlock" in {
    test(new WarpSchedulerFlushMultiWgHarness) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.warpReqValid.poke(false.B)
      dut.io.warpCtrlValid.poke(false.B)
      dut.io.warpCtrlEndprg.poke(false.B)
      dut.io.flushReady.poke(false.B)
      dut.clock.step(1)

      // Step 1: Dispatch Warp 1 to WG 1
      dut.io.warpReqValid.poke(true.B)
      dut.io.warpReqWid.poke(1.U)
      dut.io.warpReqWgId.poke(1.U)
      dut.clock.step(1)

      // Step 2: Dispatch Warp 2 to WG 2
      dut.io.warpReqValid.poke(true.B)
      dut.io.warpReqWid.poke(2.U)
      dut.io.warpReqWgId.poke(2.U)
      dut.clock.step(1)
      dut.io.warpReqValid.poke(false.B)

      // Step 3: Warp 1 finishes (WG 1) while flushReady is LOW (flush delayed)
      dut.io.warpCtrlValid.poke(true.B)
      dut.io.warpCtrlEndprg.poke(true.B)
      dut.io.warpCtrlWid.poke(1.U)
      dut.io.warpCtrlWgId.poke(1.U)
      dut.clock.step(1)

      // Step 4: Warp 2 finishes (WG 2) while flushReady is still LOW
      // Now both WG 1 and WG 2 have completed (warp_endprg_mask_0 has bits 1 and 2 set)
      dut.io.warpCtrlValid.poke(true.B)
      dut.io.warpCtrlEndprg.poke(true.B)
      dut.io.warpCtrlWid.poke(2.U)
      dut.io.warpCtrlWgId.poke(2.U)
      dut.clock.step(1)
      dut.io.warpCtrlValid.poke(false.B)

      // Both WG 1 and WG 2 need flush
      assert(dut.io.flushValid.peek().litToBoolean, "flushValid must be true when completed workgroups await retirement")

      // Step 5: Now grant flush DCache ready
      dut.io.flushReady.poke(true.B)
      dut.clock.step(1) // PriorityEncoder clears the first WG (WG 1)

      // Second WG (WG 2) should still be pending retirement
      assert(dut.io.flushValid.peek().litToBoolean, "flushValid must remain true for the second pending workgroup")

      dut.clock.step(1) // PriorityEncoder clears the second WG (WG 2)

      // Now all workgroups have been retired cleanly
      assert(!dut.io.flushValid.peek().litToBoolean, "All workgroups retired cleanly: flushValid must return to false")

      println("[PASS] warp_scheduler PriorityEncoder multi-WG flush retirement verified cleanly")
    }
  }
}

