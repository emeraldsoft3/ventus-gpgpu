/*
 * Copyright (c) 2021-2022 International Innovation Center of Tsinghua University, Shanghai
 * Ventus is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details. */
package pipeline

import chisel3._
import chisel3.util._
import top.parameters.{DIFFTEST, SPIKE_OUTPUT, wid_to_check, xLen}
import difftest._
class Branch_back extends Module{
  val io = IO(new Bundle{
    val out=DecoupledIO(new BranchCtrl)
    val in0=Flipped(DecoupledIO(new BranchCtrl))
    val in1=Flipped(DecoupledIO(new BranchCtrl))
  })
  val fifo0=Queue.apply(io.in0,0)
  val fifo1=Queue.apply(io.in1,0)
  val arbiter=Module(new Arbiter(new BranchCtrl(),2))
  arbiter.io.in(0)<>fifo0
  arbiter.io.in(1)<>fifo1
  arbiter.io.out<>io.out
  if (SPIKE_OUTPUT) {
    when(io.out.fire/* && io.out.bits.wid === wid_to_check.U*/) {
      printf(p"sm ${io.out.bits.spike_info.get.sm_id} warp ${io.out.bits.wid} ")
      printf(p"0x${Hexadecimal(io.out.bits.spike_info.get.pc)} 0x${Hexadecimal(io.out.bits.spike_info.get.inst)}")
      printf(p" Jump? ${Decimal(io.out.bits.jump)}  ${Hexadecimal(io.out.bits.new_pc)}\n")
    }
  }
  if (DIFFTEST) {
    val branchCommit = DifftestModule(new DiffInstrCommit, dontCare = true)
    branchCommit.valid := io.out.fire
    branchCommit.coreid := Cat(io.out.bits.spike_info.get.sm_id, io.out.bits.wid)
    branchCommit.index := 0.U
    branchCommit.pc := io.out.bits.spike_info.get.pc
    branchCommit.instr := io.out.bits.spike_info.get.inst
    branchCommit.skip := false.B
    branchCommit.isRVC := false.B
    branchCommit.rfwen := false.B
    branchCommit.fpwen := false.B
    branchCommit.vecwen := false.B
    branchCommit.wdest := 0.U
    branchCommit.wpdest := 0.U
    branchCommit.robIdx := 0.U
    branchCommit.lqIdx := 0.U
    branchCommit.sqIdx := 0.U
    branchCommit.isLoad := false.B
    branchCommit.isStore := false.B
    branchCommit.nFused := 0.U
    branchCommit.special := 0.U
  }
}
class InstWriteBack extends Bundle{
  val sm_id = UInt(8.W)
  val pc=UInt(xLen.W)
  val inst=UInt(32.W)
}
class Writeback(num_x:Int,num_v:Int) extends Module{
  val io = IO(new Bundle{
    val out_v=(DecoupledIO(new WriteVecCtrl))
    val out_x=(DecoupledIO(new WriteScalarCtrl))
    val in_x=Vec(num_x,Flipped(DecoupledIO(new WriteScalarCtrl)))
    val in_v=Vec(num_v,Flipped(DecoupledIO(new WriteVecCtrl)))
  })
  //val fifo=VecInit(Seq.fill(3)(Module(new Queue((new WriteCtrl),2)).io))
  val fifo_x=for(i<-0 until num_x) yield
  { val x=Queue.apply(io.in_x(i),0)
    x
  }
  val fifo_v=for(i<-0 until num_v) yield
  { val x=Queue.apply(io.in_v(i),0)
    x
  }
  val arbiter_x=Module(new Arbiter(new WriteScalarCtrl(),num_x))
  val arbiter_v=Module(new Arbiter(new WriteVecCtrl(),num_v))
  arbiter_x.io.in<>fifo_x
  arbiter_v.io.in<>fifo_v
  arbiter_x.io.out<>io.out_x
  arbiter_v.io.out<>io.out_v
  //send to operand collector
  if(SPIKE_OUTPUT){
    when(io.out_x.fire/*&&io.out_x.bits.warp_id===wid_to_check.U*/){
      printf(p"sm ${io.out_x.bits.spike_info.get.sm_id} warp ${Decimal(io.out_x.bits.warp_id)} ")
      printf(p"0x${Hexadecimal(io.out_x.bits.spike_info.get.pc)} 0x${Hexadecimal(io.out_x.bits.spike_info.get.inst)}")
      printf(p" x${io.out_x.bits.reg_idxw}  ${Hexadecimal(io.out_x.bits.wb_wxd_rd)}\n")
    }
    when(io.out_v.fire/* && io.out_v.bits.warp_id === wid_to_check.U*/) {
      printf(p"sm ${io.out_v.bits.spike_info.get.sm_id} warp ${Decimal(io.out_v.bits.warp_id)} ")
      printf(p"0x${Hexadecimal(io.out_v.bits.spike_info.get.pc)} 0x${Hexadecimal(io.out_v.bits.spike_info.get.inst)}")
      printf(p" v${io.out_v.bits.reg_idxw} ")
      printf(p"${Binary(io.out_v.bits.wvd_mask.asUInt)} ")
      io.out_v.bits.wb_wvd_rd.reverse.foreach(x=>printf(p"${Hexadecimal(x.asUInt)} "))
      printf(p"\n")
    }
  }

  if (DIFFTEST) {
    val instrCommit = DifftestModule(new DiffInstrCommit, dontCare = true)
    val intWb = DifftestModule(new DiffIntWriteback, dontCare = true)

    val commit_x_valid = io.out_x.fire
    instrCommit.valid := commit_x_valid
    instrCommit.coreid := Cat(io.out_x.bits.spike_info.get.sm_id, io.out_x.bits.warp_id)
    instrCommit.index := 0.U
    instrCommit.pc := io.out_x.bits.spike_info.get.pc
    instrCommit.instr := io.out_x.bits.spike_info.get.inst
    instrCommit.skip := false.B
    instrCommit.isRVC := false.B
    instrCommit.rfwen := io.out_x.bits.wxd
    instrCommit.fpwen := false.B
    instrCommit.vecwen := false.B
    instrCommit.wdest := io.out_x.bits.reg_idxw
    instrCommit.wpdest := io.out_x.bits.reg_idxw
    instrCommit.robIdx := 0.U
    instrCommit.lqIdx := 0.U
    instrCommit.sqIdx := 0.U
    instrCommit.isLoad := false.B
    instrCommit.isStore := false.B
    instrCommit.nFused := 0.U
    instrCommit.special := 0.U

    intWb.valid := commit_x_valid && io.out_x.bits.wxd && io.out_x.bits.reg_idxw =/= 0.U
    intWb.coreid := Cat(io.out_x.bits.spike_info.get.sm_id, io.out_x.bits.warp_id)
    intWb.address := io.out_x.bits.reg_idxw
    intWb.data := io.out_x.bits.wb_wxd_rd

    val vecCommit = DifftestModule(new DiffInstrCommit, dontCare = true)
    vecCommit.valid := io.out_v.fire
    vecCommit.coreid := Cat(io.out_v.bits.spike_info.get.sm_id, io.out_v.bits.warp_id)
    vecCommit.index := 0.U
    vecCommit.pc := io.out_v.bits.spike_info.get.pc
    vecCommit.instr := io.out_v.bits.spike_info.get.inst
    vecCommit.skip := false.B
    vecCommit.isRVC := false.B
    vecCommit.rfwen := false.B
    vecCommit.fpwen := false.B
    vecCommit.vecwen := io.out_v.bits.wvd
    vecCommit.wdest := io.out_v.bits.reg_idxw
    vecCommit.wpdest := io.out_v.bits.reg_idxw
    vecCommit.robIdx := 0.U
    vecCommit.lqIdx := 0.U
    vecCommit.sqIdx := 0.U
    vecCommit.isLoad := false.B
    vecCommit.isStore := false.B
    vecCommit.nFused := 0.U
    vecCommit.special := 0.U
  }
}
