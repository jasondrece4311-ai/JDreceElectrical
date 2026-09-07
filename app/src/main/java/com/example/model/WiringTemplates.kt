package com.example.model

object WiringTemplates {

    data class Template(
        val id: String,
        val name: String,
        val description: String,
        val elements: List<CircuitElement>,
        val wires: List<Wire>
    )

    fun getTemplates(): List<Template> = listOf(
        createDolStarterTemplate(),
        createForwardReverseTemplate(),
        createGeneratorStandbyTemplate(),
        createSensorTimerPumpTemplate()
    )

    private fun createDolStarterTemplate(): Template {
        // Direct On Line 3-phase starter with start/stop latching circuit
        val pwr = ComponentFactory.createComponent(ComponentType.POWER_3P, 100f, 60f, "3Φ-GRID")
        val qf = ComponentFactory.createComponent(ComponentType.BREAKER_3P, 100f, 220f, "QF1 (32A)")
        val km = ComponentFactory.createComponent(ComponentType.CONTACTOR_COIL, 360f, 380f, "KM1")
        val fr = ComponentFactory.createComponent(ComponentType.THERMAL_OVERLOAD, 100f, 380f, "FR1")
        val motor = ComponentFactory.createComponent(ComponentType.MOTOR_3PHASE, 100f, 540f, "M1 (5.5kW)")

        // Control Circuit
        val eStop = ComponentFactory.createComponent(ComponentType.ESTOP, 360f, 60f, "SB0 (E-Stop)")
        val stopBtn = ComponentFactory.createComponent(ComponentType.PUSHBUTTON_NC, 360f, 160f, "SB1 (Stop)")
        val startBtn = ComponentFactory.createComponent(ComponentType.PUSHBUTTON_NO, 360f, 260f, "SB2 (Start)")
        val auxLatch = ComponentFactory.createComponent(ComponentType.AUX_CONTACT_NO, 480f, 260f, "KM1-NO").copy(
            linkedCoilId = km.id
        )
        val lamp = ComponentFactory.createComponent(ComponentType.PILOT_LIGHT, 480f, 380f, "HL1 (Run)").copy(
            pilotColor = LightColor.GREEN
        )

        val elements = listOf(pwr, qf, km, fr, motor, eStop, stopBtn, startBtn, auxLatch, lamp)

        val wires = listOf(
            // Power lines L1, L2, L3 from supply to Breaker
            Wire("w1", pwr.id, "L1", qf.id, "L1_IN"),
            Wire("w2", pwr.id, "L2", qf.id, "L2_IN"),
            Wire("w3", pwr.id, "L3", qf.id, "L3_IN"),

            // From Breaker to Thermal Overload
            Wire("w4", qf.id, "L1_OUT", fr.id, "L1_IN"),
            Wire("w5", qf.id, "L2_OUT", fr.id, "L2_IN"),
            Wire("w6", qf.id, "L3_OUT", fr.id, "L3_IN"),

            // From Thermal Overload to Motor
            Wire("w7", fr.id, "L1_OUT", motor.id, "U"),
            Wire("w8", fr.id, "L2_OUT", motor.id, "V"),
            Wire("w9", fr.id, "L3_OUT", motor.id, "W"),

            // Control branch from L1
            Wire("w10", qf.id, "L1_OUT", eStop.id, "T1"),
            Wire("w11", eStop.id, "T2", stopBtn.id, "T1"),
            Wire("w12", stopBtn.id, "T2", startBtn.id, "T1"),
            // Parallel seal-in latch aux contact
            Wire("w13", stopBtn.id, "T2", auxLatch.id, "T1"),
            Wire("w14", startBtn.id, "T2", km.id, "A1"),
            Wire("w15", auxLatch.id, "T2", km.id, "A1"),

            // Contactor coil A2 to control Neutral/L2
            Wire("w16", km.id, "A2", fr.id, "NC_95"),
            Wire("w17", fr.id, "NC_96", qf.id, "L2_OUT"),

            // Parallel pilot run lamp
            Wire("w18", km.id, "A1", lamp.id, "T1"),
            Wire("w19", km.id, "A2", lamp.id, "T2")
        )

        return Template(
            id = "dol_starter",
            name = "DOL Motor Starter",
            description = "Direct-On-Line 3-phase starter with Start/Stop pushbuttons, KM1 seal-in latch, overload protection, and run indicator.",
            elements = elements,
            wires = wires
        )
    }

    private fun createForwardReverseTemplate(): Template {
        val pwr = ComponentFactory.createComponent(ComponentType.POWER_3P, 120f, 60f, "3Φ-MAINS")
        val qf = ComponentFactory.createComponent(ComponentType.BREAKER_3P, 120f, 200f, "QF-MAIN")
        val kmFwd = ComponentFactory.createComponent(ComponentType.CONTACTOR_COIL, 320f, 340f, "KM-FWD")
        val kmRev = ComponentFactory.createComponent(ComponentType.CONTACTOR_COIL, 460f, 340f, "KM-REV")
        val motor = ComponentFactory.createComponent(ComponentType.MOTOR_3PHASE, 120f, 480f, "M1 (Reversible)")
        val fwdBtn = ComponentFactory.createComponent(ComponentType.PUSHBUTTON_NO, 320f, 180f, "SB-FWD")
        val revBtn = ComponentFactory.createComponent(ComponentType.PUSHBUTTON_NO, 460f, 180f, "SB-REV")
        val stopBtn = ComponentFactory.createComponent(ComponentType.PUSHBUTTON_NC, 390f, 80f, "SB-STOP")

        val elements = listOf(pwr, qf, kmFwd, kmRev, motor, fwdBtn, revBtn, stopBtn)
        val wires = listOf(
            Wire("fw1", pwr.id, "L1", qf.id, "L1_IN"),
            Wire("fw2", pwr.id, "L2", qf.id, "L2_IN"),
            Wire("fw3", pwr.id, "L3", qf.id, "L3_IN"),
            Wire("fw4", qf.id, "L1_OUT", motor.id, "U"),
            Wire("fw5", qf.id, "L2_OUT", motor.id, "V"),
            Wire("fw6", qf.id, "L3_OUT", motor.id, "W"),
            Wire("fw7", qf.id, "L1_OUT", stopBtn.id, "T1"),
            Wire("fw8", stopBtn.id, "T2", fwdBtn.id, "T1"),
            Wire("fw9", stopBtn.id, "T2", revBtn.id, "T1"),
            Wire("fw10", fwdBtn.id, "T2", kmFwd.id, "A1"),
            Wire("fw11", revBtn.id, "T2", kmRev.id, "A1"),
            Wire("fw12", kmFwd.id, "A2", qf.id, "L2_OUT"),
            Wire("fw13", kmRev.id, "A2", qf.id, "L2_OUT")
        )

        return Template(
            id = "fwd_rev_starter",
            name = "Forward / Reverse Starter",
            description = "Dual contactor reversing starter with electrical interlock and bi-directional 3-phase induction motor rotation.",
            elements = elements,
            wires = wires
        )
    }

    private fun createGeneratorStandbyTemplate(): Template {
        val gen = ComponentFactory.createComponent(ComponentType.GENERATOR, 100f, 60f, "GEN-SET (25kVA)")
        val qfGen = ComponentFactory.createComponent(ComponentType.BREAKER_3P, 100f, 220f, "QF-GEN (40A)")
        val motor = ComponentFactory.createComponent(ComponentType.MOTOR_3PHASE, 100f, 400f, "PUMP-1 (7.5kW)")
        val ammeter = ComponentFactory.createComponent(ComponentType.METER_AMMETER, 320f, 220f, "PA1")
        val voltmeter = ComponentFactory.createComponent(ComponentType.METER_VOLTMETER, 320f, 100f, "PV1")
        val freq = ComponentFactory.createComponent(ComponentType.METER_FREQUENCY, 320f, 360f, "PF1 (Hz)")

        val elements = listOf(gen, qfGen, motor, ammeter, voltmeter, freq)
        val wires = listOf(
            Wire("gw1", gen.id, "L1", qfGen.id, "L1_IN"),
            Wire("gw2", gen.id, "L2", qfGen.id, "L2_IN"),
            Wire("gw3", gen.id, "L3", qfGen.id, "L3_IN"),
            Wire("gw4", qfGen.id, "L1_OUT", motor.id, "U"),
            Wire("gw5", qfGen.id, "L2_OUT", motor.id, "V"),
            Wire("gw6", qfGen.id, "L3_OUT", motor.id, "W"),
            Wire("gw7", gen.id, "L1", voltmeter.id, "V+"),
            Wire("gw8", gen.id, "N", voltmeter.id, "V-"),
            Wire("gw9", gen.id, "L1", freq.id, "L"),
            Wire("gw10", gen.id, "N", freq.id, "N")
        )

        return Template(
            id = "gen_standby",
            name = "Generator Standby System",
            description = "Diesel/Gas Generator Set powering high-inertia heavy pump motor, featuring load monitoring and automatic stall detection.",
            elements = elements,
            wires = wires
        )
    }

    private fun createSensorTimerPumpTemplate(): Template {
        val dcPwr = ComponentFactory.createComponent(ComponentType.POWER_DC, 80f, 60f, "24V-PSU")
        val floatSw = ComponentFactory.createComponent(ComponentType.SENSOR_FLOAT, 80f, 220f, "SL1 (Float)")
        val timer = ComponentFactory.createComponent(ComponentType.TIMER_ON_DELAY, 260f, 220f, "KT1 (Delay)").copy(
            delaySeconds = 3f
        )
        val solenoid = ComponentFactory.createComponent(ComponentType.SOLENOID, 260f, 380f, "YV1 (Valve)")
        val buzzer = ComponentFactory.createComponent(ComponentType.BUZZER, 420f, 220f, "HA1 (Alarm)")

        val elements = listOf(dcPwr, floatSw, timer, solenoid, buzzer)
        val wires = listOf(
            Wire("sw1", dcPwr.id, "+24V", floatSw.id, "T1"),
            Wire("sw2", floatSw.id, "T2", timer.id, "A1"),
            Wire("sw3", timer.id, "A2", dcPwr.id, "0V"),
            Wire("sw4", timer.id, "A1", solenoid.id, "T1"),
            Wire("sw5", solenoid.id, "T2", dcPwr.id, "0V"),
            Wire("sw6", floatSw.id, "T2", buzzer.id, "T1"),
            Wire("sw7", buzzer.id, "T2", dcPwr.id, "0V")
        )

        return Template(
            id = "sensor_pump",
            name = "Sensor & Timer Automation",
            description = "24V DC automated tank drain system with liquid float switch, on-delay timer relay, solenoid valve, and audible buzzer.",
            elements = elements,
            wires = wires
        )
    }
}
