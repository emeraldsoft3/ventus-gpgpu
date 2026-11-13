package pipeline

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import pipeline.ALUOps._

class MultiplierMulhsuTest extends AnyFreeSpec with ChiselScalatestTester {

  "Multiplier MULHSU, MULH, MULHU, and MUL verification" - {
    "should return high 32 bits for MULHSU (Issue #101)" in {
      test(new ArrayMultiplier(4, 32)) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.a.poke("habababab".U) // signed
        dut.io.in.bits.b.poke("ha36566c8".U) // unsigned
        dut.io.in.bits.c.poke(0.U)
        dut.io.in.bits.ctrl.alu_fn.poke(FN_MULHSU)
        dut.io.out.ready.poke(true.B)

        dut.clock.step(1)
        dut.io.in.valid.poke(false.B)

        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        val res = dut.io.out.bits.result.peek().litValue
        // Expected high 32 bits: 0xca2ce72d (Spike verified)
        assert(res == BigInt("ca2ce72d", 16), s"Expected 0xca2ce72d but got 0x${res.toString(16)}")
        println("[PASS] MULHSU correctly selected upper 32 bits: 0xca2ce72d")
      }
    }

    "should return high 32 bits for MULH and MULHU, and low 32 bits for MUL" in {
      test(new ArrayMultiplier(4, 32)) { dut =>
        dut.io.out.ready.poke(true.B)

        // Test MULHU
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.a.poke("habababab".U)
        dut.io.in.bits.b.poke("ha36566c8".U)
        dut.io.in.bits.c.poke(0.U)
        dut.io.in.bits.ctrl.alu_fn.poke(FN_MULHU)
        dut.clock.step(1)
        dut.io.in.valid.poke(false.B)

        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        val resMulhu = dut.io.out.bits.result.peek().litValue
        assert(resMulhu == BigInt("6d924df5", 16), s"MULHU expected 0x6d924df5 but got 0x${resMulhu.toString(16)}")
        println("[PASS] MULHU correctly returned 0x6d924df5")

        // Test MULH
        dut.clock.step(1)
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.a.poke("habababab".U)
        dut.io.in.bits.b.poke("ha36566c8".U)
        dut.io.in.bits.c.poke(0.U)
        dut.io.in.bits.ctrl.alu_fn.poke(FN_MULH)
        dut.clock.step(1)
        dut.io.in.valid.poke(false.B)

        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        val resMulh = dut.io.out.bits.result.peek().litValue
        assert(resMulh == BigInt("1e813b82", 16), s"MULH expected 0x1e813b82 but got 0x${resMulh.toString(16)}")
        println("[PASS] MULH correctly returned 0x1e813b82")

        // Test MUL
        dut.clock.step(1)
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.a.poke("habababab".U)
        dut.io.in.bits.b.poke("ha36566c8".U)
        dut.io.in.bits.c.poke(0.U)
        dut.io.in.bits.ctrl.alu_fn.poke(FN_MUL)
        dut.clock.step(1)
        dut.io.in.valid.poke(false.B)

        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        val resMul = dut.io.out.bits.result.peek().litValue
        assert(resMul == BigInt("1ffb3f98", 16), s"MUL expected 0x1ffb3f98 but got 0x${resMul.toString(16)}")
        println("[PASS] MUL correctly returned 0x1ffb3f98")
      }
    }
  }
}

